package cz.jenda.georeminder.data

import android.content.Context
import android.net.Uri
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.ReminderKind
import cz.jenda.georeminder.model.TimeRepeat
import cz.jenda.georeminder.model.DEFAULT_RADIUS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream

@Serializable
data class GeoReminderBackupData(
    val version: Int = 1,
    val reminders: List<Reminder>,
    val favorites: List<FavoritePlace>,
)

object BackupManager {
    private const val MAX_BACKUP_SIZE_BYTES = 5 * 1024 * 1024

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Exportuje data připomínek a oblíbených do daného URI souboru. */
    suspend fun exportBackup(context: Context, targetUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
        try {
            // Export nikdy nevynáší absolutní cesty ze sandboxu telefonu.
            // Samotné soubory příloh zatím formát zálohy neobsahuje.
            val reminders = ReminderStore.get(context).reloadAndGet()
                .map { it.copy(attachmentPath = null) }
            val favorites = FavoritesStore.get(context).reloadAndGet()
            val backupObj = GeoReminderBackupData(
                reminders = reminders,
                favorites = favorites,
            )
            val jsonText = json.encodeToString(backupObj)

            val stream = context.contentResolver.openOutputStream(targetUri) ?: return@withContext false
            stream.use {
                stream.write(jsonText.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    /** Importuje zálohu ze souboru a uloží do ReminderStore a FavoritesStore. */
    suspend fun importBackup(context: Context, sourceUri: Uri): Boolean =
        withContext(Dispatchers.IO) {
        try {
            val jsonText = readBackupText(context, sourceUri) ?: return@withContext false

            val backupObj = json.decodeFromString<GeoReminderBackupData>(jsonText)
            if (backupObj.version != 1) return@withContext false

            val store = ReminderStore.get(context)
            val favStore = FavoritesStore.get(context)

            val reminders = backupObj.reminders.mapNotNull(::sanitizeReminder)
            val favorites = backupObj.favorites.mapNotNull(::sanitizeFavorite)
            if (backupObj.reminders.isNotEmpty() && reminders.isEmpty()) return@withContext false
            if (backupObj.favorites.isNotEmpty() && favorites.isEmpty()) return@withContext false

            val originalReminders = store.reloadAndGet()
            val originalFavorites = favStore.reloadAndGet()

            // Jeden zápis na store namísto desítek fire-and-forget aktualizací.
            if (!store.upsertAllDurably(reminders)) return@withContext false
            if (!favStore.upsertAllDurably(favorites)) {
                // Best-effort rollback, aby import nezůstal napůl provedený.
                store.replaceAllDurably(originalReminders)
                favStore.replaceAllDurably(originalFavorites)
                return@withContext false
            }

            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Import zálohy selhal", e)
            false
        }
    }

    private fun readBackupText(context: Context, uri: Uri): String? {
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            var total = 0
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_BACKUP_SIZE_BYTES) return null
                out.write(buffer, 0, count)
            }
            out.toString(Charsets.UTF_8.name())
        }
    }

    private fun sanitizeReminder(reminder: Reminder): Reminder? {
        val cleanId = reminder.id.trim().take(200).takeIf { it.isNotEmpty() } ?: return null
        val cleanTitle = reminder.title.trim().take(200).takeIf { it.isNotEmpty() } ?: return null

        return when (reminder.kind) {
            ReminderKind.LOCATION -> {
                if (!reminder.latitude.isFinite() || reminder.latitude !in -90.0..90.0) return null
                if (!reminder.longitude.isFinite() || reminder.longitude !in -180.0..180.0) return null
                if (!reminder.radius.isFinite()) return null
                val cleanPlace = reminder.placeName.trim().take(200).takeIf { it.isNotEmpty() }
                    ?: return null
                reminder.copy(
                    id = cleanId,
                    title = cleanTitle,
                    placeName = cleanPlace,
                    radius = reminder.radius.coerceIn(50.0, 1_000.0),
                    dueDate = null,
                    timeRepeat = TimeRepeat.NEVER,
                    weekdays = null,
                    attachmentPath = null,
                )
            }
            ReminderKind.TIME -> {
                val dueDate = reminder.dueDate?.takeIf { it > 0L } ?: return null
                val cleanWeekdays = reminder.weekdays
                    ?.filter { it in 1..7 }
                    ?.distinct()
                    ?.sorted()
                    ?.ifEmpty { null }
                reminder.copy(
                    id = cleanId,
                    title = cleanTitle,
                    placeName = "",
                    latitude = 0.0,
                    longitude = 0.0,
                    radius = DEFAULT_RADIUS,
                    repeats = false,
                    dueDate = dueDate,
                    weekdays = if (reminder.timeRepeat == TimeRepeat.WEEKLY) cleanWeekdays else null,
                    attachmentPath = null,
                )
            }
        }
    }

    private fun sanitizeFavorite(favorite: FavoritePlace): FavoritePlace? {
        val cleanId = favorite.id.trim().take(200).takeIf { it.isNotEmpty() } ?: return null
        val cleanName = favorite.name.trim().take(200).takeIf { it.isNotEmpty() } ?: return null
        if (!favorite.latitude.isFinite() || favorite.latitude !in -90.0..90.0) return null
        if (!favorite.longitude.isFinite() || favorite.longitude !in -180.0..180.0) return null
        if (!favorite.radius.isFinite()) return null
        return favorite.copy(
            id = cleanId,
            name = cleanName,
            radius = favorite.radius.coerceIn(50.0, 1_000.0),
        )
    }
}
