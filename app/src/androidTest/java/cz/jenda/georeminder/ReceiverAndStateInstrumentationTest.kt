package cz.jenda.georeminder

import android.app.PendingIntent
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cz.jenda.georeminder.data.ReminderStore
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.notify.AlarmReceiver
import cz.jenda.georeminder.notify.NotificationActionReceiver
import cz.jenda.georeminder.notify.NotificationHelper
import cz.jenda.georeminder.notify.ReminderScheduler
import cz.jenda.georeminder.notify.SchedulerStateStore
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReceiverAndStateInstrumentationTest {

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Before
    fun setUp() {
        context.getSharedPreferences(SharedStorage.PREFS, 0).edit().clear().commit()
        SharedStorage.file(context, ReminderStore.FILE).delete()
    }

    @Test
    fun schedulerStatePersistsSnoozeAndStableRequestCodes() {
        val first = SchedulerStateStore(context)
        first.setSnooze("R1", 1_900_000_000_000L)
        val alarmCode = first.requestCode("R1", SchedulerStateStore.OFFSET_ALARM)
        val snoozeCode = first.requestCode("R1", SchedulerStateStore.OFFSET_SNOOZE)

        val second = SchedulerStateStore(context)
        assertEquals(1_900_000_000_000L, second.snoozeUntil("R1"))
        assertEquals(alarmCode, second.requestCode("R1", SchedulerStateStore.OFFSET_ALARM))
        assertTrue(alarmCode != snoozeCode)
    }

    @Test
    fun notificationTokenCanBeConsumedOnlyOnce() {
        val state = SchedulerStateStore(context)
        state.setNotificationActionToken("R1", "token-1")
        assertTrue(state.consumeNotificationActionToken("R1", "token-1"))
        assertFalse(state.consumeNotificationActionToken("R1", "token-1"))
    }

    @Test
    fun alarmReceiverLoadsReminderFromDiskBeforeFiring() {
        val reminder = Reminder(
            id = "alarm-cold-start",
            title = "Cold start alarm",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 60_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        writeSnapshot(listOf(reminder))
        val state = SchedulerStateStore(context)
        state.clearFired(reminder.id)

        context.sendBroadcast(
            Intent(context, AlarmReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_ALARM_FIRE)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminder.id),
        )

        waitUntil { SchedulerStateStore(context).isFired(reminder.id) }
        assertTrue(SchedulerStateStore(context).isFired(reminder.id))
    }

    @Test
    fun notificationDoneActionLoadsFromDiskIsIdempotentAndCancelsAlarms() {
        val reminder = Reminder(
            id = "done-cold-start",
            title = "Cold start done",
            kind = ReminderKind.TIME,
            dueDate = System.currentTimeMillis() + 3_600_000L,
            timeRepeat = TimeRepeat.NEVER,
        )
        writeSnapshot(listOf(reminder))
        val state = SchedulerStateStore(context)
        val scheduler = ReminderScheduler.get(context)
        scheduler.schedule(reminder)
        scheduler.scheduleNag(reminder)
        assertNotNull(normalAlarmPendingIntent(reminder.id, state))
        assertNotNull(nagPendingIntent(reminder.id, state))
        state.setNotificationActionToken(reminder.id, "done-token")

        val action = Intent(context, NotificationActionReceiver::class.java)
            .setAction(NotificationHelper.ACTION_DONE)
            .putExtra(NotificationHelper.EXTRA_REMINDER_ID, reminder.id)
            .putExtra(NotificationHelper.EXTRA_NOTIFICATION_TOKEN, "done-token")

        context.sendBroadcast(action)
        waitUntil {
            val loaded = runBlocking { ReminderStore.get(context).reloadAndWait() }
            loaded != ReminderStore.ReloadResult.ERROR &&
                ReminderStore.get(context).reminders.value.firstOrNull { it.id == reminder.id }?.isDone == true
        }

        assertNull(normalAlarmPendingIntent(reminder.id, state))
        assertNull(nagPendingIntent(reminder.id, state))

        context.sendBroadcast(action)
        Thread.sleep(300)
        runBlocking { ReminderStore.get(context).reloadAndWait() }
        assertTrue(ReminderStore.get(context).reminders.value.single { it.id == reminder.id }.isDone)
        assertFalse(SchedulerStateStore(context).consumeNotificationActionToken(reminder.id, "done-token"))
    }

    private fun normalAlarmPendingIntent(reminderId: String, state: SchedulerStateStore): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            state.requestCode(reminderId, SchedulerStateStore.OFFSET_ALARM),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_ALARM_FIRE)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun nagPendingIntent(reminderId: String, state: SchedulerStateStore): PendingIntent? =
        PendingIntent.getBroadcast(
            context,
            state.requestCode(reminderId, SchedulerStateStore.OFFSET_NAG),
            Intent(context, AlarmReceiver::class.java)
                .setAction(ReminderScheduler.ACTION_NAG_FIRE)
                .putExtra(ReminderScheduler.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun writeSnapshot(reminders: List<Reminder>) {
        val text = SharedStorage.json.encodeToString(
            ListSerializer(Reminder.serializer()),
            reminders,
        )
        assertTrue(SharedStorage.writeText(context, ReminderStore.FILE, text))
    }

    private fun waitUntil(timeoutMs: Long = 5_000L, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Condition was not met within ${timeoutMs} ms")
    }
}
