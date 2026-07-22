package cz.jenda.georeminder

import cz.jenda.georeminder.model.CzechFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class CzechFormatTest {

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
