package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.widget.WidgetRefresher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val scheduler = ReminderScheduler.get(appContext)

    // Zápisy na disk jdou na jedno IO vlákno (serializovaně), aby neblokovaly UI
    // a zároveň se nepřekrývaly.
    private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val ioScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    // true = poslední čtení dat selhalo → dočasně nepovolit zápis (ochrana proti
    // přepsání platného souboru prázdným seznamem).
    @Volatile
    private var loadFailed = false

    @Volatile
    private var lastPersistJob: Job? = null

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    init {
        reload()
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
     * Znovu načte data z disku bez blokování volajícího. Vhodné pro UI, které
     * jen potřebuje aktualizovat StateFlow.
     *
     * Receiver nesmí po tomto volání hned číst [reminders]: načtení ještě
     * nemusí být hotové. Pro doručování používej [reloadAndGet].
     */
    fun reload() {
        ioScope.launch {
            loadFromDisk()
        }
    }

    /**
     * Načte data a vrátí je až po dokončení IO. Toto je kritická varianta pro
     * BroadcastReceivery: po studeném startu procesu by obyčejné [reload]
     * vrátilo řízení s prázdným seznamem a událost by se nenávratně zahodila.
     */
    suspend fun reloadAndGet(): List<Reminder> = withContext(ioDispatcher) {
        loadFromDisk()
    }

    /** Načte aktuální data a teprve potom obnoví všechny systémové spouštěče. */
    suspend fun reloadAndResync() {
        scheduler.resync(reloadAndGet())
    }

    @Synchronized
    private fun loadFromDisk(): List<Reminder> {
        when (val res = SharedStorage.read(appContext, FILE)) {
            is SharedStorage.ReadResult.Ok -> {
                val loaded = SharedStorage.decodeReminders(res.text)
                _reminders.value = loaded
                loadFailed = false
                AttachmentHelper.cleanupOrphanedAttachments(appContext, loaded)
            }
            SharedStorage.ReadResult.Empty -> {
                // Soubor ještě neexistuje = legitimní prázdno (první spuštění).
                _reminders.value = emptyList()
                loadFailed = false
            }
            SharedStorage.ReadResult.Error -> {
                // Čtení selhalo – NEPŘEPISOVAT paměť a zablokovat zápis, aby se
                // platný soubor nepřepsal prázdným seznamem.
                loadFailed = true
                Log.w("ReminderStore", "Čtení dat selhalo – uložení dočasně zablokováno")
            }
        }
        return _reminders.value
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
        if (!oldReminder.isDone && reminder.isDone) {
            ReliabilityHistory.record(
                appContext,
                ReliabilityEventType.COMPLETED,
                reminder.id,
                reminder.title,
            )
        } else if (oldReminder.isDone && !reminder.isDone) {
            ReliabilityHistory.record(
                appContext,
                ReliabilityEventType.REACTIVATED,
                reminder.id,
                reminder.title,
            )
        }
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

    /**
     * Varianta pro BroadcastReceiver: stav se musí dostat na disk ještě před
     * pending.finish(), jinak smí Android čerstvě probuzený proces ukončit.
     */
    suspend fun markDoneAndWait(reminder: Reminder) {
        markDone(reminder)
        lastPersistJob?.join()
    }

    @Synchronized
    fun delete(reminder: Reminder) {
        _reminders.value = _reminders.value.filterNot { it.id == reminder.id }
        if (reminder.attachmentPath != null) {
            AttachmentHelper.deleteAttachment(appContext, reminder.attachmentPath)
        }
        scheduler.cancel(reminder.id)
        ReliabilityHistory.record(
            appContext,
            ReliabilityEventType.DELETED,
            reminder.id,
            reminder.title,
        )
        persist()
    }

    /** Odloží připomínku – nová jednorázová notifikace za daný počet minut. */
    fun snooze(reminder: Reminder, minutes: Int) {
        scheduler.snooze(reminder, minutes)
        ReliabilityHistory.record(
            appContext,
            ReliabilityEventType.SNOOZED,
            reminder.id,
            reminder.title,
            (System.currentTimeMillis() + minutes * 60_000L).toString(),
        )
    }

    /** Odloží připomínku na konkrétní čas (např. zítra ráno). */
    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        scheduler.snoozeAt(reminder, atMillis)
        ReliabilityHistory.record(
            appContext,
            ReliabilityEventType.SNOOZED,
            reminder.id,
            reminder.title,
            atMillis.toString(),
        )
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
        lastPersistJob = ioScope.launch {
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
