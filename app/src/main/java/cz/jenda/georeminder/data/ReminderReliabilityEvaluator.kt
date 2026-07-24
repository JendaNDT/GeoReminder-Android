package cz.jenda.georeminder.data

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat

enum class GeofenceRegistrationState {
    PENDING,
    ACTIVE,
    FAILED,
}

enum class ReminderReadinessCode {
    COMPLETED,
    NOTIFICATIONS_BLOCKED,
    BATTERY_RESTRICTED,
    LOCATION_PERMISSION_REQUIRED,
    BACKGROUND_LOCATION_REQUIRED,
    GEOFENCE_PENDING,
    GEOFENCE_ACTIVE,
    GEOFENCE_FAILED,
    TIME_EXACT,
    TIME_APPROXIMATE,
    TIME_PAST,
}

object ReminderReliabilityEvaluator {
    fun evaluate(
        reminder: Reminder,
        notificationsEnabled: Boolean,
        batteryUnrestricted: Boolean,
        fineLocationGranted: Boolean,
        backgroundLocationGranted: Boolean,
        exactAlarmsGranted: Boolean,
        geofenceState: GeofenceRegistrationState?,
        now: Long = System.currentTimeMillis(),
    ): ReminderReadinessCode {
        if (reminder.isDone) return ReminderReadinessCode.COMPLETED
        if (!notificationsEnabled) return ReminderReadinessCode.NOTIFICATIONS_BLOCKED
        if (!batteryUnrestricted) return ReminderReadinessCode.BATTERY_RESTRICTED

        if (reminder.kind == ReminderKind.LOCATION) {
            if (!fineLocationGranted) {
                return ReminderReadinessCode.LOCATION_PERMISSION_REQUIRED
            }
            if (!backgroundLocationGranted) {
                return ReminderReadinessCode.BACKGROUND_LOCATION_REQUIRED
            }
            return when (geofenceState) {
                GeofenceRegistrationState.ACTIVE -> ReminderReadinessCode.GEOFENCE_ACTIVE
                GeofenceRegistrationState.FAILED -> ReminderReadinessCode.GEOFENCE_FAILED
                GeofenceRegistrationState.PENDING,
                null -> ReminderReadinessCode.GEOFENCE_PENDING
            }
        }

        if (reminder.timeRepeat == TimeRepeat.NEVER &&
            (reminder.dueDate ?: Long.MAX_VALUE) <= now
        ) {
            return ReminderReadinessCode.TIME_PAST
        }
        return if (exactAlarmsGranted) {
            ReminderReadinessCode.TIME_EXACT
        } else {
            ReminderReadinessCode.TIME_APPROXIMATE
        }
    }
}
