package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer

/**
 * Úložiště připomínek: drží seznam v paměti (StateFlow pro UI), ukládá ho
 * jako JSON na disk (čte ho i widget) a synchronizuje geofence + budíky.
 * Zrcadlí ReminderStore z iOS verze.
 */
class ReminderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler(appContext)

    // Čtení i zápisy používají stejnou sériovou IO frontu. Tím se zabrání tomu,
    // aby receiver četl napůl probíhající změnu nebo resync předběhl načtení.
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // true = poslední čtení dat selhalo → dočasně nepovolit zápis (ochrana proti
    // přepsání platného souboru prázdným seznamem).
    @Volatile
    private var loadFailed = false

    // Rozlišuje cold start bez jediného úspěšného načtení od přechodné chyby
    // během běžícího procesu. V druhém případě lze bezpečně použít poslední
    // známý stav v paměti pro doručení alarmu/geofence.
    @Volatile
    private var hasLoadedSuccessfully = false

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    init {
        reload()
    }

    enum class ReloadResult {
        LOADED,
        EMPTY,
        CACHED,
        ERROR,
    }

    companion object {
        private const val FILE = "reminders.json"

        @Volatile
        private var instance: ReminderStore? = null

        fun get(context: Context): ReminderStore =
            instance ?: synchronized(this) {
                instance ?: ReminderStore(context).also { instance = it }
            }
    }

    /**
     * Pohodlná asynchronní obnova pro UI. Pokud volající potřebuje s čerstvými
     * daty hned pokračovat (receiver, boot, resync), musí použít reloadAndWait().
     */
    fun reload() {
        ioScope.launch {
            reloadAndWait()
        }
    }

    /**
     * Znovu načte data z disku a vrátí se až po dokončení čtení a aktualizaci
     * StateFlow. Kritické systémové cesty tak nikdy nepokračují nad starým nebo
     * prázdným seznamem pouze proto, že asynchronní IO ještě nedoběhlo.
     *
     * Při přechodné chybě po předchozím úspěšném načtení vrací CACHED a ponechá
     * poslední známá data v paměti. ERROR znamená cold start bez bezpečných dat.
     */
    suspend fun reloadAndWait(): ReloadResult = withContext(ioDispatcher) {
        synchronized(this@ReminderStore) {
            when (val res = SharedStorage.read(appContext, FILE)) {
                is SharedStorage.ReadResult.Ok -> {
                    val loaded = SharedStorage.decodeReminders(res.text)
                    _reminders.value = loaded
                    loadFailed = false
                    hasLoadedSuccessfully = true
                    AttachmentHelper.cleanupOrphanedAttachments(appContext, loaded)
                    ReloadResult.LOADED
                }

                SharedStorage.ReadResult.Empty -> {
                    // Soubor ještě neexistuje = legitimní prázdno (první spuštění).
                    _reminders.value = emptyList()
                    loadFailed = false
                    hasLoadedSuccessfully = true
                    ReloadResult.EMPTY
                }

                SharedStorage.ReadResult.Error -> {
                    // Čtení selhalo – NEPŘEPISOVAT paměť a zablokovat zápis, aby se
                    // platný soubor nepřepsal prázdným seznamem. Pokud už jsme v tomto
                    // procesu dříve úspěšně načetli data, receivery mohou použít cache.
                    loadFailed = true
                    Log.w("ReminderStore", "Čtení dat selhalo – uložení dočasně zablokováno")
                    if (hasLoadedSuccessfully) ReloadResult.CACHED else ReloadResult.ERROR
                }
            }
        }
    }

    @Synchronized
    fun add(reminder: Reminder) {
        _reminders.value = _reminders.value + reminder
        persist()
        scheduler.schedule(reminder)
    }

    @Synchronized
    fun update(reminder: Reminder) {
        val list = _reminders.value.toMutableList()
        val index = list.indexOfFirst { it.id == reminder.id }
        if (index < 0) return
        val oldReminder = list[index]
        if (oldReminder.attachmentPath != null && oldReminder.attachmentPath != reminder.attachmentPath) {
            AttachmentHelper.deleteAttachment(appContext, oldReminder.attachmentPath)
        }
        list[index] = reminder
        _reminders.value = list
        persist()
        scheduler.cancel(reminder.id)
        if (!reminder.isDone) {
            scheduler.schedule(reminder)
        }
    }

    fun toggleDone(reminder: Reminder) {
        update(reminder.copy(isDone = !reminder.isDone))
    }

    fun markDone(reminder: Reminder) {
        if (!reminder.isDone) toggleDone(reminder)
    }

    @Synchronized
    fun delete(reminder: Reminder) {
        _reminders.value = _reminders.value.filterNot { it.id == reminder.id }
        if (reminder.attachmentPath != null) {
            AttachmentHelper.deleteAttachment(appContext, reminder.attachmentPath)
        }
        scheduler.cancel(reminder.id)
        persist()
    }

    /** Odloží připomínku – nová jednorázová notifikace za daný počet minut. */
    fun snooze(reminder: Reminder, minutes: Int) {
        scheduler.snooze(reminder, minutes)
    }

    /** Odloží připomínku na konkrétní čas (např. zítra ráno). */
    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        scheduler.snoozeAt(reminder, atMillis)
    }

    /** Znovu zaregistruje geofence a budíky (start appky, po restartu telefonu). */
    fun resyncAll() {
        scheduler.resync(_reminders.value)
    }

    private fun persist() {
        if (loadFailed) {
            Log.w("ReminderStore", "Uložení přeskočeno – poslední čtení dat selhalo")
            return
        }
        val snapshot = _reminders.value
        ioScope.launch {
            try {
                val text = SharedStorage.json.encodeToString(
                    ListSerializer(Reminder.serializer()), snapshot
                )
                SharedStorage.writeText(appContext, FILE, text)
                WidgetRefresher.refresh(appContext)
            } catch (e: Exception) {
                Log.w("ReminderStore", "Chyba při sériovém zápisu na disk", e)
            }
        }
    }
}
