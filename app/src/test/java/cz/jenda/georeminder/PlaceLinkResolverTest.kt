package cz.jenda.georeminder

import cz.jenda.georeminder.data.PlaceLinkResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlaceLinkResolverTest {

    @Test
    fun testGeoUriParsingWithLabel() = runBlocking {
        val resolved = PlaceLinkResolver.resolve("geo:0,0?q=50.08,14.43(Albert)")
        assertNotNull(resolved)
        assertEquals("Albert", resolved!!.first)
        assertEquals(50.08, resolved.second.latitude, 0.001)
        assertEquals(14.43, resolved.second.longitude, 0.001)
    }

    @Test
    fun testGeoUriParsingPlainCoordinates() = runBlocking {
        val resolved = PlaceLinkResolver.resolve("geo:50.08,14.43")
        assertNotNull(resolved)
        assertEquals("", resolved!!.first)
        assertEquals(50.08, resolved.second.latitude, 0.001)
        assertEquals(14.43, resolved.second.longitude, 0.001)
    }

    @Test
    fun testInvalidTextReturnsNull() = runBlocking {
        assertNull(PlaceLinkResolver.resolve("Toto není žádný platný odkaz ani geo URI."))
    }

    @Test
    fun testOutOfRangeGeoCoordinatesReturnNull() = runBlocking {
        assertNull(PlaceLinkResolver.resolve("geo:95.0,14.4"))
        assertNull(PlaceLinkResolver.resolve("geo:50.0,181.0"))
        assertNull(PlaceLinkResolver.resolve("geo:0,0?q=-91.0,10.0(Mimo)"))
    }
}
