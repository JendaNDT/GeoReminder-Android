package cz.jenda.georeminder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ReliabilityHistoryTest {
    @Test
    fun appendEventSuppressesImmediateDuplicate() {
        val first = ReliabilityEvent(
            type = ReliabilityEventType.SCHEDULED_EXACT,
            timestamp = 1_000L,
            reminderId = "A",
            detail = "5000",
        )
        val current = listOf(first)
        val duplicate = first.copy(id = "B", timestamp = 20_000L)

        val result = ReliabilityHistory.appendEvent(current, duplicate)

        assertSame(current, result)
    }

    @Test
    fun appendEventKeepsNewestAndRespectsLimit() {
        val current = (1L..5L).map {
            ReliabilityEvent(
                type = ReliabilityEventType.NOTIFICATION_SHOWN,
                timestamp = it,
                reminderId = it.toString(),
            )
        }
        val newest = ReliabilityEvent(
            type = ReliabilityEventType.TEST_TRIGGERED,
            timestamp = 10L,
        )

        val result = ReliabilityHistory.appendEvent(current, newest, maxEvents = 3)

        assertEquals(listOf(newest, current[0], current[1]), result)
    }
}
