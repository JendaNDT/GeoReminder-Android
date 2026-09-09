package cz.jenda.georeminder.notify

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind

/** Čistá, testovatelná pravidla pro geofence bez závislosti na Android runtime. */
object GeofencePolicy {
    const val MAX_ACTIVE_GEOFENCES = 100
    const val MIN_RADIUS_METERS = 50.0
    const val MAX_RADIUS_METERS = 1000.0

    data class CapacitySelection(
        val selected: List<Reminder>,
        val overflow: List<Reminder>,
    )

    fun isValidRegion(reminder: Reminder): Boolean {
        if (reminder.kind != ReminderKind.LOCATION) return false
        return isValidRegion(reminder.latitude, reminder.longitude, reminder.radius)
    }

    fun isValidRegion(latitude: Double, longitude: Double, radius: Double): Boolean {
        return latitude.isFinite() && longitude.isFinite() && radius.isFinite() &&
            latitude in -90.0..90.0 &&
            longitude in -180.0..180.0 &&
            radius in MIN_RADIUS_METERS..MAX_RADIUS_METERS
    }

    /**
     * Při překročení limitu zůstávají aktivní nejstarší připomínky. Pořadí je
     * deterministické i po restartu, takže 101. reminder svévolně nevytlačí jiný.
     */
    fun selectWithinCapacity(reminders: List<Reminder>): CapacitySelection {
        val ordered = reminders.sortedWith(
            compareBy<Reminder> { it.createdAt }.thenBy { it.id },
        )
        return CapacitySelection(
            selected = ordered.take(MAX_ACTIVE_GEOFENCES),
            overflow = ordered.drop(MAX_ACTIVE_GEOFENCES),
        )
    }
}
