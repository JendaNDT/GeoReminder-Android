package cz.jenda.georeminder.notify

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.SystemAccess
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId
import kotlinx.coroutines.flow.StateFlow

/**
 * Plánování systémových spouštěčů připomínek.
 *
 * - LOCATION → GeofencingClient
 * - TIME → AlarmManager
 * - technický stav (fired/snooze/requestCode/geofence status) → SchedulerStateStore
 *
 * Scheduler je navržený tak, aby opakovaný resync byl bezpečný a aby stejné
 * reminder ID vždy používalo stejné PendingIntent requestCode i po restartu.
 */
class ReminderScheduler(context: Context) {
    private val appContext = context.applicationContext
    private val geofencing = LocationServices.getGeofencingClient(appContext)
    private val alarms = appContext.getSystemService(AlarmManager::class.java)
    private val stateStore = SchedulerStateStore(appContext)

    val geofenceStates: StateFlow<Map<String, GeofenceRegistrationState>> =
        stateStore.geofenceStates

    private val geofenceResyncLock = Any()
    private var geofenceResyncRunning = false
    private var pendingGeofenceSnapshot: List<Reminder>? = null

    companion object {
        const val ACTION_ALARM_FIRE = "cz.jenda.georeminder.ALARM_FIRE"
        const val ACTION_SNOOZE_FIRE = "cz.jenda.georeminder.SNOOZE_FIRE"
        const val ACTION_NAG_FIRE = "cz.jenda.georeminder.NAG_FIRE"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val NAG_INTERVAL_MINUTES = 5
        const val SNOOZE_MINUTES = 60

        private const val TAG = "ReminderScheduler"

        @Volatile
        private var instance: ReminderScheduler? = null

        fun get(context: Context): ReminderScheduler =
            instance ?: synchronized(this) {
                instance ?: ReminderScheduler(context.applicationContext).also { instance = it }
            }

        fun nextDaily(dueMillis: Long, now: Long = System.currentTimeMillis()): Long {
            val zone = ZoneId.systemDefault()
            val dueTime = Instant.ofEpochMilli(dueMillis)
                .atZone(zone)
                .toLocalTime()
                .withSecond(0)
                .withNano(0)
            val nowInstant = Instant.ofEpochMilli(now)
            val nowLocal = nowInstant.atZone(zone)
            var candidate = ZonedDateTime.of(nowLocal.toLocalDate(), dueTime, zone)
            if (!candidate.toInstant().isAfter(nowInstant)) {
                candidate = ZonedDateTime.of(nowLocal.toLocalDate().plusDays(1), dueTime, zone)
            }
            return candidate.toInstant().toEpochMilli()
        }

        fun isoWeekday(millis: Long): Int =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).dayOfWeek.value

        fun nextWeekly(
            dueMillis: Long,
            weekdays: List<Int>?,
            now: Long = System.currentTimeMillis(),
        ): Long {
            val zone = ZoneId.systemDefault()
            val dueZoned = Instant.ofEpochMilli(dueMillis).atZone(zone)
            val dueTime = dueZoned.toLocalTime().withSecond(0).withNano(0)
            val targetDays = weekdays?.filter { it in 1..7 }?.takeIf { it.isNotEmpty() }?.toSet()
                ?: setOf(dueZoned.dayOfWeek.value)
            val nowInstant = Instant.ofEpochMilli(now)
            val startDate = nowInstant.atZone(zone).toLocalDate()

            for (offset in 0..14) {
                val date = startDate.plusDays(offset.toLong())
                if (date.dayOfWeek.value !in targetDays) continue
                val candidate = ZonedDateTime.of(date, dueTime, zone)
                if (candidate.toInstant().isAfter(nowInstant)) {
                    return candidate.toInstant().toEpochMilli()
                }
            }

            val fallbackDate = startDate.plusWeeks(1)
            return ZonedDateTime.of(fallbackDate, dueTime, zone).toInstant().toEpochMilli()
        }
    }

    fun schedule(reminder: Reminder) {
        if (reminder.isDone) return
        cancelLegacyPendingIntents(reminder.id)

        val snoozeUntil = stateStore.snoozeUntil(reminder.id)
        if (snoozeUntil != null && snoozeUntil > System.currentTimeMillis()) {
            if (reminder.kind == ReminderKind.LOCATION) {
                setGeofenceState(reminder.id, GeofenceRegistrationStatus.SNOOZED)
            }
            cancelOriginalTrigger(reminder)
            setExact(snoozeUntil, alarmPendingIntent(reminder.id, snooze = true))
            return
        }

        when (reminder.kind) {
            ReminderKind.LOCATION -> addGeofence(reminder)
            ReminderKind.TIME -> scheduleAlarm(reminder)
        }
    }

    fun cancel(reminderId: String) {
        geofencing.removeGeofences(listOf(reminderId))
        cancelPendingIntent(alarmPendingIntent(reminderId, snooze = false))
        cancelPendingIntent(alarmPendingIntent(reminderId, snooze = true))
        cancelNag(reminderId)
        cancelLegacyPendingIntents(reminderId)
        NotificationHelper.cancel(appContext, reminderId)
        stateStore.clearFired(reminderId)
        stateStore.clearSnooze(reminderId)
        clearGeofenceState(reminderId)
    }

    fun scheduleNag(reminder: Reminder) {
        setExact(
            System.currentTimeMillis() + NAG_INTERVAL_MINUTES * 60_000L,
            nagPendingIntent(reminder.id),
        )
    }

    fun cancelNag(reminderId: String) {
        cancelPendingIntent(nagPendingIntent(reminderId))
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

    fun markGeofenceFired(reminderId: String) {
        stateStore.markFired(reminderId)
        setGeofenceState(reminderId, GeofenceRegistrationStatus.FIRED)
    }

    fun isAlarmFired(reminderId: String): Boolean = stateStore.isFired(reminderId)

    fun markAlarmFired(reminderId: String) {
        stateStore.markFired(reminderId)
    }

    fun clearSnooze(reminderId: String) {
        stateStore.clearSnooze(reminderId)
    }

    fun snooze(reminder: Reminder, minutes: Int) {
        snoozeAt(reminder, System.currentTimeMillis() + minutes * 60_000L)
    }

    fun snoozeAt(reminder: Reminder, atMillis: Long) {
        val target = atMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        cancelNag(reminder.id)
        cancelPendingIntent(alarmPendingIntent(reminder.id, snooze = true))
        stateStore.setSnooze(reminder.id, target)
        if (reminder.kind == ReminderKind.LOCATION) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.SNOOZED)
        }
        cancelOriginalTrigger(reminder)
        setExact(target, alarmPendingIntent(reminder.id, snooze = true))
    }

    fun resumeAfterSnooze(reminder: Reminder, allReminders: List<Reminder>? = null) {
        stateStore.clearSnooze(reminder.id)
        if (reminder.isDone) return

        if (isOneTime(reminder)) {
            stateStore.markFired(reminder.id)
            if (reminder.kind == ReminderKind.LOCATION) {
                setGeofenceState(reminder.id, GeofenceRegistrationStatus.FIRED)
            }
            cancelOriginalTrigger(reminder)
            return
        }

        when {
            reminder.kind == ReminderKind.LOCATION && reminder.repeats -> {
                if (allReminders != null) {
                    resyncGeofences(allReminders)
                } else {
                    addGeofence(reminder)
                }
            }
            reminder.kind == ReminderKind.TIME && reminder.timeRepeat != TimeRepeat.NEVER ->
                scheduleNextOccurrence(reminder)
        }
    }

    private fun isOneTime(reminder: Reminder): Boolean = when (reminder.kind) {
        ReminderKind.LOCATION -> !reminder.repeats
        ReminderKind.TIME -> reminder.timeRepeat == TimeRepeat.NEVER
    }

    fun scheduleNextOccurrence(reminder: Reminder) {
        val due = reminder.dueDate ?: return
        val next = when (reminder.timeRepeat) {
            TimeRepeat.DAILY -> nextDaily(due)
            TimeRepeat.WEEKLY -> nextWeekly(due, reminder.weekdays)
            TimeRepeat.NEVER -> return
        }
        setExact(next, alarmPendingIntent(reminder.id, snooze = false))
    }

    fun resync(all: List<Reminder>) {
        all.forEach {
            cancelNag(it.id)
            cancelLegacyPendingIntents(it.id)
        }

        val active = all.filter { !it.isDone }
        val snoozedIds = restoreSnoozes(active)

        active
            .filter { it.kind == ReminderKind.TIME && it.id !in snoozedIds }
            .forEach { scheduleAlarm(it) }

        resyncGeofences(all)
    }

    /**
     * Lehký reconcile jen pro location remindery. Používá se při přidání,
     * smazání, změně a snooze, aby se správně přepočítal limit 100 bez zásahu
     * do časových alarmů a dožadování.
     */
    fun resyncGeofences(all: List<Reminder>) {
        val activeLocations = all.filter {
            !it.isDone && it.kind == ReminderKind.LOCATION
        }
        stateStore.retainGeofenceStates(activeLocations.map { it.id }.toSet())
        refreshLegacyFailureFlag()
        queueGeofenceResync(activeLocations)
    }

    /** GeofencingEvent může oznámit chybu bez konkrétního request ID. */
    fun handleGeofenceServiceError(errorCode: Int, all: List<Reminder>) {
        val status = statusForErrorCode(errorCode)
        val now = System.currentTimeMillis()
        all.asSequence()
            .filter { !it.isDone && it.kind == ReminderKind.LOCATION }
            .filter { reminder ->
                val snoozeUntil = stateStore.snoozeUntil(reminder.id)
                (snoozeUntil == null || snoozeUntil <= now) &&
                    (reminder.repeats || !stateStore.isFired(reminder.id))
            }
            .forEach { setGeofenceState(it.id, status, errorCode) }
        Log.w(TAG, "Geofence service error: code=$errorCode status=${status.name}")
    }

    private fun restoreSnoozes(active: List<Reminder>): Set<String> {
        val byId = active.associateBy { it.id }
        val now = System.currentTimeMillis()
        val stillSnoozed = mutableSetOf<String>()

        for ((id, at) in stateStore.allSnoozes()) {
            val reminder = byId[id]
            if (reminder == null) {
                stateStore.clearSnooze(id)
                continue
            }
            if (at > now) {
                setExact(at, alarmPendingIntent(id, snooze = true))
                stillSnoozed += id
                if (reminder.kind == ReminderKind.LOCATION) {
                    setGeofenceState(id, GeofenceRegistrationStatus.SNOOZED)
                }
            } else {
                NotificationHelper.show(appContext, reminder)
                stateStore.clearSnooze(id)
                if (isOneTime(reminder)) {
                    stateStore.markFired(id)
                    if (reminder.kind == ReminderKind.LOCATION) {
                        setGeofenceState(id, GeofenceRegistrationStatus.FIRED)
                    }
                    cancelOriginalTrigger(reminder)
                }
            }
        }
        return stillSnoozed
    }

    private fun cancelOriginalTrigger(reminder: Reminder) {
        when (reminder.kind) {
            ReminderKind.LOCATION -> geofencing.removeGeofences(listOf(reminder.id))
            ReminderKind.TIME -> cancelPendingIntent(alarmPendingIntent(reminder.id, snooze = false))
        }
    }

    private fun cancelPendingIntent(pendingIntent: PendingIntent) {
        alarms.cancel(pendingIntent)
        pendingIntent.cancel()
    }

    private fun queueGeofenceResync(snapshot: List<Reminder>) {
        var startNow = false
        synchronized(geofenceResyncLock) {
            pendingGeofenceSnapshot = snapshot
            if (!geofenceResyncRunning) {
                geofenceResyncRunning = true
                startNow = true
            }
        }
        if (startNow) runNextGeofenceResync()
    }

    private fun runNextGeofenceResync() {
        val snapshot = synchronized(geofenceResyncLock) {
            val latest = pendingGeofenceSnapshot ?: emptyList()
            pendingGeofenceSnapshot = null
            latest
        }

        try {
            geofencing.removeGeofences(geofencePendingIntent())
                .addOnCompleteListener {
                    addGeofencesBatch(snapshot) {
                        finishGeofenceResyncPass()
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Hromadný reset geofence selhal", e)
            addGeofencesBatch(snapshot) {
                finishGeofenceResyncPass()
            }
        }
    }

    private fun finishGeofenceResyncPass() {
        val runAgain = synchronized(geofenceResyncLock) {
            if (pendingGeofenceSnapshot != null) {
                true
            } else {
                geofenceResyncRunning = false
                false
            }
        }
        if (runAgain) runNextGeofenceResync()
    }

    @SuppressLint("MissingPermission")
    private fun addGeofencesBatch(reminders: List<Reminder>, onComplete: () -> Unit) {
        val now = System.currentTimeMillis()
        val candidates = mutableListOf<Reminder>()

        reminders.forEach { reminder ->
            val snoozeUntil = stateStore.snoozeUntil(reminder.id)
            when {
                snoozeUntil != null && snoozeUntil > now ->
                    setGeofenceState(reminder.id, GeofenceRegistrationStatus.SNOOZED)

                !reminder.repeats && stateStore.isFired(reminder.id) ->
                    setGeofenceState(reminder.id, GeofenceRegistrationStatus.FIRED)

                !GeofencePolicy.isValidRegion(reminder) ->
                    setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_INVALID_REGION)

                else -> candidates += reminder
            }
        }

        if (candidates.isEmpty()) {
            onComplete()
            return
        }

        if (!LocationHolder.hasBackgroundLocation(appContext)) {
            candidates.forEach {
                setGeofenceState(it.id, GeofenceRegistrationStatus.FAILED_PERMISSION)
            }
            onComplete()
            return
        }

        if (!LocationHolder.isSystemLocationEnabled(appContext)) {
            candidates.forEach {
                setGeofenceState(it.id, GeofenceRegistrationStatus.FAILED_LOCATION_DISABLED)
            }
            onComplete()
            return
        }

        val capacity = GeofencePolicy.selectWithinCapacity(candidates)
        capacity.overflow.forEach {
            setGeofenceState(it.id, GeofenceRegistrationStatus.FAILED_TOO_MANY)
        }

        if (capacity.selected.isEmpty()) {
            onComplete()
            return
        }

        val geofences = capacity.selected.map { buildGeofence(it) }

        try {
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofences(geofences)
                .build()

            geofencing.addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener {
                    capacity.selected.forEach {
                        setGeofenceState(it.id, GeofenceRegistrationStatus.ACTIVE)
                    }
                }
                .addOnFailureListener { error ->
                    val (status, errorCode) = statusForException(error)
                    capacity.selected.forEach {
                        setGeofenceState(it.id, status, errorCode)
                    }
                    Log.w(TAG, "Hromadná registrace geofence selhala: status=${status.name} code=$errorCode")
                }
                .addOnCompleteListener { onComplete() }
        } catch (e: SecurityException) {
            capacity.selected.forEach {
                setGeofenceState(it.id, GeofenceRegistrationStatus.FAILED_PERMISSION)
            }
            Log.w(TAG, "Hromadná registrace geofence bez oprávnění")
            onComplete()
        } catch (e: Exception) {
            val (status, errorCode) = statusForException(e)
            capacity.selected.forEach {
                setGeofenceState(it.id, status, errorCode)
            }
            Log.w(TAG, "Hromadná registrace geofence selhala: status=${status.name} code=$errorCode")
            onComplete()
        }
    }

    @SuppressLint("MissingPermission")
    private fun addGeofence(reminder: Reminder) {
        if (!GeofencePolicy.isValidRegion(reminder)) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_INVALID_REGION)
            return
        }
        if (!LocationHolder.hasBackgroundLocation(appContext)) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_PERMISSION)
            return
        }
        if (!LocationHolder.isSystemLocationEnabled(appContext)) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_LOCATION_DISABLED)
            return
        }
        if (!reminder.repeats && stateStore.isFired(reminder.id)) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FIRED)
            return
        }
        val snoozeUntil = stateStore.snoozeUntil(reminder.id)
        if (snoozeUntil != null && snoozeUntil > System.currentTimeMillis()) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.SNOOZED)
            return
        }
        if (stateStore.activeGeofenceCount(excludingReminderId = reminder.id) >=
            GeofencePolicy.MAX_ACTIVE_GEOFENCES
        ) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_TOO_MANY)
            return
        }

        try {
            val request = GeofencingRequest.Builder()
                .setInitialTrigger(0)
                .addGeofence(buildGeofence(reminder))
                .build()

            geofencing.addGeofences(request, geofencePendingIntent())
                .addOnSuccessListener {
                    setGeofenceState(reminder.id, GeofenceRegistrationStatus.ACTIVE)
                }
                .addOnFailureListener { error ->
                    val (status, errorCode) = statusForException(error)
                    setGeofenceState(reminder.id, status, errorCode)
                    Log.w(TAG, "Registrace geofence ${reminder.id} selhala: status=${status.name} code=$errorCode")
                }
        } catch (e: SecurityException) {
            setGeofenceState(reminder.id, GeofenceRegistrationStatus.FAILED_PERMISSION)
        } catch (e: Exception) {
            val (status, errorCode) = statusForException(e)
            setGeofenceState(reminder.id, status, errorCode)
            Log.w(TAG, "Registrace geofence ${reminder.id} selhala: status=${status.name} code=$errorCode")
        }
    }

    private fun buildGeofence(reminder: Reminder): Geofence {
        require(GeofencePolicy.isValidRegion(reminder)) { "Invalid geofence region" }
        val transition = if (reminder.trigger == TriggerType.ARRIVE) {
            Geofence.GEOFENCE_TRANSITION_ENTER
        } else {
            Geofence.GEOFENCE_TRANSITION_EXIT
        }
        return Geofence.Builder()
            .setRequestId(reminder.id)
            .setCircularRegion(
                reminder.latitude,
                reminder.longitude,
                reminder.radius.toFloat(),
            )
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(transition)
            .build()
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

    private fun scheduleAlarm(reminder: Reminder) {
        val due = reminder.dueDate ?: return
        val now = System.currentTimeMillis()
        val snoozeUntil = stateStore.snoozeUntil(reminder.id)
        if (snoozeUntil != null && snoozeUntil > now) return

        if (reminder.timeRepeat == TimeRepeat.NEVER && stateStore.isFired(reminder.id)) {
            cancelPendingIntent(alarmPendingIntent(reminder.id, snooze = false))
            return
        }

        val triggerAt = when (reminder.timeRepeat) {
            TimeRepeat.NEVER -> {
                if (due <= now) {
                    NotificationHelper.show(appContext, reminder)
                    stateStore.markFired(reminder.id)
                    cancelPendingIntent(alarmPendingIntent(reminder.id, snooze = false))
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
            if (!SystemAccess.canScheduleExactAlarms(appContext)) {
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

    private fun cancelLegacyPendingIntents(reminderId: String) {
        cancelPendingIntent(legacyAlarmPendingIntent(reminderId, snooze = false))
        cancelPendingIntent(legacyAlarmPendingIntent(reminderId, snooze = true))
        cancelPendingIntent(legacyNagPendingIntent(reminderId))
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

    private fun statusForException(error: Exception): Pair<GeofenceRegistrationStatus, Int?> {
        val code = (error as? ApiException)?.statusCode
        return statusForErrorCode(code) to code
    }

    private fun statusForErrorCode(errorCode: Int?): GeofenceRegistrationStatus = when (errorCode) {
        GeofenceStatusCodes.GEOFENCE_INSUFFICIENT_LOCATION_PERMISSION ->
            GeofenceRegistrationStatus.FAILED_PERMISSION
        GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE ->
            GeofenceRegistrationStatus.FAILED_LOCATION_DISABLED
        GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES ->
            GeofenceRegistrationStatus.FAILED_TOO_MANY
        else -> GeofenceRegistrationStatus.FAILED_SERVICE
    }

    private fun setGeofenceState(
        reminderId: String,
        status: GeofenceRegistrationStatus,
        errorCode: Int? = null,
    ) {
        stateStore.setGeofenceState(reminderId, status, errorCode)
        if (status.isFailure) {
            Log.w(TAG, "Geofence state ${reminderId}: ${status.name} code=$errorCode")
        }
        refreshLegacyFailureFlag()
    }

    private fun clearGeofenceState(reminderId: String) {
        stateStore.clearGeofenceState(reminderId)
        refreshLegacyFailureFlag()
    }

    private fun refreshLegacyFailureFlag() {
        LocationHolder.geofenceFailed.value =
            stateStore.geofenceStates.value.values.any { it.status.isFailure }
    }
}
