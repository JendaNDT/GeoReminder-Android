package cz.jenda.georeminder

import cz.jenda.georeminder.data.FeatureSettings
import cz.jenda.georeminder.data.LanguageController
import cz.jenda.georeminder.model.CzechFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CzechFormatTest {

    private lateinit var previousLanguage: String

    @Before
    fun setCzechLanguage() {
        previousLanguage = FeatureSettings.appLanguage.value
        FeatureSettings.appLanguage.value = LanguageController.LANG_CS
    }

    @After
    fun restoreLanguage() {
        FeatureSettings.appLanguage.value = previousLanguage
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
}
