package cz.jenda.georeminder.notify

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Čistá matematika opakovaných časových připomínek.
 *
 * Produkce používá systémové časové pásmo, testy mohou dodat explicitní ZoneId,
 * takže DST, přechod roku a jednotlivé dny týdne jsou deterministické.
 */
internal object SchedulerTimeMath {

    fun nextDaily(
        dueMillis: Long,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val dueTime = localReminderTime(dueMillis, zoneId)
        val nowInstant = Instant.ofEpochMilli(now)
        val today = nowInstant.atZone(zoneId).toLocalDate()

        var candidate = candidate(today, dueTime, zoneId)
        if (!candidate.toInstant().isAfter(nowInstant)) {
            candidate = candidate(today.plusDays(1), dueTime, zoneId)
        }
        return candidate.toInstant().toEpochMilli()
    }

    fun isoWeekday(
        millis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Int = Instant.ofEpochMilli(millis)
        .atZone(zoneId)
        .dayOfWeek
        .value

    fun nextWeekly(
        dueMillis: Long,
        weekdays: List<Int>?,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val dueTime = localReminderTime(dueMillis, zoneId)
        val fallbackDay = isoWeekday(dueMillis, zoneId)
        val targetDays = weekdays
            ?.filter { it in 1..7 }
            ?.distinct()
            ?.takeIf { it.isNotEmpty() }
            ?.toSet()
            ?: setOf(fallbackDay)

        val nowInstant = Instant.ofEpochMilli(now)
        val today = nowInstant.atZone(zoneId).toLocalDate()

        for (offset in 0..7) {
            val date = today.plusDays(offset.toLong())
            if (date.dayOfWeek.value !in targetDays) continue
            val candidate = candidate(date, dueTime, zoneId)
            if (candidate.toInstant().isAfter(nowInstant)) {
                return candidate.toInstant().toEpochMilli()
            }
        }

        // Teoreticky sem nedojdeme, ale kontrakt musí vždy vrátit budoucí čas.
        var date = today.plusDays(1)
        while (date.dayOfWeek.value !in targetDays) date = date.plusDays(1)
        return candidate(date, dueTime, zoneId).toInstant().toEpochMilli()
    }

    private fun localReminderTime(dueMillis: Long, zoneId: ZoneId): LocalTime =
        Instant.ofEpochMilli(dueMillis)
            .atZone(zoneId)
            .toLocalTime()
            .withSecond(0)
            .withNano(0)

    /**
     * ZonedDateTime.of() řeší DST gap posunem na nejbližší platný lokální čas
     * a při podzimním overlapu zvolí dřívější offset. To je deterministické a
     * další den se reminder vrátí na svou běžnou lokální hodinu.
     */
    private fun candidate(date: LocalDate, time: LocalTime, zoneId: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, time, zoneId)
}
