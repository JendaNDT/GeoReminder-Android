package cz.jenda.georeminder.widget

import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import java.util.Calendar
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

internal object WidgetOrdering {
    const val FRESH_LOCATION_MAX_AGE_MS = 15L * 60L * 1000L

    data class UserLocation(
        val latitude: Double,
        val longitude: Double,
        val timestampMillis: Long,
    )

    fun sort(
        reminders: List<Reminder>,
        now: Long = System.currentTimeMillis(),
        userLocation: UserLocation? = null,
    ): List<Reminder> {
        val freshLocation = userLocation?.takeIf {
            it.latitude.isFinite() && it.longitude.isFinite() &&
                it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 &&
                it.timestampMillis > 0L && now - it.timestampMillis in 0..FRESH_LOCATION_MAX_AGE_MS
        }

        return reminders.sortedWith(
            compareBy<Reminder> { reminder ->
                when {
                    reminder.kind == ReminderKind.TIME -> 0
                    freshLocation != null && reminder.kind == ReminderKind.LOCATION -> 1
                    else -> 2
                }
            }.thenBy { reminder ->
                when {
                    reminder.kind == ReminderKind.TIME -> nextTime(reminder, now)
                    freshLocation != null && reminder.kind == ReminderKind.LOCATION ->
                        distanceMeters(
                            freshLocation.latitude,
                            freshLocation.longitude,
                            reminder.latitude,
                            reminder.longitude,
                        ).toLong()
                    else -> Long.MAX_VALUE - reminder.createdAt.coerceAtLeast(0L)
                }
            }.thenBy { it.id }
        )
    }

    internal fun nextTime(reminder: Reminder, now: Long): Long {
        val due = reminder.dueDate ?: return Long.MAX_VALUE
        return when (reminder.timeRepeat) {
            TimeRepeat.NEVER -> if (due <= now) now else due
            TimeRepeat.DAILY -> nextDaily(due, now)
            TimeRepeat.WEEKLY -> nextWeekly(due, reminder.weekdays, now)
        }
    }

    private fun nextDaily(dueMillis: Long, now: Long): Long {
        val due = Calendar.getInstance().apply { timeInMillis = dueMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, due.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, due.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    private fun nextWeekly(dueMillis: Long, weekdays: List<Int>?, now: Long): Long {
        val targetDays = weekdays?.filter { it in 1..7 }?.distinct()?.takeIf { it.isNotEmpty() }
            ?: listOf(isoWeekday(dueMillis))
        val due = Calendar.getInstance().apply { timeInMillis = dueMillis }
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, due.get(Calendar.HOUR_OF_DAY))
            set(Calendar.MINUTE, due.get(Calendar.MINUTE))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (isoWeekday(next.timeInMillis) in targetDays && next.timeInMillis > now) {
                return next.timeInMillis
            }
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return Long.MAX_VALUE
    }

    private fun isoWeekday(millis: Long): Int {
        val dow = Calendar.getInstance().apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)
        return ((dow + 5) % 7) + 1
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        if (!lat2.isFinite() || !lon2.isFinite() || lat2 !in -90.0..90.0 || lon2 !in -180.0..180.0) {
            return Double.MAX_VALUE
        }
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * earthRadius * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
}
