package cz.jenda.georeminder.data

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.notify.GeofenceRegistrationStatus
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.notify.SchedulerStateStore

enum class DiagnosticEventType {
    RESYNC_OK,
    RESYNC_FAILED,
    GEOFENCE_REGISTER_OK,
    GEOFENCE_REGISTER_FAIL,
    ALARM_SCHEDULED,
    ALARM_FIRED,
    SNOOZE_SET,
    DATA_PARTIAL_RECOVERY,
}

data class DiagnosticEvent(
    val timestamp: Long,
    val type: DiagnosticEventType,
    val detail: String? = null,
)

data class DiagnosticSnapshot(
    val notificationsEnabled: Boolean,
    val fineLocationGranted: Boolean,
    val backgroundLocationGranted: Boolean,
    val systemLocationEnabled: Boolean,
    val exactAlarmsAllowed: Boolean,
    val batteryUnrestricted: Boolean,
    val activeGeoReminders: Int,
    val registeredGeofences: Int,
    val failedGeofences: Int,
    val geofenceLimitUsed: Int,
    val activeTimeReminders: Int,
    val nearestTimeAlarm: Long?,
    val snoozedReminders: Int,
    val lastSuccessfulResync: Long?,
    val lastRegistrationError: String?,
    val dataIntegrityState: ReminderStore.DataIntegrityState,
    val recentEvents: List<DiagnosticEvent>,
)

/**
 * Malá lokální technická diagnostika. Neukládá názvy reminderů, jejich ID,
 * adresy ani GPS souřadnice. Historie je omezená na posledních [MAX_EVENTS]
 * technických událostí.
 */
class DiagnosticStore private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS = "georeminder_diagnostics"
        private const val KEY_EVENTS = "events"
        private const val KEY_LAST_RESYNC = "last_resync_ok"
        internal const val MAX_EVENTS = 50

        @Volatile
        private var instance: DiagnosticStore? = null

        fun get(context: Context): DiagnosticStore =
            instance ?: synchronized(this) {
                instance ?: DiagnosticStore(context).also { instance = it }
            }

        internal fun sanitizeDetail(value: String?): String? = value
            ?.replace('|', '/')
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotEmpty() }

        internal fun encodeEvent(event: DiagnosticEvent): String = listOf(
            event.timestamp.toString(),
            event.type.name,
            sanitizeDetail(event.detail).orEmpty(),
        ).joinToString("|")

        internal fun decodeEvent(raw: String): DiagnosticEvent? {
            val parts = raw.split('|', limit = 3)
            val timestamp = parts.getOrNull(0)?.toLongOrNull() ?: return null
            val type = runCatching {
                DiagnosticEventType.valueOf(parts.getOrNull(1).orEmpty())
            }.getOrNull() ?: return null
            return DiagnosticEvent(
                timestamp = timestamp,
                type = type,
                detail = sanitizeDetail(parts.getOrNull(2)),
            )
        }

        internal fun trimEvents(events: List<DiagnosticEvent>): List<DiagnosticEvent> =
            events.takeLast(MAX_EVENTS)
    }

    @Synchronized
    fun record(
        type: DiagnosticEventType,
        detail: String? = null,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        val updated = trimEvents(
            events() + DiagnosticEvent(timestamp, type, sanitizeDetail(detail)),
        )
        prefs.edit()
            .putString(KEY_EVENTS, updated.joinToString("\n", transform = ::encodeEvent))
            .apply()
    }

    @Synchronized
    fun events(): List<DiagnosticEvent> = prefs.getString(KEY_EVENTS, null)
        .orEmpty()
        .lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull(::decodeEvent)
        .toList()
        .let(::trimEvents)

    fun markResyncSuccess(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_LAST_RESYNC, timestamp).apply()
        record(DiagnosticEventType.RESYNC_OK, timestamp = timestamp)
    }

    fun markResyncFailure(error: Throwable? = null) {
        record(
            DiagnosticEventType.RESYNC_FAILED,
            error?.javaClass?.simpleName,
        )
    }

    fun snapshot(now: Long = System.currentTimeMillis()): DiagnosticSnapshot {
        val reminderStore = ReminderStore.get(appContext)
        val reminders = reminderStore.reminders.value
        val active = reminders.filter { !it.isDone }
        val schedulerState = SchedulerStateStore(appContext)
        val geofenceStates = schedulerState.geofenceStates.value
        val snoozes = schedulerState.allSnoozes().filterValues { it > now }
        val recentEvents = events().sortedByDescending { it.timestamp }

        val activeGeo = active.count { it.kind == ReminderKind.LOCATION }
        val activeTime = active.filter { it.kind == ReminderKind.TIME }
        val nearestAlarm = activeTime
            .asSequence()
            .filter { it.id !in snoozes }
            .mapNotNull { reminder ->
                val due = reminder.dueDate ?: return@mapNotNull null
                when (reminder.timeRepeat) {
                    TimeRepeat.NEVER -> due.takeIf { it > now }
                    TimeRepeat.DAILY -> ReminderScheduler.nextDaily(due, now)
                    TimeRepeat.WEEKLY -> ReminderScheduler.nextWeekly(due, reminder.weekdays, now)
                }
            }
            .minOrNull()

        val failedStates = geofenceStates.values.filter { it.status.isFailure }
        val lastFailure = recentEvents
            .firstOrNull { it.type == DiagnosticEventType.GEOFENCE_REGISTER_FAIL }
            ?.detail

        val powerManager = appContext.getSystemService(PowerManager::class.java)
        val batteryUnrestricted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) == true
        } else {
            true
        }

        return DiagnosticSnapshot(
            notificationsEnabled = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
            fineLocationGranted = LocationHolder.hasFineLocation(appContext),
            backgroundLocationGranted = LocationHolder.hasBackgroundLocation(appContext),
            systemLocationEnabled = LocationHolder.isSystemLocationEnabled(appContext),
            exactAlarmsAllowed = SystemAccess.canScheduleExactAlarms(appContext),
            batteryUnrestricted = batteryUnrestricted,
            activeGeoReminders = activeGeo,
            registeredGeofences = geofenceStates.values.count {
                it.status == GeofenceRegistrationStatus.ACTIVE
            },
            failedGeofences = failedStates.size,
            geofenceLimitUsed = activeGeo.coerceAtMost(100),
            activeTimeReminders = activeTime.size,
            nearestTimeAlarm = nearestAlarm,
            snoozedReminders = snoozes.size,
            lastSuccessfulResync = prefs.getLong(KEY_LAST_RESYNC, 0L).takeIf { it > 0L },
            lastRegistrationError = lastFailure,
            dataIntegrityState = reminderStore.dataIntegrityState.value,
            recentEvents = recentEvents,
        )
    }

    fun technicalReport(snapshot: DiagnosticSnapshot = snapshot()): String = buildString {
        appendLine("GeoReminder diagnostics")
        appendLine("notifications=${snapshot.notificationsEnabled}")
        appendLine("fine_location=${snapshot.fineLocationGranted}")
        appendLine("background_location=${snapshot.backgroundLocationGranted}")
        appendLine("system_location=${snapshot.systemLocationEnabled}")
        appendLine("exact_alarms=${snapshot.exactAlarmsAllowed}")
        appendLine("battery_unrestricted=${snapshot.batteryUnrestricted}")
        appendLine("active_geo=${snapshot.activeGeoReminders}")
        appendLine("geofences_registered=${snapshot.registeredGeofences}")
        appendLine("geofences_failed=${snapshot.failedGeofences}")
        appendLine("geofence_limit=${snapshot.geofenceLimitUsed}/100")
        appendLine("active_time=${snapshot.activeTimeReminders}")
        appendLine("nearest_time_alarm=${snapshot.nearestTimeAlarm ?: "none"}")
        appendLine("snoozed=${snapshot.snoozedReminders}")
        appendLine("last_resync=${snapshot.lastSuccessfulResync ?: "none"}")
        appendLine("last_registration_error=${snapshot.lastRegistrationError ?: "none"}")
        appendLine("data_state=${snapshot.dataIntegrityState.name}")
        appendLine("events:")
        snapshot.recentEvents.forEach { event ->
            append(event.timestamp)
                .append(' ')
                .append(event.type.name)
            event.detail?.let { append(' ').append(sanitizeDetail(it)) }
            appendLine()
        }
    }
}
