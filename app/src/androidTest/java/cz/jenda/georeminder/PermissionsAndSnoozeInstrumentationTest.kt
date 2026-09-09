package cz.jenda.georeminder

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cz.jenda.georeminder.data.LocationHolder
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.data.SystemAccess
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.notify.AlarmReceiver
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.notify.SchedulerStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PermissionsAndSnoozeInstrumentationTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun setUp() {
        context.getSharedPreferences(SharedStorage.PREFS, 0).edit().clear().commit()
    }

    @Test
    fun test01FineLocationStartsDeniedAndCanBeGranted() {
        assertFalse(LocationHolder.hasFineLocation(context))
        grantRuntimePermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertFalse(LocationHolder.hasFineLocation(context))
        grantRuntimePermission(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(LocationHolder.hasFineLocation(context))
    }

    @Test
    fun test02BackgroundLocationStartsDeniedAndCanBeGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        assertFalse(LocationHolder.hasBackgroundLocation(context))
        grantRuntimePermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        grantRuntimePermission(Manifest.permission.ACCESS_FINE_LOCATION)
        grantRuntimePermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        assertTrue(LocationHolder.hasBackgroundLocation(context))
    }

    @Test
    fun test03NotificationPermissionStartsDeniedAndCanBeGranted() {
        if (Build.VERSION.SDK_INT < 33) return

        assertEquals(
            PackageManager.PERMISSION_DENIED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
        grantRuntimePermission(Manifest.permission.POST_NOTIFICATIONS)
        assertEquals(
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS),
        )
    }

    @Test
    fun test04ExactAlarmCapabilityMatchesPlatformAlarmManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            assertTrue(SystemAccess.canScheduleExactAlarms(context))
            return
        }
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        assertEquals(alarmManager.canScheduleExactAlarms(), SystemAccess.canScheduleExactAlarms(context))
    }

    @Test
    fun test05TimeSnoozeCancelsOriginalAlarmAndPersistsSnooze() {
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

    @Test
    fun test06PersistedSnoozeIsRestoredByResyncWithoutOriginalAlarm() {
        val reminder = Reminder(
            id = "snooze-restore",
            title = "Restore snooze",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 3_600_000L,
            timeRepeat = TimeRepeat.DAILY,
        )
        val state = SchedulerStateStore(context)
        val target = System.currentTimeMillis() + 180_000L
        state.setSnooze(reminder.id, target)

        ReminderScheduler.get(context).resync(listOf(reminder))

        assertEquals(target, SchedulerStateStore(context).snoozeUntil(reminder.id))
        assertNull(normalAlarmPendingIntent(reminder.id, state))
        assertNotNull(snoozePendingIntent(reminder.id, state))

        ReminderScheduler.get(context).cancel(reminder.id)
    }

    private fun grantRuntimePermission(permission: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
        } else {
            // Reading to EOF waits for pm; closing its pipe immediately races the grant.
            val descriptor = instrumentation.uiAutomation
                .executeShellCommand("pm grant ${context.packageName} $permission")
            val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                .bufferedReader().use { it.readText() }
            assertTrue("pm grant failed: $output", output.isBlank())
        }
        val deadline = SystemClock.elapsedRealtime() + 5_000L
        while (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED &&
            SystemClock.elapsedRealtime() < deadline
        ) {
            SystemClock.sleep(50L)
        }
        assertEquals(
            "Permission was not granted within 5 seconds: $permission",
            PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(permission),
        )
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
