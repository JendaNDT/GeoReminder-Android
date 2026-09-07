package cz.jenda.georeminder

import cz.jenda.georeminder.notify.nextNotificationMorningMillis
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class NotificationActionTimeTest {

    @Test
    fun `pred osmou vrati dnesni osmou`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 7, 7, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = Calendar.getInstance().apply {
            timeInMillis = nextNotificationMorningMillis(now.timeInMillis)
        }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, result.get(Calendar.MONTH))
        assertEquals(7, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }

    @Test
    fun `po osme vrati zitrejsi osmou`() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 7, 8, 1, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val result = Calendar.getInstance().apply {
            timeInMillis = nextNotificationMorningMillis(now.timeInMillis)
        }

        assertEquals(2026, result.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, result.get(Calendar.MONTH))
        assertEquals(8, result.get(Calendar.DAY_OF_MONTH))
        assertEquals(8, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, result.get(Calendar.MINUTE))
    }
}
