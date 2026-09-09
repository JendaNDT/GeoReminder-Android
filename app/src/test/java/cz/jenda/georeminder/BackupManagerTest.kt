package cz.jenda.georeminder

import cz.jenda.georeminder.data.BackupManager
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupManagerTest {

    @Test
    fun zipEntryPolicyRejectsTraversalAndAbsolutePaths() {
        assertTrue(BackupManager.isSafeZipEntryName("backup.json"))
        assertTrue(BackupManager.isSafeZipEntryName("attachments/file.pdf"))
        assertTrue(!BackupManager.isSafeZipEntryName("../secret"))
        assertTrue(!BackupManager.isSafeZipEntryName("attachments/../secret"))
        assertTrue(!BackupManager.isSafeZipEntryName("/absolute/path"))
        assertTrue(!BackupManager.isSafeZipEntryName("attachments\\evil.pdf"))
    }

    @Test
    fun importedReminderAlwaysDropsExternalAttachmentPath() {
        val sanitized = BackupManager.sanitizeReminder(
            Reminder(
                id = "R1",
                title = "Test",
                attachmentPath = "/data/user/0/cz.jenda.georeminder/files/reminders.json",
            )
        )
        assertEquals(null, sanitized?.attachmentPath)
    }

    @Test
    fun invalidLocationReminderIsRejected() {
        assertNull(
            BackupManager.sanitizeReminder(
                Reminder(
                    id = "R1",
                    kind = ReminderKind.LOCATION,
                    latitude = 91.0,
                    longitude = 14.0,
                )
            )
        )
        assertNull(
            BackupManager.sanitizeReminder(
                Reminder(
                    id = "R2",
                    kind = ReminderKind.LOCATION,
                    latitude = Double.NaN,
                    longitude = 14.0,
                )
            )
        )
    }

    @Test
    fun timeReminderWithoutDueDateIsRejected() {
        assertNull(
            BackupManager.sanitizeReminder(
                Reminder(
                    id = "T1",
                    kind = ReminderKind.TIME,
                    dueDate = null,
                )
            )
        )
    }

    @Test
    fun radiusAndWeekdaysAreNormalized() {
        val sanitized = BackupManager.sanitizeReminder(
            Reminder(
                id = "T2",
                kind = ReminderKind.TIME,
                dueDate = 1_800_000_000_000L,
                timeRepeat = TimeRepeat.WEEKLY,
                weekdays = listOf(7, 3, 3, 0, 9),
                radius = 9_999.0,
            )
        )
        assertEquals(1000.0, sanitized?.radius ?: 0.0, 0.0)
        assertEquals(listOf(3, 7), sanitized?.weekdays)
    }

    @Test
    fun invalidFavoriteIsRejectedAndRadiusIsClamped() {
        assertNull(
            BackupManager.sanitizeFavorite(
                FavoritePlace(id = "F1", latitude = 0.0, longitude = 181.0)
            )
        )
        val valid = BackupManager.sanitizeFavorite(
            FavoritePlace(id = "F2", latitude = 50.0, longitude = 14.0, radius = 5.0)
        )
        assertEquals(50.0, valid?.radius ?: 0.0, 0.0)
    }

    @Test
    fun duplicateIdsUseLastImportedValue() {
        val first = Reminder(id = "R1", title = "Starý")
        val second = Reminder(id = "R1", title = "Nový")
        val unique = BackupManager.dedupeById(listOf(first, second)) { it.id }
        assertEquals(1, unique.size)
        assertEquals("Nový", unique.single().title)
    }

    @Test
    fun incomingSnapshotOverridesExistingIdWithoutDuplicatingIt() {
        val current = listOf(
            Reminder(id = "A", title = "A-old"),
            Reminder(id = "B", title = "B"),
        )
        val incoming = listOf(
            Reminder(id = "A", title = "A-new"),
            Reminder(id = "C", title = "C"),
        )
        val merged = BackupManager.mergeById(current, incoming) { it.id }
        assertEquals(3, merged.size)
        assertEquals("A-new", merged.first { it.id == "A" }.title)
    }
}
