package cz.jenda.georeminder.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.Calendar

/**
 * Plánování „spouštěčů" připomínek:
 *  - na místě → geofence (GeofencingClient, hlídá systém i při zavřené appce)
 *  - na čas → přesný budík (AlarmManager), opakování se přeplánovává po každém spuštění
 * Ekvivalent UNLocationNotificationTrigger + UNCalendarNotificationTrigger z iOS.
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val geofencing = LocationServices.getGeofencingClient(appContext)
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val prefs = appContext.getSharedPreferences(SharedStorage.PREFS, Context.MODE_PRIVATE)

    companion object {
        const val ACTION_ALARM_FIRE = "cz.jenda.georeminder.ALARM_FIRE"
        const val ACTION_SNOOZE_FIRE = "cz.jenda.georeminder.SNOOZE_FIRE"
        const val ACTION_NAG_FIRE = "cz.jenda.georeminder.NAG_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val NAG_INTERVAL_MINUTES = 5
        const val SNOOZE_MINUTES = 60
        private const val KEY_FIRED_GEOFENCES = "firedGeofenceIds"
        private const val KEY_FIRED_ALARMS = "firedAlarmIds"
        private const val KEY_SNOOZE_PREFIX = "snooze_"

        // Serializuje read-modify-write nad SharedPreferences (souběh událostí).
        private val prefsLock = Any()

        /** Nejbližší budoucí výskyt stejné hodiny a minuty (denní opakování). */
        fun nextDaily(dueMillis: Long, now: Long = System.currentTimeMillis()): Long {
            val due = Calendar.getInstance().apply { timeInMillis = dueMillis }
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, due.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, due.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (next.timeInMillis <= now) next.add(Calendar.DAY_OF_YEAR, 1)
            return next.timeInMillis
        }

        /** Den v týdnu v ISO formátu: 1 = pondělí … 7 = neděle. */
        fun isoWeekday(millis: Long): Int {
            val dow = Calendar.getInstance().apply { timeInMillis = millis }
                .get(Calendar.DAY_OF_WEEK)
            return ((dow + 5) % 7) + 1
        }

        /**
         * Nejbližší budoucí výskyt v některém z vybraných dnů týdne (hodina a
         * minuta podle dueMillis). Bez vybraných dnů se použije den z dueMillis.
         */
        fun nextWeekly(
            dueMillis: Long,
            weekdays: List<Int>?,
            now: Long = System.currentTimeMillis(),
        ): Long {
            val targetDays = weekdays?.takeIf { it.isNotEmpty() }
                ?: listOf(isoWeekday(dueMillis))
            val due = Calendar.getInstance().apply { timeInMillis = dueMillis }
            val next = Calendar.getInstance().apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, due.get(Calendar.HOUR_OF_DAY))
                set(Calendar.MINUTE, due.get(Calendar.MINUTE))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            var safety = 0
            while ((isoWeekday(next.timeInMillis) !in targetDays
                        || next.timeInMillis <= now) && safety < 15
            ) {
                next.add(Calendar.DAY_OF_YEAR, 1)
                safety++
            }
            // Pojistka pro poškozená data (žádný platný den): vrať aspoň
            // nejbližší budoucí výskyt, ne termín v minulosti.
            if (next.timeInMillis <= now) next.add(Calendar.DAY_OF_YEAR, 7)
            return next.timeInMillis
        }
    }

    // MARK: - Veřejné API

    fun schedule(reminder: Reminder) {
        if (reminder.isDone) return
        when (reminder.kind) {
            ReminderKind.LOCATION -> addGeofence(reminder)
            ReminderKind.TIME -> scheduleAlarm(reminder)
        }
    }

    fun cancel(reminderId: String) {
        geofencing.removeGeofences(listOf(reminderId))
        alarms.cancel(alarmPendingIntent(reminderId, snooze = false))
        alarms.cancel(alarmPendingIntent(reminderId, snooze = true))
        cancelNag(reminderId)
        NotificationHelper.cancel(appContext, reminderId)
        clearGeofenceFired(reminderId)
        clearAlarmFired(reminderId)
        clearSnooze(reminderId)
    }

    // MARK: - Dožadování (opakované připomenutí do potvrzení)

    /** Za 5 minut připomenout znovu (volá se po každém zobrazení notifikace). */
    fun scheduleNag(reminder: Reminder) {
        setExact(
            System.currentTimeMillis() + NAG_INTERVAL_MINUTES * 60_000L,
            nagPendingIntent(reminder.id)
        )
    }

    /** Zastaví dožadování (Hotovo, odložení, úprava, otevření appky). */
    fun cancelNag(reminderId: String) {
        alarms.cancel(nagPendingIntent(reminderId))
    }

    private fun nagPendingIntent(reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode() xor 0x0F0F0F,
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_NAG_FIRE)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // MARK: - Značka „už vystřeleno" pro jednorázové geofence
    // Jednorázová geo-připomínka po spuštění zůstává v seznamu aktivní (jako na
    // iOS), ale nesmí se při dalším startu appky znovu zaregistrovat a střílet
    // opakovaně. Úprava/Vrátit/smazání jde přes cancel(), který značku smaže.

    private fun firedGeofenceIds(): Set<String> =
        prefs.getStringSet(KEY_FIRED_GEOFENCES, emptySet()) ?: emptySet()

    fun markGeofenceFired(reminderId: String) = synchronized(prefsLock) {
        prefs.edit()
            .putStringSet(KEY_FIRED_GEOFENCES, HashSet(firedGeofenceIds()).apply { add(reminderId) })
            .apply()
    }

    private fun clearGeofenceFired(reminderId: String) = synchronized(prefsLock) {
        val current = firedGeofenceIds()
        if (reminderId in current) {
            prefs.edit()
                .putStringSet(KEY_FIRED_GEOFENCES, HashSet(current).apply { remove(reminderId) })
                .apply()
        }
    }

    // Značka „už odpáleno" pro jednorázové časové budíky – aby je catch-up po
    // restartu telefonu neposlal znovu, když se normálně doručily před restartem.
    private fun firedAlarmIds(): Set<String> =
        prefs.getStringSet(KEY_FIRED_ALARMS, emptySet()) ?: emptySet()

    /** true = jednorázový budík už byl doručen (catch-up nebo AlarmReceiver). */
    fun isAlarmFired(reminderId: String): Boolean = reminderId in firedAlarmIds()

    fun markAlarmFired(reminderId: String) = synchronized(prefsLock) {
        prefs.edit()
            .putStringSet(KEY_FIRED_ALARMS, HashSet(firedAlarmIds()).apply { add(reminderId) })
            .apply()
    }

    private fun clearAlarmFired(reminderId: String) = synchronized(prefsLock) {
        val current = firedAlarmIds()
        if (reminderId in current) {
            prefs.edit()
                .putStringSet(KEY_FIRED_ALARMS, HashSet(current).apply { remove(reminderId) })
                .apply()
        }
    }

    private fun rememberSnooze(reminderId: String, atMillis: Long) = synchronized(prefsLock) {
        prefs.edit().putLong(KEY_SNOOZE_PREFIX + reminderId, atMillis).apply()
    }

    fun clearSnooze(reminderId: String) = synchronized(prefsLock) {
        prefs.edit().remove(KEY_SNOOZE_PREFIX + reminderId).apply()
    }

    /** Odložení: jednorázový budík za daný počet minut (i pro geo-připomínky). */
    fun snooze(reminder: Reminder, minutes: Int) {
        snoozeAt(reminder, System.currentTimeMillis() + minutes * 60_000L)
    }

    /** Odložení na konkrétní čas (např. zítra ráno). Pozastaví i dožadování. */
    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        cancelNag(reminder.id)
        setExact(atMillis, alarmPendingIntent(reminder.id, snooze = true))
        // Zapamatovat odložení, ať ho jde obnovit po restartu telefonu.
        rememberSnooze(reminder.id, atMillis)
    }

    /** Po spuštění opakovaného budíku naplánuje další výskyt. */
    fun scheduleNextOccurrence(reminder: Reminder) {
        val due = reminder.dueDate ?: return
        val next = when (reminder.timeRepeat) {
            TimeRepeat.DAILY -> nextDaily(due)
            TimeRepeat.WEEKLY -> nextWeekly(due, reminder.weekdays)
            TimeRepeat.NEVER -> return
        }
        setExact(next, alarmPendingIntent(reminder.id, snooze = false))
    }

    /**
     * Znovu zaregistruje všechno aktivní (start appky, restart telefonu).
     * Otevření appky zároveň zastaví běžící dožadování – uživatel appku vidí.
     */
    fun resync(all: List<Reminder>) {
        all.forEach { cancelNag(it.id) }
        // Optimisticky vyčistit chybový příznak geofence; když registrace zase
        // selže, failure listener ho nastaví zpět na true (jinak by banner
        // „zamrzl" i po smazání problémové připomínky).
        LocationHolder.geofenceFailed.value = false
        val active = all.filter { !it.isDone }
        active.forEach { schedule(it) }
        restoreSnoozes(active)
    }

    /**
     * Obnoví odložené (snooze) budíky po restartu telefonu. Pokud odložený čas
     * mezitím uplynul (telefon byl vypnutý), připomínku doručí hned.
     */
    private fun restoreSnoozes(active: List<Reminder>) {
        val byId = active.associateBy { it.id }
        val now = System.currentTimeMillis()
        val snoozeKeys = prefs.all.keys.filter { it.startsWith(KEY_SNOOZE_PREFIX) }
        for (key in snoozeKeys) {
            val id = key.removePrefix(KEY_SNOOZE_PREFIX)
            val at = prefs.getLong(key, 0L)
            val reminder = byId[id]
            if (reminder == null || at == 0L) {
                clearSnooze(id)
                continue
            }
            if (at > now) {
                setExact(at, alarmPendingIntent(id, snooze = true))
            } else {
                NotificationHelper.show(appContext, reminder)
                clearSnooze(id)
            }
        }
    }

    // MARK: - Geofence

    @SuppressLint("MissingPermission")
    private fun addGeofence(reminder: Reminder) {
        if (!LocationHolder.hasFineLocation(appContext)) return
        // Jednorázová připomínka, která už vystřelila, se znovu neregistruje
        if (!reminder.repeats && reminder.id in firedGeofenceIds()) return

        val transition = if (reminder.trigger == TriggerType.ARRIVE) {
            Geofence.GEOFENCE_TRANSITION_ENTER
        } else {
            Geofence.GEOFENCE_TRANSITION_EXIT
        }

        val geofence = Geofence.Builder()
            .setRequestId(reminder.id)
            .setCircularRegion(
                reminder.latitude,
                reminder.longitude,
                reminder.radius.toFloat()
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()

        // initialTrigger = 0: notifikace jen při skutečném překročení hranice,
        // ne hned při vytvoření připomínky na místě, kde zrovna stojíš.
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        try {
            geofencing.addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener { LocationHolder.geofenceFailed.value = false }
                .addOnFailureListener { e ->
                    // Např. systémový limit 100 geofence nebo vypnuté služby polohy –
                    // dřív to selhalo úplně potichu, teď to appka ukáže bannerem.
                    Log.w("ReminderScheduler", "Registrace geofence selhala", e)
                    LocationHolder.geofenceFailed.value = true
                }
        } catch (_: SecurityException) {
            // Bez oprávnění „Povolit vždy" – appka to ukazuje bannerem;
            // po udělení oprávnění se geofence znovu zaregistrují (resync).
        }
    }

    /** Jeden sdílený PendingIntent pro všechny geofence (systém do něj vkládá data). */
    private fun geofencePendingIntent(): PendingIntent {
        val flags = if (Build.VERSION.SDK_INT >= 31) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(
            appContext,
            1000,
            Intent(appContext, GeofenceReceiver::class.java),
            flags
        )
    }

    // MARK: - Budíky

    private fun scheduleAlarm(reminder: Reminder) {
        val due = reminder.dueDate ?: return
        val now = System.currentTimeMillis()
        val triggerAt = when (reminder.timeRepeat) {
            TimeRepeat.NEVER -> {
                if (due <= now) {
                    // Termín už uplynul – typicky zmeškaný, když byl telefon
                    // vypnutý. Doručit jednou, pokud se budík ještě neodpálil.
                    if (reminder.id !in firedAlarmIds()) {
                        NotificationHelper.show(appContext, reminder)
                        markAlarmFired(reminder.id)
                    }
                    // Zrušit případný dosud čekající (nepřesný/Doze) budík, ať
                    // tutéž připomínku nedoručí podruhé.
                    alarms.cancel(alarmPendingIntent(reminder.id, snooze = false))
                    return
                }
                due
            }
            TimeRepeat.DAILY -> nextDaily(due, now)
            TimeRepeat.WEEKLY -> nextWeekly(due, reminder.weekdays, now)
        }
        setExact(triggerAt, alarmPendingIntent(reminder.id, snooze = false))
    }

    private fun setExact(triggerAtMillis: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= 31 && !alarms.canScheduleExactAlarms()) {
                // Bez povolení přesných budíků: nepřesný budík (může přijít o pár minut později)
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun alarmPendingIntent(reminderId: String, snooze: Boolean): PendingIntent {
        val action = if (snooze) ACTION_SNOOZE_FIRE else ACTION_ALARM_FIRE
        val requestCode = reminderId.hashCode() xor (if (snooze) 0x5A5A5A else 0)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
