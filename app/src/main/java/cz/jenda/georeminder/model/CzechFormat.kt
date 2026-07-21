package cz.jenda.georeminder.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formátování datumů vždy v češtině – nezávisle na jazyku systému.
 * (V iOS verzi byla známá kosmetická vada: anglický systém psal „20. 7. 2026 at 18:30".
 * Tady formát vynucujeme, jak doporučuje DESIGN_SPEC §8.)
 */
object CzechFormat {
    private val locale = Locale("cs", "CZ")

    /** „20. 7. 2026 18:30" */
    fun dateTime(millis: Long): String =
        SimpleDateFormat("d. M. yyyy H:mm", locale).format(Date(millis))

    /** „18:30" */
    fun time(millis: Long): String =
        SimpleDateFormat("H:mm", locale).format(Date(millis))

    /** „pondělí 18:30" */
    fun weekdayTime(millis: Long): String =
        SimpleDateFormat("EEEE H:mm", locale).format(Date(millis))

    private val shortDays = arrayOf("po", "út", "st", "čt", "pá", "so", "ne")
    private val fullDays = arrayOf(
        "pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota", "neděle"
    )

    /** „pondělí 18:30" (jeden den) / „po, st, pá 18:30" (více vybraných dnů) */
    fun weeklyLabel(millis: Long, weekdays: List<Int>?): String {
        return when {
            weekdays.isNullOrEmpty() -> weekdayTime(millis)
            weekdays.size == 1 ->
                fullDays[(weekdays[0] - 1).coerceIn(0, 6)] + " " + time(millis)
            else ->
                weekdays.sorted().joinToString(", ") { shortDays[(it - 1).coerceIn(0, 6)] } +
                        " " + time(millis)
        }
    }

    /** „21. 7. 2026" – pro kapsli s datem ve formuláři */
    fun date(millis: Long): String =
        SimpleDateFormat("d. M. yyyy", locale).format(Date(millis))

    /** „850 m odsud" / „1,2 km odsud" */
    fun distance(meters: Float): String {
        return if (meters < 1000) {
            "${meters.toInt()} m odsud"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(locale, "%.1f km odsud", km)
        }
    }

    /** „850 m" / „1,2 km" – krátká varianta pro výsledky hledání */
    fun distanceShort(meters: Float): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(locale, "%.1f km", km)
        }
    }
}
