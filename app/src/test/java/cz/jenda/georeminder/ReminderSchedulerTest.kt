package cz.jenda.georeminder

import cz.jenda.georeminder.notify.ReminderScheduler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ReminderSchedulerTest {
    private lateinit var originalZone: TimeZone

    @Before
    fun useUtc() {
        originalZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(originalZone)
    }

    @Test
    fun nextDailyMovesPastTimeToTomorrow() {
        val due = utcMillis(2026, Calendar.JANUARY, 1, 8, 30)
        val now = utcMillis(2026, Calendar.JANUARY, 1, 9, 0)
        val expected = utcMillis(2026, Calendar.JANUARY, 2, 8, 30)
        assertEquals(expected, ReminderScheduler.nextDaily(due, now))
    }

    @Test
    fun nextWeeklyChoosesNextSelectedDay() {
        // 6 July 2026 is Monday; Monday's time has already passed.
        val due = utcMillis(2026, Calendar.JULY, 6, 8, 0)
        val now = utcMillis(2026, Calendar.JULY, 6, 9, 0)
        val expectedWednesday = utcMillis(2026, Calendar.JULY, 8, 8, 0)
        assertEquals(
            expectedWednesday,
            ReminderScheduler.nextWeekly(due, listOf(1, 3), now),
        )
    }

    @Test
    fun nextWeeklyNeverReturnsPastForInvalidImportedDays() {
        val due = utcMillis(2026, Calendar.JULY, 6, 8, 0)
        val now = utcMillis(2026, Calendar.JULY, 6, 9, 0)
        assertTrue(ReminderScheduler.nextWeekly(due, listOf(99), now) > now)
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
}
