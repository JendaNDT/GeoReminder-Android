package cz.jenda.georeminder.model

import cz.jenda.georeminder.data.LanguageController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formátování datumů a vzdáleností podle skutečně aktivního jazyka aplikace. */
object CzechFormat {

    private val csShortDays = arrayOf("po", "út", "st", "čt", "pá", "so", "ne")
    private val csFullDays = arrayOf("pondělí", "úterý", "středa", "čtvrtek", "pátek", "sobota", "neděle")
    private val enShortDays = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    private val enFullDays = arrayOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun dateTime(millis: Long): String = dateTimeForLocale(millis, LanguageController.effectiveLocale())
    fun time(millis: Long): String = timeForLocale(millis, LanguageController.effectiveLocale())
    fun weekdayTime(millis: Long): String = weekdayTimeForLocale(millis, LanguageController.effectiveLocale())
    fun weeklyLabel(millis: Long, weekdays: List<Int>?): String =
        weeklyLabelForLocale(millis, weekdays, LanguageController.effectiveLocale())
    fun date(millis: Long): String = dateForLocale(millis, LanguageController.effectiveLocale())
    fun distance(meters: Float): String = distanceForLocale(meters, LanguageController.effectiveLocale())
    fun distanceShort(meters: Float): String = distanceShortForLocale(meters, LanguageController.effectiveLocale())

    internal fun dateTimeForLocale(millis: Long, locale: Locale): String =
        SimpleDateFormat("d. M. yyyy H:mm", locale).format(Date(millis))

    internal fun timeForLocale(millis: Long, locale: Locale): String =
        SimpleDateFormat("H:mm", locale).format(Date(millis))

    internal fun weekdayTimeForLocale(millis: Long, locale: Locale): String =
        SimpleDateFormat("EEEE H:mm", locale).format(Date(millis))

    internal fun weeklyLabelForLocale(
        millis: Long,
        weekdays: List<Int>?,
        locale: Locale,
    ): String {
        val english = locale.language.equals("en", ignoreCase = true)
        val fullDays = if (english) enFullDays else csFullDays
        val shortDays = if (english) enShortDays else csShortDays
        return when {
            weekdays.isNullOrEmpty() -> weekdayTimeForLocale(millis, locale)
            weekdays.size == 1 ->
                fullDays[(weekdays[0] - 1).coerceIn(0, 6)] + " " + timeForLocale(millis, locale)
            else ->
                weekdays.sorted().joinToString(", ") { shortDays[(it - 1).coerceIn(0, 6)] } +
                    " " + timeForLocale(millis, locale)
        }
    }

    internal fun dateForLocale(millis: Long, locale: Locale): String =
        SimpleDateFormat("d. M. yyyy", locale).format(Date(millis))

    internal fun distanceForLocale(meters: Float, locale: Locale): String {
        val suffix = if (locale.language.equals("en", ignoreCase = true)) "away" else "odsud"
        return if (meters < 1000) {
            "${meters.toInt()} m $suffix"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(locale, "%.1f km %s", km, suffix)
        }
    }

    internal fun distanceShortForLocale(meters: Float, locale: Locale): String {
        return if (meters < 1000) {
            "${meters.toInt()} m"
        } else {
            val km = Math.round(meters / 100.0) / 10.0
            String.format(locale, "%.1f km", km)
        }
    }
}
