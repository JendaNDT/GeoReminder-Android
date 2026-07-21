package cz.jenda.georeminder.data

import android.content.Context
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.notify.NotificationHelper
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.widget.WidgetRefresher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.ListSerializer

/**
 * Úložiště připomínek: drží seznam v paměti (StateFlow pro UI), ukládá ho
 * jako JSON na disk (čte ho i widget) a synchronizuje geofence + budíky.
 * Zrcadlí ReminderStore z iOS verze.
 */
class ReminderStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val scheduler = ReminderScheduler(appContext)

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
        val text = SharedStorage.readText(appContext, FILE) ?: return
        val decoded = try {
            SharedStorage.json.decodeFromString(
                ListSerializer(Reminder.serializer()), text
            )
        } catch (_: Exception) {
            return
        }
        _reminders.value = decoded
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
        val text = SharedStorage.json.encodeToString(
            ListSerializer(Reminder.serializer()), _reminders.value
        )
        SharedStorage.writeText(appContext, FILE, text)
        WidgetRefresher.refresh(appContext)
    }
}
