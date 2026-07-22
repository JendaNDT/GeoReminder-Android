package cz.jenda.georeminder.data

import android.content.Context
import android.os.Looper
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
import kotlinx.serialization.builtins.ListSerializer

/**
 * Úložiště připomínek: drží seznam v paměti (StateFlow pro UI), ukládá ho
 * jako JSON na disk (čte ho i widget) a synchronizuje geofence + budíky.
 * Zrcadlí ReminderStore z iOS verze.
 */
class ReminderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler(appContext)

    // Zápisy na disk jdou na jedno IO vlákno (serializovaně), aby neblokovaly UI
    // a zároveň se nepřekrývaly.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // true = poslední čtení dat selhalo → dočasně nepovolit zápis (ochrana proti
    // přepsání platného souboru prázdným seznamem).
    @Volatile
    private var loadFailed = false

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

    /** Znovu načte data z disku (po akci na notifikaci, návratu do popředí…). */
    @Synchronized
    fun reload() {
        when (val res = SharedStorage.read(appContext, FILE)) {
            is SharedStorage.ReadResult.Ok -> {
                _reminders.value = SharedStorage.decodeReminders(res.text)
                loadFailed = false
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
