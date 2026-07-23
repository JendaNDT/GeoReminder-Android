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
        val geoUri = "geo:0,0?q=50.08,14.43(Albert)"
        val resolved = PlaceLinkResolver.resolve(geoUri)

        assertNotNull(resolved)
        assertEquals("Albert", resolved!!.first)
        assertEquals(50.08, resolved.second.latitude, 0.001)
        assertEquals(14.43, resolved.second.longitude, 0.001)
    }

    @Test
    fun testGeoUriParsingPlainCoordinates() = runBlocking {
        val geoUri = "geo:50.08,14.43"
        val resolved = PlaceLinkResolver.resolve(geoUri)

        assertNotNull(resolved)
        assertEquals("", resolved!!.first)
        assertEquals(50.08, resolved.second.latitude, 0.001)
        assertEquals(14.43, resolved.second.longitude, 0.001)
    }

    @Test
    fun testInvalidTextReturnsNull() = runBlocking {
        val invalidText = "Toto není žádný platný odkaz ani geo URI."
        val resolved = PlaceLinkResolver.resolve(invalidText)

        assertNull(resolved)
    }

    @Test
    fun externalUrlIsRejectedWithoutFollowingRedirects() = runBlocking {
        assertNull(PlaceLinkResolver.resolve("https://example.com/maps?q=50.08,14.43"))
    }

    @Test
    fun outOfRangeGeoCoordinatesAreRejected() = runBlocking {
        assertNull(PlaceLinkResolver.resolve("geo:999,999"))
    }
}
