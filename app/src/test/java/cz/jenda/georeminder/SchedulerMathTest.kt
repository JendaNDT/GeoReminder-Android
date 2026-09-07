package cz.jenda.georeminder

import cz.jenda.georeminder.notify.ReminderScheduler
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchedulerMathTest {
    private lateinit var previousTimeZone: TimeZone
    private val zone = ZoneId.of("Europe/Prague")

    @Before
    fun setPragueTimeZone() {
        previousTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(previousTimeZone)
    }

    @Test
    fun nextDailyUsesTodayWhenTargetTimeIsStillAhead() {
        val due = localMillis(2026, 1, 1, 18, 30)
        val now = localMillis(2026, 9, 7, 10, 0)
        assertEquals(localMillis(2026, 9, 7, 18, 30), ReminderScheduler.nextDaily(due, now))
    }

    @Test
    fun nextDailyMovesToTomorrowAfterTargetTime() {
        val due = localMillis(2026, 1, 1, 7, 0)
        val now = localMillis(2026, 9, 7, 16, 35)
        assertEquals(localMillis(2026, 9, 8, 7, 0), ReminderScheduler.nextDaily(due, now))
    }

    @Test
    fun nextDailyCrossesYearBoundary() {
        val due = localMillis(2026, 1, 1, 8, 15)
        val now = localMillis(2026, 12, 31, 20, 0)
        assertEquals(localMillis(2027, 1, 1, 8, 15), ReminderScheduler.nextDaily(due, now))
    }

    @Test
    fun nextDailyHandlesSpringDstGapDeterministically() {
        val due = localMillis(2026, 3, 28, 2, 30)
        val now = localMillis(2026, 3, 28, 23, 0)
        val actual = ReminderScheduler.nextDaily(due, now)
        val local = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(actual), zone)

        assertEquals(2026, local.year)
        assertEquals(3, local.monthValue)
        assertEquals(29, local.dayOfMonth)
        // 02:30 v den přechodu neexistuje, Calendar ho normalizuje na 03:30.
        assertEquals(3, local.hour)
        assertEquals(30, local.minute)
    }

    @Test
    fun nextWeeklySupportsEveryIsoWeekday() {
        val due = localMillis(2026, 1, 5, 18, 30)
        val mondayMorning = localMillis(2026, 9, 7, 10, 0)

        for (isoDay in 1..7) {
            val actual = ReminderScheduler.nextWeekly(due, listOf(isoDay), mondayMorning)
            val local = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(actual), zone)
            assertEquals("ISO weekday $isoDay", isoDay, local.dayOfWeek.value)
            assertEquals(18, local.hour)
            assertEquals(30, local.minute)
            assertTrue(actual > mondayMorning)
        }
    }

    @Test
    fun nextWeeklyUsesLaterSelectedDayWhenTodayAlreadyPassed() {
        val due = localMillis(2026, 1, 5, 7, 0)
        val mondayAfternoon = localMillis(2026, 9, 7, 16, 0)
        val actual = ReminderScheduler.nextWeekly(due, listOf(1, 3, 5), mondayAfternoon)
        assertEquals(localMillis(2026, 9, 9, 7, 0), actual)
    }

    @Test
    fun nextWeeklyFallsBackToDueWeekdayWhenListMissing() {
        val due = localMillis(2026, 1, 7, 12, 0) // středa
        val now = localMillis(2026, 9, 7, 10, 0) // pondělí
        val actual = ReminderScheduler.nextWeekly(due, null, now)
        assertEquals(localMillis(2026, 9, 9, 12, 0), actual)
    }

    @Test
    fun isoWeekdayMapsMondayThroughSundayToOneThroughSeven() {
        for (isoDay in 1..7) {
            val date = java.time.LocalDate.of(2026, 9, 7).plusDays((isoDay - 1).toLong())
            val millis = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            assertEquals(isoDay, ReminderScheduler.isoWeekday(millis))
        }
    }

    private fun localMillis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, minute)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
}
