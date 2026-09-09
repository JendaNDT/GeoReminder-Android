package cz.jenda.georeminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticStoreTest {

    @Test
    fun `ring buffer keeps only newest fifty events`() {
        val events = (1L..75L).map {
            DiagnosticEvent(it, DiagnosticEventType.ALARM_SCHEDULED, "event-$it")
        }

        val trimmed = DiagnosticStore.trimEvents(events)

        assertEquals(50, trimmed.size)
        assertEquals(26L, trimmed.first().timestamp)
        assertEquals(75L, trimmed.last().timestamp)
    }

    @Test
    fun `event codec round trips safe technical detail`() {
        val event = DiagnosticEvent(
            timestamp = 123456L,
            type = DiagnosticEventType.GEOFENCE_REGISTER_FAIL,
            detail = "FAILED_SERVICE code=1000",
        )

        assertEquals(event, DiagnosticStore.decodeEvent(DiagnosticStore.encodeEvent(event)))
    }

    @Test
    fun `detail strips separators and line breaks`() {
        val sanitized = DiagnosticStore.sanitizeDetail("FAILED|42\nprivate\rvalue")

        assertEquals("FAILED/42 private value", sanitized)
        assertFalse(sanitized!!.contains('|'))
        assertFalse(sanitized.contains('\n'))
    }

    @Test
    fun `blank detail becomes null`() {
        assertNull(DiagnosticStore.sanitizeDetail("   \n  "))
    }

    @Test
    fun `detail is bounded`() {
        val sanitized = DiagnosticStore.sanitizeDetail("x".repeat(500))
        assertTrue(sanitized!!.length <= 120)
    }
}
