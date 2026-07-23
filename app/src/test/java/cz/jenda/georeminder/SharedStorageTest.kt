package cz.jenda.georeminder

import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.model.Reminder
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedStorageTest {

    @Test
    fun validEmptyArrayIsNotAnError() {
        val result = SharedStorage.decodeRemindersResult("[]")
        assertTrue(result is SharedStorage.DecodeResult.Ok)
        result as SharedStorage.DecodeResult.Ok
        assertTrue(result.value.isEmpty())
        assertFalse(result.hadInvalidEntries)
    }

    @Test
    fun malformedRootIsAnErrorNotAnEmptyDatabase() {
        val result = SharedStorage.decodeRemindersResult("{not-json")
        assertTrue(result is SharedStorage.DecodeResult.Error)
    }

    @Test
    fun validRecordsAreRecoveredButCorruptionIsFlagged() {
        val valid = Reminder(title = "Keep me")
        val validJson = SharedStorage.json.encodeToString(Reminder.serializer(), valid)
        val text = "[$validJson,{\"id\":42}]"

        val result = SharedStorage.decodeRemindersResult(text)
        assertTrue(result is SharedStorage.DecodeResult.Ok)
        result as SharedStorage.DecodeResult.Ok
        assertEquals(listOf(valid), result.value)
        assertTrue(result.hadInvalidEntries)
    }

    @Test
    fun roundTripPreservesEveryReminder() {
        val input = listOf(Reminder(title = "One"), Reminder(title = "Two"))
        val text = SharedStorage.json.encodeToString(
            ListSerializer(Reminder.serializer()),
            input,
        )
        val result = SharedStorage.decodeRemindersResult(text) as SharedStorage.DecodeResult.Ok
        assertEquals(input, result.value)
        assertFalse(result.hadInvalidEntries)
    }
}
