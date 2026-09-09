package cz.jenda.georeminder

import cz.jenda.georeminder.data.AttachmentHelper
import cz.jenda.georeminder.data.SharedStorage
import cz.jenda.georeminder.model.Reminder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DataRecoveryTest {

    @Test
    fun validEmptyArrayIsLegitimateEmptyData() {
        val result = SharedStorage.decodeReminders("[]")
        assertTrue(result is SharedStorage.DecodeRemindersResult.Success)
        assertEquals(
            0,
            (result as SharedStorage.DecodeRemindersResult.Success).reminders.size,
        )
    }

    @Test
    fun existingBlankFileIsCorruptedNotLegitimateEmpty() {
        val result = SharedStorage.decodeReminders("   \n")
        assertTrue(result is SharedStorage.DecodeRemindersResult.Corrupted)
        assertEquals(
            "empty_file",
            (result as SharedStorage.DecodeRemindersResult.Corrupted).reason,
        )
    }

    @Test
    fun completelyBrokenJsonIsCorrupted() {
        val result = SharedStorage.decodeReminders("{this is not json")
        assertTrue(result is SharedStorage.DecodeRemindersResult.Corrupted)
    }

    @Test
    fun oneBrokenRecordAmongValidRecordsReturnsPartial() {
        val valid = Reminder(id = "GOOD", title = "Platná připomínka")
        val validJson = SharedStorage.json.encodeToString(Reminder.serializer(), valid)
        val result = SharedStorage.decodeReminders("[$validJson,{\"id\":123}]")

        assertTrue(result is SharedStorage.DecodeRemindersResult.Partial)
        val partial = result as SharedStorage.DecodeRemindersResult.Partial
        assertEquals(1, partial.reminders.size)
        assertEquals("GOOD", partial.reminders.single().id)
        assertEquals(1, partial.skippedCount)
    }

    @Test
    fun arrayWithOnlyBrokenRecordsIsCorrupted() {
        val result = SharedStorage.decodeReminders("[{\"id\":123}, true]")
        assertTrue(result is SharedStorage.DecodeRemindersResult.Corrupted)
        assertEquals(
            "all_records_invalid",
            (result as SharedStorage.DecodeRemindersResult.Corrupted).reason,
        )
    }

    @Test
    fun attachmentSizePolicyRejectsZeroAndOverLimit() {
        assertTrue(!AttachmentHelper.isAllowedAttachmentSize(0))
        assertTrue(AttachmentHelper.isAllowedAttachmentSize(1))
        assertTrue(AttachmentHelper.isAllowedAttachmentSize(AttachmentHelper.MAX_ATTACHMENT_SIZE_BYTES))
        assertTrue(!AttachmentHelper.isAllowedAttachmentSize(AttachmentHelper.MAX_ATTACHMENT_SIZE_BYTES + 1))
    }
}
