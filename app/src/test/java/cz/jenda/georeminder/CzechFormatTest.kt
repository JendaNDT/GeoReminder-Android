package cz.jenda.georeminder

import cz.jenda.georeminder.model.CzechFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.Locale

class CzechFormatTest {
    private lateinit var originalLocale: Locale

    @Before
    fun setUpLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("cs-CZ"))
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun testDistanceFormatting() {
        assertEquals("0 m odsud", CzechFormat.distance(0f))
        assertEquals("320 m odsud", CzechFormat.distance(320f))
        assertEquals("999 m odsud", CzechFormat.distance(999f))
        assertEquals("1,2 km odsud", CzechFormat.distance(1200f))
        assertEquals("5,0 km odsud", CzechFormat.distance(5000f))
    }

    @Test
    fun testDistanceShortFormatting() {
        assertEquals("320 m", CzechFormat.distanceShort(320f))
        assertEquals("1,5 km", CzechFormat.distanceShort(1500f))
    }

    @Test
    fun testWeeklyLabelFormatting() {
        val millis = 1700000000000L
        val formattedMultiple = CzechFormat.weeklyLabel(millis, listOf(1, 3, 5))
        assert(formattedMultiple.startsWith("po, st, pá"))

        val formattedSingle = CzechFormat.weeklyLabel(millis, listOf(1))
        assert(formattedSingle.startsWith("pondělí"))
    }

    @Test
    fun testEnglishFormattingUsesEnglishLabels() {
        Locale.setDefault(Locale.US)
        assertEquals("1.2 km away", CzechFormat.distance(1200f))
        val formatted = CzechFormat.weeklyLabel(1700000000000L, listOf(1, 3, 5))
        assertTrue(formatted.startsWith("Mon, Wed, Fri"))
    }
}
