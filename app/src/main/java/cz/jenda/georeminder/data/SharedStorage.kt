package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.io.File
import java.security.MessageDigest

/**
 * Společné úložiště pro appku a widget – prosté JSON soubory v interním
 * úložišti aplikace (Android nepotřebuje App Group, widget běží ve stejném
 * procesu/balíčku). Formát JSON je shodný s iOS verzí.
 */
object SharedStorage {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    const val PREFS = "georeminder"

    sealed class DecodeRemindersResult {
        data class Success(val reminders: List<Reminder>) : DecodeRemindersResult()
        data class Partial(
            val reminders: List<Reminder>,
            val skippedCount: Int,
        ) : DecodeRemindersResult()
        data class Corrupted(val reason: String) : DecodeRemindersResult()
    }

    /**
     * Čistá parsovací část bez Android runtime závislostí, aby šla spolehlivě
     * testovat jako obyčejný JVM unit test.
     */
    fun decodeReminders(text: String): DecodeRemindersResult {
        if (text.isBlank()) {
            return DecodeRemindersResult.Corrupted("empty_file")
        }

        try {
            return DecodeRemindersResult.Success(
                json.decodeFromString(ListSerializer(Reminder.serializer()), text)
            )
        } catch (_: Exception) {
            // Pokračujeme obnovou po jednotlivých záznamech.
        }

        val array = try {
            json.parseToJsonElement(text) as? JsonArray
                ?: return DecodeRemindersResult.Corrupted("root_is_not_array")
        } catch (_: Exception) {
            return DecodeRemindersResult.Corrupted("invalid_json")
        }

        val recovered = mutableListOf<Reminder>()
        var skipped = 0
        for (element in array) {
            try {
                recovered += json.decodeFromJsonElement(Reminder.serializer(), element)
            } catch (_: Exception) {
                skipped++
            }
        }

        return when {
            skipped == 0 -> DecodeRemindersResult.Success(recovered)
            recovered.isEmpty() -> DecodeRemindersResult.Corrupted("all_records_invalid")
            else -> DecodeRemindersResult.Partial(recovered, skipped)
        }
    }

    sealed class ReadResult {
        data class Ok(val text: String) : ReadResult()
        object Empty : ReadResult()
        object Error : ReadResult()
    }

    fun file(context: Context, filename: String): File =
        File(context.applicationContext.filesDir, filename)

    fun read(context: Context, filename: String): ReadResult {
        val f = file(context, filename)
        if (!f.exists()) return ReadResult.Empty
        val atomicFile = android.util.AtomicFile(f)
        return try {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            ReadResult.Ok(text)
        } catch (e: Exception) {
            Log.w("SharedStorage", "Čtení $filename selhalo", e)
            ReadResult.Error
        }
    }

    fun readText(context: Context, filename: String): String? =
        (read(context, filename) as? ReadResult.Ok)?.text

    @Synchronized
    fun writeText(context: Context, filename: String, content: String): Boolean {
        val atomicFile = android.util.AtomicFile(file(context, filename))
        var stream: java.io.FileOutputStream? = null
        return try {
            stream = atomicFile.startWrite()
            stream.write(content.toByteArray(Charsets.UTF_8))
            atomicFile.finishWrite(stream)
            true
        } catch (e: Exception) {
            if (stream != null) {
                atomicFile.failWrite(stream)
            }
            Log.w("SharedStorage", "Zápis $filename selhal – původní soubor zůstává zachovaný", e)
            false
        }
    }

    fun preserveCorruptCopy(context: Context, filename: String, content: String): File? {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(content.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
                .take(16)
            val baseName = filename.substringBeforeLast('.', filename)
            val backupName = "$baseName.corrupt-$digest.json"
            val target = file(context, backupName)
            if (target.exists()) return target
            if (writeText(context, backupName, content)) target else null
        } catch (e: Exception) {
            Log.w("SharedStorage", "Vytvoření záchranné kopie $filename selhalo", e)
            null
        }
    }
}
