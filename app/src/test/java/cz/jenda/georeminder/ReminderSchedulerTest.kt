package cz.jenda.georeminder

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.notify.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderSchedulerTest {
    @Test
    fun nextOccurrenceReturnsOneTimeDueDate() {
        val reminder = Reminder(
            title = "Jednorázová",
            kind = ReminderKind.TIME,
            dueDate = 42_000L,
            timeRepeat = TimeRepeat.NEVER,
        )

        assertEquals(
            42_000L,
            ReminderScheduler.nextOccurrenceAt(reminder, now = 10_000L),
        )
    }

    @Test
    fun nextOccurrenceIsNullForLocationReminder() {
        val reminder = Reminder(
            title = "Místo",
            kind = ReminderKind.LOCATION,
        )

        assertNull(ReminderScheduler.nextOccurrenceAt(reminder))
    }
}
