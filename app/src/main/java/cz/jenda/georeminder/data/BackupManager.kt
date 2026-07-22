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

    /** Exportuje data připomínek a oblíbených do daného URI souboru. */
    fun exportBackup(context: Context, targetUri: Uri): Boolean {
        return try {
            val reminders = ReminderStore.get(context).reminders.value
            val favorites = FavoritesStore.get(context).favorites.value
            val backupObj = GeoReminderBackupData(
                reminders = reminders,
                favorites = favorites,
            )
            val jsonText = json.encodeToString(backupObj)

            context.contentResolver.openOutputStream(targetUri)?.use { stream ->
                stream.write(jsonText.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (_: Exception) {
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

            backupObj.reminders.forEach { r ->
                val exists = store.reminders.value.any { it.id == r.id }
                if (exists) {
                    store.update(r)
                } else {
                    store.add(r)
                }
            }
            backupObj.favorites.forEach { f ->
                val exists = favStore.favorites.value.any { it.id == f.id }
                if (exists) {
                    favStore.update(f)
                } else {
                    favStore.add(f)
                }
            }

            true
        } catch (_: Exception) {
            false
        }
    }
}
