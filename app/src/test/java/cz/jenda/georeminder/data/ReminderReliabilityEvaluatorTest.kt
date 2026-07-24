package cz.jenda.georeminder.data

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderReliabilityEvaluatorTest {
    private val locationReminder = Reminder(
        id = "LOCATION",
        title = "Nákup",
        kind = ReminderKind.LOCATION,
    )

    @Test
    fun locationReminderRequiresBackgroundPermission() {
        val result = ReminderReliabilityEvaluator.evaluate(
            reminder = locationReminder,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            fineLocationGranted = true,
            backgroundLocationGranted = false,
            exactAlarmsGranted = true,
            geofenceState = null,
        )

        assertEquals(ReminderReadinessCode.BACKGROUND_LOCATION_REQUIRED, result)
    }

    @Test
    fun activeGeofenceIsReportedReady() {
        val result = ReminderReliabilityEvaluator.evaluate(
            reminder = locationReminder,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            fineLocationGranted = true,
            backgroundLocationGranted = true,
            exactAlarmsGranted = true,
            geofenceState = GeofenceRegistrationState.ACTIVE,
        )

        assertEquals(ReminderReadinessCode.GEOFENCE_ACTIVE, result)
    }

    @Test
    fun timeReminderDistinguishesExactAndApproximateScheduling() {
        val reminder = Reminder(
            id = "TIME",
            title = "Zavolat",
            kind = ReminderKind.TIME,
            dueDate = 20_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        val common = ReminderReliabilityEvaluator.evaluate(
            reminder = reminder,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            fineLocationGranted = false,
            backgroundLocationGranted = false,
            exactAlarmsGranted = true,
            geofenceState = null,
            now = 10_000L,
        )
        val approximate = ReminderReliabilityEvaluator.evaluate(
            reminder = reminder,
            notificationsEnabled = true,
            batteryUnrestricted = true,
            fineLocationGranted = false,
            backgroundLocationGranted = false,
            exactAlarmsGranted = false,
            geofenceState = null,
            now = 10_000L,
        )

        assertEquals(ReminderReadinessCode.TIME_EXACT, common)
        assertEquals(ReminderReadinessCode.TIME_APPROXIMATE, approximate)
    }

    @Test
    fun globalNotificationBlockHasPriority() {
        val result = ReminderReliabilityEvaluator.evaluate(
            reminder = locationReminder,
            notificationsEnabled = false,
            batteryUnrestricted = true,
            fineLocationGranted = true,
            backgroundLocationGranted = true,
            exactAlarmsGranted = true,
            geofenceState = GeofenceRegistrationState.ACTIVE,
        )

        assertEquals(ReminderReadinessCode.NOTIFICATIONS_BLOCKED, result)
    }
}
