package cz.jenda.georeminder

import cz.jenda.georeminder.model.CzechFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class CzechFormatTest {

    private val czech = Locale.forLanguageTag("cs-CZ")
    private val english = Locale.US

    @Test
    fun testDistanceFormatting() {
        assertEquals("0 m odsud", CzechFormat.distanceForLocale(0f, czech))
        assertEquals("320 m odsud", CzechFormat.distanceForLocale(320f, czech))
        assertEquals("999 m odsud", CzechFormat.distanceForLocale(999f, czech))
        assertEquals("1,2 km odsud", CzechFormat.distanceForLocale(1200f, czech))
        assertEquals("5,0 km odsud", CzechFormat.distanceForLocale(5000f, czech))
    }

    @Test
    fun testDistanceShortFormatting() {
        assertEquals("320 m", CzechFormat.distanceShortForLocale(320f, czech))
        assertEquals("1,5 km", CzechFormat.distanceShortForLocale(1500f, czech))
        assertEquals("1.5 km", CzechFormat.distanceShortForLocale(1500f, english))
    }

    @Test
    fun testWeeklyLabelFormatting() {
        val millis = 1700000000000L
        val formattedMultiple = CzechFormat.weeklyLabelForLocale(millis, listOf(1, 3, 5), czech)
        assert(formattedMultiple.startsWith("po, st, pá"))

        val formattedSingle = CzechFormat.weeklyLabelForLocale(millis, listOf(1), czech)
        assert(formattedSingle.startsWith("pondělí"))

        val englishMultiple = CzechFormat.weeklyLabelForLocale(millis, listOf(1, 3, 5), english)
        assert(englishMultiple.startsWith("Mon, Wed, Fri"))
    }
}
