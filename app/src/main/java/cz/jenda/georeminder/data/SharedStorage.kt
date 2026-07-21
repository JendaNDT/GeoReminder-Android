package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.Reminder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import java.io.File

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
     * Odolné dekódování seznamu připomínek: zkusí celý list a při chybě přejde
     * na čtení po jednom záznamu (vadný přeskočí). Sdílí appka i widget.
     */
    fun decodeReminders(text: String): List<Reminder> {
        if (text.isBlank()) return emptyList()
        return try {
            json.decodeFromString(ListSerializer(Reminder.serializer()), text)
        } catch (_: Exception) {
            val out = mutableListOf<Reminder>()
            try {
                for (el in json.parseToJsonElement(text).jsonArray) {
                    try {
                        out.add(json.decodeFromJsonElement(Reminder.serializer(), el))
                    } catch (e: Exception) {
                        Log.w("SharedStorage", "Přeskakuji vadný záznam připomínky", e)
                    }
                }
            } catch (e: Exception) {
                Log.w("SharedStorage", "Data nejsou platné pole – vracím, co se povedlo", e)
            }
            out
        }
    }

    /**
     * Výsledek čtení. Záměrně rozlišuje „prázdno" (soubor ještě neexistuje –
     * legitimní stav při prvním spuštění) od skutečné chyby čtení. Bez tohoto
     * rozlišení by se přechodná chyba tvářila jako prázdný seznam a další
     * uložení by přepsalo platná data.
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
        return try {
            if (f.exists()) ReadResult.Ok(f.readText()) else ReadResult.Empty
        } catch (e: Exception) {
            Log.w("SharedStorage", "Čtení $filename selhalo", e)
            ReadResult.Error
        }
    }

    /** Kompatibilní čtení pro widget (jen zobrazuje): null = prázdno i chyba. */
    fun readText(context: Context, filename: String): String? =
        (read(context, filename) as? ReadResult.Ok)?.text

    /**
     * Atomický zápis: nejdřív do dočasného souboru, z existujícího souboru se
     * udělá záloha `.bak`, pak přejmenování. Zápisy jsou serializované
     * (@Synchronized), aby se souběžné zápisy z UI a z receiveru nepraly.
     */
    @Synchronized
    fun writeText(context: Context, filename: String, content: String) {
        try {
            val f = file(context, filename)
            val tmp = File(f.parentFile, "$filename.tmp")
            tmp.writeText(content)
            if (f.exists()) {
                try {
                    f.copyTo(File(f.parentFile, "$filename.bak"), overwrite = true)
                } catch (_: Exception) {
                    // Záloha je jen pojistka – její selhání nesmí zablokovat zápis.
                }
            }
            if (!tmp.renameTo(f)) {
                f.writeText(content)
            }
        } catch (e: Exception) {
            Log.w("SharedStorage", "Zápis $filename selhal – změna zůstala jen v paměti", e)
        }
    }
}
