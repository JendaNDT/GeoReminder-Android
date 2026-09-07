package cz.jenda.georeminder

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.ui.editorInitialDueDate
import org.junit.Assert.assertEquals
import org.junit.Test

class EditReminderPolicyTest {
    @Test fun repeatingDailyKeepsOriginalDueDate() {
        val now = 2_000_000L
        val original = 1_000_000L
        val reminder = Reminder(title = "Denní", kind = ReminderKind.TIME, dueDate = original, timeRepeat = TimeRepeat.DAILY)
        assertEquals(original, editorInitialDueDate(reminder, now))
    }

    @Test fun repeatingWeeklyKeepsOriginalDueDate() {
        val now = 2_000_000L
        val original = 1_000_000L
        val reminder = Reminder(title = "Týdenní", kind = ReminderKind.TIME, dueDate = original, timeRepeat = TimeRepeat.WEEKLY, weekdays = listOf(1, 3, 5))
        assertEquals(original, editorInitialDueDate(reminder, now))
    }

    @Test fun oneTimePastReminderIsClampedToNow() {
        val now = 2_000_000L
        val reminder = Reminder(title = "Jednorázová", kind = ReminderKind.TIME, dueDate = 1_000_000L, timeRepeat = TimeRepeat.NEVER)
        assertEquals(now, editorInitialDueDate(reminder, now))
    }

    @Test fun oneTimeFutureReminderKeepsDueDate() {
        val now = 2_000_000L
        val future = 3_000_000L
        val reminder = Reminder(title = "Jednorázová", kind = ReminderKind.TIME, dueDate = future, timeRepeat = TimeRepeat.NEVER)
        assertEquals(future, editorInitialDueDate(reminder, now))
    }

    @Test fun newReminderDefaultsToOneHourFromNow() {
        val now = 2_000_000L
        assertEquals(now + 3_600_000L, editorInitialDueDate(null, now))
    }
}
