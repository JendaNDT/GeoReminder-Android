package cz.jenda.georeminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
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

    fun testNotificationPermissionGrantAndRevokeAreReflected() {
        if (Build.VERSION.SDK_INT < 33) return
        val automation = instrumentation.uiAutomation
        val packageName = context.packageName

        automation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )

        automation.revokeRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    fun testExactAlarmCapabilityMatchesPlatformAlarmManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue(SystemAccess.canScheduleExactAlarms(context))
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(alarmManager.canScheduleExactAlarms(), SystemAccess.canScheduleExactAlarms(context))
    }

    fun testTimeSnoozeCancelsOriginalAlarmAndPersistsSnooze() {
        val reminder = Reminder(
            id = "snooze-time",
            title = "Snooze test",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 3_600_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        val scheduler = ReminderScheduler.get(context)
        val state = SchedulerStateStore(context)

        scheduler.schedule(reminder)
        assertNotNull(normalAlarmPendingIntent(reminder.id, state))

        val target = System.currentTimeMillis() + 120_000L
        scheduler.snoozeAt(reminder, target)

        val stored = SchedulerStateStore(context).snoozeUntil(reminder.id)
        assertNotNull(stored)
        assertTrue(stored!! >= target)
        assertNull(normalAlarmPendingIntent(reminder.id, state))
        assertNotNull(snoozePendingIntent(reminder.id, state))

        scheduler.cancel(reminder.id)
    }

    private fun normalAlarmPendingIntent(
        reminderId: String,
        state: SchedulerStateStore,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        state.requestCode(reminderId, SchedulerStateStore.OFFSET_ALARM),
        Intent(context, AlarmReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_ALARM_FIRE)
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun snoozePendingIntent(
        reminderId: String,
        state: SchedulerStateStore,
    ): PendingIntent? = PendingIntent.getBroadcast(
        context,
        state.requestCode(reminderId, SchedulerStateStore.OFFSET_SNOOZE),
        Intent(context, AlarmReceiver::class.java)
            .setAction(ReminderScheduler.ACTION_SNOOZE_FIRE)
            .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )
}
