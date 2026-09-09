package cz.jenda.georeminder

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.notify.GeofencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofencePolicyTest {

    @Test
    fun validRegion_acceptsSupportedBoundaries() {
        assertTrue(GeofencePolicy.isValidRegion(-90.0, -180.0, 50.0))
        assertTrue(GeofencePolicy.isValidRegion(90.0, 180.0, 1000.0))
        assertTrue(GeofencePolicy.isValidRegion(49.75, 13.38, 150.0))
    }

    @Test
    fun validRegion_rejectsInvalidCoordinatesAndRadius() {
        assertFalse(GeofencePolicy.isValidRegion(-90.0001, 0.0, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(90.0001, 0.0, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(0.0, -180.0001, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(0.0, 180.0001, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(0.0, 0.0, 49.9))
        assertFalse(GeofencePolicy.isValidRegion(0.0, 0.0, 1000.1))
        assertFalse(GeofencePolicy.isValidRegion(Double.NaN, 0.0, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(0.0, Double.POSITIVE_INFINITY, 150.0))
        assertFalse(GeofencePolicy.isValidRegion(0.0, 0.0, Double.NEGATIVE_INFINITY))
    }

    @Test
    fun validRegion_rejectsTimeReminder() {
        val reminder = Reminder(kind = ReminderKind.TIME)
        assertFalse(GeofencePolicy.isValidRegion(reminder))
    }

    @Test
    fun capacity_keepsOldest100AndReportsOverflow() {
        val reminders = (0 until 101).map { index ->
            Reminder(
                id = "R-$index",
                kind = ReminderKind.LOCATION,
                latitude = 49.0,
                longitude = 13.0,
                radius = 150.0,
                createdAt = 1_000L + index,
            )
        }.reversed()

        val selection = GeofencePolicy.selectWithinCapacity(reminders)

        assertEquals(100, selection.selected.size)
        assertEquals(1, selection.overflow.size)
        assertEquals("R-0", selection.selected.first().id)
        assertEquals("R-99", selection.selected.last().id)
        assertEquals("R-100", selection.overflow.single().id)
    }

    @Test
    fun capacity_isDeterministicForEqualTimestamps() {
        val reminders = listOf(
            Reminder(id = "B", kind = ReminderKind.LOCATION, createdAt = 1L),
            Reminder(id = "A", kind = ReminderKind.LOCATION, createdAt = 1L),
        )

        val selection = GeofencePolicy.selectWithinCapacity(reminders)

        assertEquals(listOf("A", "B"), selection.selected.map { it.id })
        assertTrue(selection.overflow.isEmpty())
    }
}
