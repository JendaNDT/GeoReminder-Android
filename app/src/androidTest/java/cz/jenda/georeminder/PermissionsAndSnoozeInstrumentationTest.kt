package cz.jenda.georeminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.test.InstrumentationTestCase
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.data.SystemAccess
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.notify.AlarmReceiver
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.notify.SchedulerStateStore

@Suppress("DEPRECATION")
class PermissionsAndSnoozeInstrumentationTest : InstrumentationTestCase() {

    private val context get() = instrumentation.targetContext

    override fun setUp() {
        super.setUp()
        context.getSharedPreferences(SharedStorage.PREFS, 0).edit().clear().commit()
    }

    fun testFineLocationGrantAndRevokeAreReflected() {
        val automation = instrumentation.uiAutomation
        val packageName = context.packageName

        automation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(LocationHolder.hasFineLocation(context))

        automation.revokeRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        assertFalse(LocationHolder.hasFineLocation(context))
    }

    fun testBackgroundLocationGrantAndRevokeAreReflected() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val automation = instrumentation.uiAutomation
        val packageName = context.packageName

        automation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(LocationHolder.hasBackgroundLocation(context))

        automation.revokeRuntimePermission(packageName, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertFalse(LocationHolder.hasBackgroundLocation(context))
    }

    fun testExactAlarmCapabilityMatchesPlatformAlarmManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue(SystemAccess.canScheduleExactAlarms(context))
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(alarmManager.canScheduleExactAlarms(), SystemAccess.canScheduleExactAlarms(context))
    }

    fun testTimeSnoozePersistsAndCreatesSnoozePendingIntent() {
        val reminder = Reminder(
            id = "snooze-time",
            title = "Snooze test",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 3_600_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        val target = System.currentTimeMillis() + 120_000L
        ReminderScheduler.get(context).snoozeAt(reminder, target)

        val state = SchedulerStateStore(context)
        val stored = state.snoozeUntil(reminder.id)
        assertNotNull(stored)
        assertTrue(stored!! >= target)

        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            state.requestCode(reminder.id, SchedulerStateStore.OFFSET_SNOOZE),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_SNOOZE_FIRE)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        assertNotNull(snoozeIntent)

        ReminderScheduler.get(context).cancel(reminder.id)
        snoozeIntent?.cancel()
    }
}
