package cz.jenda.georeminder.model

import cz.jenda.georeminder.data.LanguageController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formátování datumů a vzdáleností podle skutečně aktivního jazyka aplikace. */
object CzechFormat {

    private fun getLocale(): Locale = LanguageController.effectiveLocale()
    private fun isEnglish(): Boolean =
        LanguageController.effectiveLanguageCode() == LanguageController.LANG_EN

    private val csShortDays = arrayOf("po", "út", "st", "čt", "pá", "so", "ne")
    private val csFullDays = arrayOf("pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota", "neděle")

    private val enShortDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val enFullDays = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    /** „20. 7. 2026 18:30" */
    fun dateTime(millis: Long): String {
        val fmt = SimpleDateFormat("d. M. yyyy H:mm", getLocale())
        return fmt.format(Date(millis))
    }

    /** „18:30" */
    fun time(millis: Long): String {
        val fmt = SimpleDateFormat("H:mm", getLocale())
        return fmt.format(Date(millis))
    }

    /** „pondělí 18:30" / „Monday 18:30" */
    fun weekdayTime(millis: Long): String {
        val fmt = SimpleDateFormat("EEEE H:mm", getLocale())
        return fmt.format(Date(millis))
    }

    /** „pondělí 18:30" (jeden den) / „po, st, pá 18:30" (více vybraných dnů) */
    fun weeklyLabel(millis: Long, weekdays: List<Int>?): String {
        val fullDays = if (isEnglish()) enFullDays else csFullDays
        val shortDays = if (isEnglish()) enShortDays else csShortDays

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
    fun date(millis: Long): String {
        val fmt = SimpleDateFormat("d. M. yyyy", getLocale())
        return fmt.format(Date(millis))
    }

    /** „850 m odsud" / „850 m away" */
    fun distance(meters: Float): String {
        val suffix = if (isEnglish()) "away" else "odsud"
        return if (meters < 1000) {
            "${meters.toInt()} m $suffix"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(getLocale(), "%.1f km %s", km, suffix)
        }
    }

    /** „850 m" / „1.2 km" – krátká varianta pro výsledky hledání */
    fun distanceShort(meters: Float): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(getLocale(), "%.1f km", km)
        }
    }
}
