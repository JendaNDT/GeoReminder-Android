package cz.jenda.georeminder

import cz.jenda.georeminder.model.AlertStyle
import cz.jenda.georeminder.model.AppleDateSerializer
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.TriggerType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReminderTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun testAppleDateSerializer() {
        val nowMillis = 1700000000000L
        val encoded = json.encodeToString(AppleDateSerializer, nowMillis)
        val decoded = json.decodeFromString(AppleDateSerializer, encoded)
        // Allow tiny floating point millisecond rounding
        assertEquals(nowMillis / 1000, decoded / 1000)
    }

    @Test
    fun testReminderSerializationRoundtrip() {
        val reminder = Reminder(
            title = "Koupit mléko",
            kind = ReminderKind.LOCATION,
            placeName = "Albert",
            latitude = 50.08,
            longitude = 14.43,
            radius = 150.0,
            trigger = TriggerType.ARRIVE,
            repeats = true,
            alertStyle = AlertStyle.URGENT,
            nagging = true
        )

        val jsonString = json.encodeToString(reminder)
        assert(jsonString.contains("Koupit mléko"))
        assert(jsonString.contains("Albert"))

        val decoded = json.decodeFromString<Reminder>(jsonString)
        assertEquals(reminder.id, decoded.id)
        assertEquals(reminder.title, decoded.title)
        assertEquals(reminder.kind, decoded.kind)
        assertEquals(reminder.alertStyle, decoded.alertStyle)
        assertEquals(reminder.nagging, decoded.nagging)
    }

    @Test
    fun testiOSCompatibilityJsonParsing() {
        // Sample JSON string produced by iOS app
        val iosJson = """
            {
                "id": "11111111-2222-3333-4444-555555555555",
                "title": "Zavolat známému",
                "kind": "time",
                "placeName": "",
                "latitude": 0.0,
                "longitude": 0.0,
                "radius": 150.0,
                "trigger": "arrive",
                "repeats": false,
                "dueDate": 725888400.0,
                "timeRepeat": "never",
                "isDone": false,
                "createdAt": 725888000.0
            }
        """.trimIndent()

        val decoded = json.decodeFromString<Reminder>(iosJson)
        assertEquals("11111111-2222-3333-4444-555555555555", decoded.id)
        assertEquals("Zavolat známému", decoded.title)
        assertEquals(ReminderKind.TIME, decoded.kind)
        assertNotNull(decoded.dueDate)
        assertEquals(AlertStyle.DEFAULT, decoded.alertStyle) // default fallback
    }

    @Test
    fun testSubtitleGeneration() {
        val locReminder = Reminder(
            title = "Nákup",
            kind = ReminderKind.LOCATION,
            placeName = "Globus",
            trigger = TriggerType.LEAVE,
            repeats = true
        )
        assertEquals("Globus • Když odjedu • opakuje se", locReminder.subtitle)
    }
}
