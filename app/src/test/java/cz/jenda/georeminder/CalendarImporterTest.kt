package cz.jenda.georeminder

import cz.jenda.georeminder.data.CalendarEventItem
import cz.jenda.georeminder.data.CalendarImporter
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarImporterTest {
    @Test
    fun `source key rozlisuje jednotlive instance stejne udalosti`() {
        assertEquals("42:1000", CalendarImporter.sourceKey(42, 1000))
        assertEquals("42:2000", CalendarImporter.sourceKey(42, 2000))
    }

    @Test
    fun `reminder si zachova zdrojovy instance key`() {
        val event = CalendarEventItem(
            eventId = 42,
            instanceKey = "42:123456",
            title = "Porada",
            startTimeMillis = 123456,
            endTimeMillis = 124000,
            location = "Kancelář",
            allDay = false,
        )
        val reminder = CalendarImporter.toReminder(event)
        assertEquals("42:123456", reminder.calendarSourceKey)
        assertEquals(123456, reminder.dueDate)
        assertEquals("Kancelář", reminder.placeName)
    }
}
