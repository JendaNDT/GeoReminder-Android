package cz.jenda.georeminder.data

import android.content.Context
import android.net.Uri
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class GeoReminderBackupData(
    val version: Int = 1,
    val reminders: List<Reminder>,
    val favorites: List<FavoritePlace>,
)

object BackupManager {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Exportuje data připomínek a oblíbených do daného URI souboru.
     * JSON záloha fyzické přílohy neobsahuje, proto do ní nepatří ani absolutní
     * cesta z interního úložiště konkrétního telefonu.
     */
    fun exportBackup(context: Context, targetUri: Uri): Boolean {
        return try {
            val reminders = ReminderStore.get(context).reminders.value
                .map { it.copy(attachmentPath = null) }
            val favorites = FavoritesStore.get(context).favorites.value
            val backupObj = GeoReminderBackupData(
                reminders = reminders,
                favorites = favorites,
            )
            val jsonText = json.encodeToString(backupObj)

            val stream = context.contentResolver.openOutputStream(targetUri) ?: return false
            stream.use {
                it.write(jsonText.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Export zálohy selhal", e)
            false
        }
    }

    /** Importuje zálohu ze souboru a uloží do ReminderStore a FavoritesStore. */
    fun importBackup(context: Context, sourceUri: Uri): Boolean {
        return try {
            val jsonText = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: return false

            val backupObj = json.decodeFromString<GeoReminderBackupData>(jsonText)
            val store = ReminderStore.get(context)
            val favStore = FavoritesStore.get(context)

            val currentReminders = store.reminders.value.associateBy { it.id }
            backupObj.reminders.forEach { r ->
                val latValid = !r.latitude.isNaN() && r.latitude in -90.0..90.0
                val lonValid = !r.longitude.isNaN() && r.longitude in -180.0..180.0
                if (!latValid || !lonValid) return@forEach

                // JSON záloha neobsahuje soubor přílohy. Cizí absolutní cestu proto
                // nikdy nepřebíráme do aplikace; jinak by mohla ukazovat mimo
                // spravovaný attachments adresář nebo na neexistující soubor.
                val sanitized = r.copy(
                    radius = r.radius.coerceIn(50.0, 1000.0),
                    attachmentPath = null,
                )
                if (currentReminders.containsKey(sanitized.id)) {
                    store.update(sanitized)
                } else {
                    store.add(sanitized)
                }
            }
            val currentFavs = favStore.favorites.value.associateBy { it.id }
            backupObj.favorites.forEach { f ->
                val latValid = !f.latitude.isNaN() && f.latitude in -90.0..90.0
                val lonValid = !f.longitude.isNaN() && f.longitude in -180.0..180.0
                if (!latValid || !lonValid) return@forEach
                val sanitized = f.copy(radius = f.radius.coerceIn(50.0, 1000.0))
                if (currentFavs.containsKey(sanitized.id)) {
                    favStore.update(sanitized)
                } else {
                    favStore.add(sanitized)
                }
            }

            true
        } catch (e: Exception) {
            android.util.Log.w("BackupManager", "Import zálohy selhal", e)
            false
        }
    }
}
