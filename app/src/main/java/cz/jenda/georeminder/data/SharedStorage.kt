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

    /** Jeden název SharedPreferences pro celou appku (nastavení, značky…). */
    const val PREFS = "georeminder"

    /**
     * Výsledek dekódování reminders.json. Dřívější API vracelo při poškození
     * prostě prázdný/částečný seznam, takže volající nemohl rozlišit legitimní
     * prázdno od ztráty dat.
     */
    sealed class DecodeRemindersResult {
        data class Success(val reminders: List<Reminder>) : DecodeRemindersResult()
        data class Partial(
            val reminders: List<Reminder>,
            val skippedCount: Int,
        ) : DecodeRemindersResult()
        data class Corrupted(val reason: String) : DecodeRemindersResult()
    }

    /**
     * Odolné dekódování seznamu připomínek. Nejdřív zkusí celý list, a pokud
     * jeden záznam selže, zkusí jednotlivé položky. Výsledek ale vždy výslovně
     * řekne, zda šlo o plně validní, částečně obnovitelná nebo nečitelná data.
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
        } catch (e: Exception) {
            Log.w("SharedStorage", "Data připomínek nejsou platný JSON", e)
            return DecodeRemindersResult.Corrupted("invalid_json")
        }

        val recovered = mutableListOf<Reminder>()
        var skipped = 0
        for (element in array) {
            try {
                recovered += json.decodeFromJsonElement(Reminder.serializer(), element)
            } catch (e: Exception) {
                skipped++
                Log.w("SharedStorage", "Přeskakuji vadný záznam připomínky", e)
            }
        }

        return when {
            skipped == 0 -> DecodeRemindersResult.Success(recovered)
            recovered.isEmpty() -> DecodeRemindersResult.Corrupted("all_records_invalid")
            else -> DecodeRemindersResult.Partial(recovered, skipped)
        }
    }

    /**
     * Výsledek čtení. Záměrně rozlišuje „prázdno" (soubor ještě neexistuje –
     * legitimní stav při prvním spuštění) od skutečné chyby čtení.
     */
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

    /** Kompatibilní čtení pro widget (jen zobrazuje): null = prázdno i chyba. */
    fun readText(context: Context, filename: String): String? =
        (read(context, filename) as? ReadResult.Ok)?.text

    /**
     * Atomický zápis přes android.util.AtomicFile. Vrací false při skutečném
     * selhání, aby recovery/import nemohl pokračovat s falešným pocitem úspěchu.
     */
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

    /**
     * Před automatickou částečnou obnovou uloží nedotčený zdroj do samostatné
     * interní kopie. Název používá hash obsahu, takže opakovaný reload téhož
     * poškozeného souboru nevyrábí nekonečné množství kopií.
     */
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
