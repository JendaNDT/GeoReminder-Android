package cz.jenda.georeminder

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.widget.WidgetOrdering
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetOrderingTest {
    @Test
    fun `time reminder ma prednost pred geo a radi se podle nejblizsiho vyskytu`() {
        val now = 1_700_000_000_000L
        val later = Reminder(id = "later", kind = ReminderKind.TIME, dueDate = now + 20_000, timeRepeat = TimeRepeat.NEVER)
        val sooner = Reminder(id = "sooner", kind = ReminderKind.TIME, dueDate = now + 10_000, timeRepeat = TimeRepeat.NEVER)
        val geo = Reminder(id = "geo", kind = ReminderKind.LOCATION, latitude = 50.0, longitude = 14.0)

        val sorted = WidgetOrdering.sort(listOf(later, geo, sooner), now)
        assertEquals(listOf("sooner", "later", "geo"), sorted.map { it.id })
    }

    @Test
    fun `cerstva poloha radi geo podle vzdalenosti`() {
        val now = 1_700_000_000_000L
        val far = Reminder(id = "far", kind = ReminderKind.LOCATION, latitude = 50.1, longitude = 14.0)
        val near = Reminder(id = "near", kind = ReminderKind.LOCATION, latitude = 50.001, longitude = 14.0)
        val location = WidgetOrdering.UserLocation(50.0, 14.0, now - 1_000)

        val sorted = WidgetOrdering.sort(listOf(far, near), now, location)
        assertEquals(listOf("near", "far"), sorted.map { it.id })
    }

    @Test
    fun `stara poloha se ignoruje a geo fallbackuje na createdAt`() {
        val now = 1_700_000_000_000L
        val older = Reminder(id = "old", kind = ReminderKind.LOCATION, createdAt = now - 20_000)
        val newer = Reminder(id = "new", kind = ReminderKind.LOCATION, createdAt = now - 10_000)
        val stale = WidgetOrdering.UserLocation(50.0, 14.0, now - WidgetOrdering.FRESH_LOCATION_MAX_AGE_MS - 1)

        val sorted = WidgetOrdering.sort(listOf(older, newer), now, stale)
        assertEquals(listOf("new", "old"), sorted.map { it.id })
    }
}
