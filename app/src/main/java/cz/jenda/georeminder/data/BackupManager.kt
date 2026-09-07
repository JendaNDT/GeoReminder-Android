package cz.jenda.georeminder.data

import android.content.Context
import android.net.Uri
import android.util.Log
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class GeoReminderBackupData(
    val version: Int = BackupManager.CURRENT_VERSION,
    val reminders: List<Reminder>,
    val favorites: List<FavoritePlace>,
)

object BackupManager {
    const val CURRENT_VERSION = 2
    const val LEGACY_JSON_VERSION = 1

    private const val BACKUP_JSON_ENTRY = "backup.json"
    private const val ATTACHMENTS_PREFIX = "attachments/"
    private const val MAX_BACKUP_JSON_BYTES = 5 * 1024 * 1024
    private const val MAX_ARCHIVE_ENTRIES = 5_000
    private const val MAX_RECORDS = 20_000
    private const val MAX_TOTAL_ATTACHMENT_BYTES = 250L * 1024L * 1024L

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Export v2: ZIP obsahující backup.json a fyzické přílohy. V JSONu nejsou
     * absolutní interní cesty telefonu, ale pouze relativní jména ZIP položek.
     */
    fun exportBackup(context: Context, targetUri: Uri): Boolean {
        return try {
            val attachmentEntries = mutableListOf<Pair<String, File>>()
            val remindersForBackup = ReminderStore.get(context).reminders.value.map { reminder ->
                val file = AttachmentHelper.managedAttachmentForBackup(context, reminder.attachmentPath)
                if (file == null) {
                    reminder.copy(attachmentPath = null)
                } else {
                    val extension = file.extension
                        .lowercase()
                        .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                        ?: "bin"
                    val entryName = "$ATTACHMENTS_PREFIX${UUID.randomUUID()}.$extension"
                    attachmentEntries += entryName to file
                    reminder.copy(attachmentPath = entryName)
                }
            }
            val favorites = FavoritesStore.get(context).favorites.value
            val backup = GeoReminderBackupData(
                version = CURRENT_VERSION,
                reminders = remindersForBackup,
                favorites = favorites,
            )
            val backupJson = json.encodeToString(backup)
            if (backupJson.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_JSON_BYTES) {
                Log.w("BackupManager", "backup.json je příliš velký")
                return false
            }

            val output = context.contentResolver.openOutputStream(targetUri) ?: return false
            ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(BACKUP_JSON_ENTRY))
                zip.write(backupJson.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                attachmentEntries.forEach { (entryName, file) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            true
        } catch (e: Exception) {
            Log.w("BackupManager", "Export ZIP zálohy selhal", e)
            false
        }
    }

    /**
     * Import automaticky rozezná nový ZIP v2 a starý JSON v1. Import je dávkový:
     * validace + deduplikace proběhne nejdřív v paměti, potom následuje jeden
     * zápis favorites a jeden zápis reminders + jediný scheduler resync.
     */
    fun importBackup(context: Context, sourceUri: Uri): Boolean {
        val createdAttachmentPaths = mutableListOf<String>()
        return try {
            val raw = context.contentResolver.openInputStream(sourceUri) ?: return false
            val buffered = BufferedInputStream(raw)
            buffered.mark(8)
            val signature = ByteArray(4)
            val signatureSize = buffered.read(signature)
            buffered.reset()

            val imported = if (signatureSize == 4 && isZipSignature(signature)) {
                readZipBackup(context, buffered, createdAttachmentPaths)
            } else {
                readLegacyJsonBackup(buffered)
            } ?: run {
                AttachmentHelper.deleteAttachments(context, createdAttachmentPaths)
                return false
            }

            applyImportedBackup(context, imported, createdAttachmentPaths)
        } catch (e: Exception) {
            AttachmentHelper.deleteAttachments(context, createdAttachmentPaths)
            Log.w("BackupManager", "Import zálohy selhal", e)
            false
        }
    }

    private data class ImportedBackup(
        val reminders: List<Reminder>,
        val favorites: List<FavoritePlace>,
    )

    private fun readLegacyJsonBackup(input: BufferedInputStream): ImportedBackup? {
        input.use { stream ->
            val bytes = readLimited(stream, MAX_BACKUP_JSON_BYTES) ?: return null
            val data = decodeAndValidateBackupJson(bytes.toString(Charsets.UTF_8)) ?: return null
            if (data.version != LEGACY_JSON_VERSION && data.version != CURRENT_VERSION) return null
            return sanitizeBackupData(
                data = data,
                attachmentPaths = emptyMap(),
                allowArchiveAttachmentPaths = false,
            )
        }
    }

    private fun readZipBackup(
        context: Context,
        input: BufferedInputStream,
        createdAttachmentPaths: MutableList<String>,
    ): ImportedBackup? {
        val attachmentPaths = linkedMapOf<String, String>()
        val seenEntries = mutableSetOf<String>()
        var backupJson: String? = null
        var entryCount = 0
        var totalAttachmentBytes = 0L

        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entryCount++
                if (entryCount > MAX_ARCHIVE_ENTRIES) return null

                val entryName = entry.name
                if (!isSafeZipEntryName(entryName) || !seenEntries.add(entryName)) {
                    return null
                }

                if (!entry.isDirectory) {
                    when {
                        entryName == BACKUP_JSON_ENTRY -> {
                            if (backupJson != null) return null
                            val bytes = readLimited(zip, MAX_BACKUP_JSON_BYTES) ?: return null
                            backupJson = bytes.toString(Charsets.UTF_8)
                        }

                        entryName.startsWith(ATTACHMENTS_PREFIX) -> {
                            val importedPath = AttachmentHelper.copyBackupEntryToInternal(
                                context,
                                entryName,
                                zip,
                            ) ?: return null
                            createdAttachmentPaths += importedPath
                            attachmentPaths[entryName] = importedPath
                            totalAttachmentBytes += File(importedPath).length()
                            if (totalAttachmentBytes > MAX_TOTAL_ATTACHMENT_BYTES) return null
                        }

                        else -> {
                            // Neznámé položky ignorovat kvůli budoucí rozšiřitelnosti formátu.
                        }
                    }
                }
                zip.closeEntry()
            }
        }

        val data = decodeAndValidateBackupJson(backupJson ?: return null) ?: return null
        if (data.version != CURRENT_VERSION) return null
        return sanitizeBackupData(
            data = data,
            attachmentPaths = attachmentPaths,
            allowArchiveAttachmentPaths = true,
        )
    }

    private fun decodeAndValidateBackupJson(text: String): GeoReminderBackupData? {
        return try {
            val data = json.decodeFromString<GeoReminderBackupData>(text)
            if (data.version !in LEGACY_JSON_VERSION..CURRENT_VERSION) return null
            if (data.reminders.size > MAX_RECORDS || data.favorites.size > MAX_RECORDS) return null
            data
        } catch (e: Exception) {
            Log.w("BackupManager", "backup.json nelze dekódovat", e)
            null
        }
    }

    private fun sanitizeBackupData(
        data: GeoReminderBackupData,
        attachmentPaths: Map<String, String>,
        allowArchiveAttachmentPaths: Boolean,
    ): ImportedBackup {
        val reminders = data.reminders.mapNotNull { reminder ->
            sanitizeReminder(reminder)?.let { sanitized ->
                val resolvedAttachment = if (
                    allowArchiveAttachmentPaths &&
                    !reminder.attachmentPath.isNullOrBlank() &&
                    isSafeZipEntryName(reminder.attachmentPath) &&
                    reminder.attachmentPath.startsWith(ATTACHMENTS_PREFIX)
                ) {
                    attachmentPaths[reminder.attachmentPath]
                } else {
                    null
                }
                sanitized.copy(attachmentPath = resolvedAttachment)
            }
        }

        val favorites = data.favorites.mapNotNull(::sanitizeFavorite)
        return ImportedBackup(
            reminders = dedupeById(reminders) { it.id },
            favorites = dedupeById(favorites) { it.id },
        )
    }

    internal fun sanitizeReminder(reminder: Reminder): Reminder? {
        if (reminder.id.isBlank()) return null
        if (!reminder.radius.isFinite()) return null
        if (reminder.kind == ReminderKind.LOCATION) {
            if (!reminder.latitude.isFinite() || reminder.latitude !in -90.0..90.0) return null
            if (!reminder.longitude.isFinite() || reminder.longitude !in -180.0..180.0) return null
        }
        if (reminder.kind == ReminderKind.TIME && reminder.dueDate == null) return null

        val weekdays = reminder.weekdays
            ?.filter { it in 1..7 }
            ?.distinct()
            ?.sorted()
            ?.takeIf { it.isNotEmpty() }

        return reminder.copy(
            radius = reminder.radius.coerceIn(50.0, 1000.0),
            weekdays = if (reminder.timeRepeat == TimeRepeat.WEEKLY) weekdays else reminder.weekdays,
            attachmentPath = null,
        )
    }

    internal fun sanitizeFavorite(place: FavoritePlace): FavoritePlace? {
        if (place.id.isBlank()) return null
        if (!place.latitude.isFinite() || place.latitude !in -90.0..90.0) return null
        if (!place.longitude.isFinite() || place.longitude !in -180.0..180.0) return null
        if (!place.radius.isFinite()) return null
        return place.copy(radius = place.radius.coerceIn(50.0, 1000.0))
    }

    /** Poslední výskyt stejného ID vyhrává, pořadí unikátních ID zůstává stabilní. */
    internal fun <T> dedupeById(items: List<T>, idOf: (T) -> String): List<T> {
        val map = linkedMapOf<String, T>()
        items.forEach { item -> map[idOf(item)] = item }
        return map.values.toList()
    }

    internal fun <T> mergeById(current: List<T>, incoming: List<T>, idOf: (T) -> String): List<T> {
        val map = linkedMapOf<String, T>()
        current.forEach { item -> map[idOf(item)] = item }
        incoming.forEach { item -> map[idOf(item)] = item }
        return map.values.toList()
    }

    private fun applyImportedBackup(
        context: Context,
        imported: ImportedBackup,
        createdAttachmentPaths: MutableList<String>,
    ): Boolean {
        val reminderStore = ReminderStore.get(context)
        val favoritesStore = FavoritesStore.get(context)
        val oldReminders = reminderStore.reminders.value
        val oldFavorites = favoritesStore.favorites.value

        val finalReminders = mergeById(oldReminders, imported.reminders) { it.id }
        val finalFavorites = mergeById(oldFavorites, imported.favorites) { it.id }

        // Favorites první: pokud selže, reminders ani scheduler zůstávají nedotčené.
        if (!favoritesStore.replaceAllFromImport(finalFavorites)) {
            AttachmentHelper.deleteAttachments(context, createdAttachmentPaths)
            return false
        }

        if (!reminderStore.replaceAllFromImport(finalReminders)) {
            // Best-effort rollback první části transakce.
            favoritesStore.replaceAllFromImport(oldFavorites)
            AttachmentHelper.deleteAttachments(context, createdAttachmentPaths)
            return false
        }

        val referencedNewPaths = finalReminders.mapNotNull { it.attachmentPath }.toSet()
        AttachmentHelper.deleteAttachments(
            context,
            createdAttachmentPaths.filterNot { it in referencedNewPaths },
        )

        // Přílohy nahrazených reminderů mažeme až po úspěšném zápisu obou datasetů.
        val finalPaths = finalReminders.mapNotNull { it.attachmentPath }.toSet()
        oldReminders.mapNotNull { it.attachmentPath }
            .filterNot { it in finalPaths }
            .forEach { AttachmentHelper.deleteAttachment(context, it) }

        return true
    }

    private fun isZipSignature(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 0x03.toByte() || bytes[2] == 0x05.toByte() || bytes[2] == 0x07.toByte()) &&
            (bytes[3] == 0x04.toByte() || bytes[3] == 0x06.toByte() || bytes[3] == 0x08.toByte())

    internal fun isSafeZipEntryName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        if (name.startsWith('/') || name.startsWith('\\')) return false
        if ('\\' in name) return false
        val segments = name.split('/')
        if (segments.any { it == ".." }) return false
        return true
    }

    private fun readLimited(input: java.io.InputStream, maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream(minOf(maxBytes, 64 * 1024))
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
