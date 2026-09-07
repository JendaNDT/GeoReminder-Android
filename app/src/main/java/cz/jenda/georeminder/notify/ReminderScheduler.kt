package cz.jenda.georeminder.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import java.util.Calendar

/**
 * Plánování systémových spouštěčů připomínek.
 *
 * - LOCATION → GeofencingClient
 * - TIME → AlarmManager
 * - technický stav (fired/snooze/requestCode) → SchedulerStateStore
 *
 * Scheduler je navržený tak, aby opakovaný resync byl bezpečný a aby stejné
 * reminder ID vždy používalo stejné PendingIntent requestCode i po restartu.
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val geofencing = LocationServices.getGeofencingClient(appContext)
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val stateStore = SchedulerStateStore(appContext)

    private val resyncLock = Any()
    private var resyncGeneration = 0L

    companion object {
        const val ACTION_ALARM_FIRE = "cz.jenda.georeminder.ALARM_FIRE"
        const val ACTION_SNOOZE_FIRE = "cz.jenda.georeminder.SNOOZE_FIRE"
        const val ACTION_NAG_FIRE = "cz.jenda.georeminder.NAG_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val NAG_INTERVAL_MINUTES = 5
        const val SNOOZE_MINUTES = 60

        @Volatile
        private var instance: ReminderScheduler? = null

        fun get(context: Context): ReminderScheduler =
            instance ?: synchronized(this) {
                instance ?: ReminderScheduler(context.applicationContext).also { instance = it }
            }

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
            while ((isoWeekday(next.timeInMillis) !in targetDays || next.timeInMillis <= now) && safety < 15) {
                next.add(Calendar.DAY_OF_YEAR, 1)
                safety++
            }
            if (next.timeInMillis <= now) next.add(Calendar.DAY_OF_YEAR, 7)
            return next.timeInMillis
        }
    }

    // MARK: - Veřejné API

    fun schedule(reminder: Reminder) {
        if (reminder.isDone) return

        // Po upgradu z původní verze mohou v AlarmManageru ještě žít PendingIntenty
        // založené na String.hashCode(). Před prvním novým plánováním je zrušíme.
        cancelLegacyPendingIntents(reminder.id)

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
        cancelLegacyPendingIntents(reminderId)
        NotificationHelper.cancel(appContext, reminderId)
        stateStore.clearFired(reminderId)
        stateStore.clearSnooze(reminderId)
    }

    // MARK: - Dožadování

    fun scheduleNag(reminder: Reminder) {
        setExact(
            System.currentTimeMillis() + NAG_INTERVAL_MINUTES * 60_000L,
            nagPendingIntent(reminder.id),
        )
    }

    fun cancelNag(reminderId: String) {
        alarms.cancel(nagPendingIntent(reminderId))
    }

    private fun nagPendingIntent(reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            stateStore.requestCode(reminderId, SchedulerStateStore.OFFSET_NAG),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_NAG_FIRE)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    // MARK: - Jednorázové „už vystřeleno"

    fun markGeofenceFired(reminderId: String) {
        stateStore.markFired(reminderId)
    }

    fun isAlarmFired(reminderId: String): Boolean = stateStore.isFired(reminderId)

    fun markAlarmFired(reminderId: String) {
        stateStore.markFired(reminderId)
    }

    // MARK: - Snooze

    fun clearSnooze(reminderId: String) {
        stateStore.clearSnooze(reminderId)
    }

    fun snooze(reminder: Reminder, minutes: Int) {
        snoozeAt(reminder, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        cancelNag(reminder.id)
        setExact(atMillis, alarmPendingIntent(reminder.id, snooze = true))
        stateStore.setSnooze(reminder.id, atMillis)
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
     * Znovu nastaví celý systémový stav podle seznamu reminderů.
     *
     * AlarmManager je idempotentní díky stabilním PendingIntentům. U geofence
     * nejdřív odstraníme všechny geofence patřící našemu sdílenému PendingIntentu
     * a až po dokončení odstranění přidáme aktuální snapshot. Generační číslo
     * zabrání tomu, aby starší paralelní resync přepsal novější stav.
     */
    fun resync(all: List<Reminder>) {
        all.forEach {
            cancelNag(it.id)
            cancelLegacyPendingIntents(it.id)
        }

        LocationHolder.geofenceFailed.value = false
        val active = all.filter { !it.isDone }

        // Časové remindery lze bezpečně přeplánovat hned: stejný PendingIntent
        // nahradí předchozí alarm místo vytvoření duplicity.
        active.filter { it.kind == ReminderKind.TIME }.forEach { scheduleAlarm(it) }
        restoreSnoozes(active)

        val locationSnapshot = active.filter { it.kind == ReminderKind.LOCATION }
        val generation = synchronized(resyncLock) {
            resyncGeneration += 1
            resyncGeneration
        }

        try {
            geofencing.removeGeofences(geofencePendingIntent())
                .addOnCompleteListener {
                    val stillLatest = synchronized(resyncLock) { generation == resyncGeneration }
                    if (!stillLatest) return@addOnCompleteListener
                    locationSnapshot.forEach { addGeofence(it) }
                }
        } catch (e: Exception) {
            Log.w("ReminderScheduler", "Hromadný reset geofence selhal", e)
            val stillLatest = synchronized(resyncLock) { generation == resyncGeneration }
            if (stillLatest) {
                locationSnapshot.forEach { addGeofence(it) }
            }
        }
    }

    /** Obnoví snooze po restartu telefonu. */
    private fun restoreSnoozes(active: List<Reminder>) {
        val byId = active.associateBy { it.id }
        val now = System.currentTimeMillis()

        for ((id, at) in stateStore.allSnoozes()) {
            val reminder = byId[id]
            if (reminder == null) {
                stateStore.clearSnooze(id)
                continue
            }
            if (at > now) {
                setExact(at, alarmPendingIntent(id, snooze = true))
            } else {
                NotificationHelper.show(appContext, reminder)
                stateStore.clearSnooze(id)
            }
        }
    }

    // MARK: - Geofence

    @SuppressLint("MissingPermission")
    private fun addGeofence(reminder: Reminder) {
        if (!LocationHolder.hasFineLocation(appContext)) return
        if (!reminder.repeats && stateStore.isFired(reminder.id)) return

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
                reminder.radius.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        try {
            geofencing.addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener { LocationHolder.geofenceFailed.value = false }
                .addOnFailureListener { e ->
                    Log.w("ReminderScheduler", "Registrace geofence selhala", e)
                    LocationHolder.geofenceFailed.value = true
                }
        } catch (_: SecurityException) {
            LocationHolder.geofenceFailed.value = true
        }
    }

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
            flags,
        )
    }

    // MARK: - AlarmManager

    private fun scheduleAlarm(reminder: Reminder) {
        val due = reminder.dueDate ?: return
        val now = System.currentTimeMillis()
        val triggerAt = when (reminder.timeRepeat) {
            TimeRepeat.NEVER -> {
                if (due <= now) {
                    if (!stateStore.isFired(reminder.id)) {
                        NotificationHelper.show(appContext, reminder)
                        stateStore.markFired(reminder.id)
                    }
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
        val offset = if (snooze) SchedulerStateStore.OFFSET_SNOOZE else SchedulerStateStore.OFFSET_ALARM
        return PendingIntent.getBroadcast(
            appContext,
            stateStore.requestCode(reminderId, offset),
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // MARK: - Migrace PendingIntentů z v2.7 a starších

    private fun cancelLegacyPendingIntents(reminderId: String) {
        alarms.cancel(legacyAlarmPendingIntent(reminderId, snooze = false))
        alarms.cancel(legacyAlarmPendingIntent(reminderId, snooze = true))
        alarms.cancel(legacyNagPendingIntent(reminderId))
    }

    private fun legacyAlarmPendingIntent(reminderId: String, snooze: Boolean): PendingIntent {
        val action = if (snooze) ACTION_SNOOZE_FIRE else ACTION_ALARM_FIRE
        val requestCode = reminderId.hashCode() xor (if (snooze) 0x5A5A5A else 0)
        return PendingIntent.getBroadcast(
            appContext,
            requestCode,
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun legacyNagPendingIntent(reminderId: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode() xor 0x0F0F0F,
            Intent(appContext, AlarmReceiver::class.java)
                .setAction(ACTION_NAG_FIRE)
                .putExtra(EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
