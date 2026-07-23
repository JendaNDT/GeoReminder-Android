package cz.jenda.georeminder.data

import android.content.Context
import android.util.Log
import cz.jenda.georeminder.model.FavoritePlace
import cz.jenda.georeminder.model.Reminder
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import java.io.File

/**
 * Společné úložiště pro appku a widget – prosté JSON soubory v interním
 * úložišti aplikace (Android nepotřebuje App Group, widget běží ve stejném
 * procesu/balíčku). Formát JSON je shodný s iOS verzí.
 */
object SharedStorage {
    private fun warn(message: String, error: Throwable? = null) {
        // android.util.Log nemá implementaci v čistých JVM testech.
        runCatching {
            if (error == null) Log.w("SharedStorage", message)
            else Log.w("SharedStorage", message, error)
        }
    }

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Jeden název SharedPreferences pro celou appku (nastavení, značky…). */
    const val PREFS = "georeminder"

    sealed class DecodeResult<out T> {
        data class Ok<T>(
            val value: List<T>,
            /** Soubor byl pole, ale alespoň jeden záznam byl poškozený. */
            val hadInvalidEntries: Boolean = false
        ) : DecodeResult<T>()

        object Error : DecodeResult<Nothing>()
    }

    /**
     * Odolné dekódování, které nezaměňuje poškozený soubor za legitimní
     * prázdný seznam. Při poškození jednotlivé položky vrátí platné
     * záznamy, ale označí výsledek, aby store soubor automaticky nepřepsal.
     */
    private fun <T> decodeList(text: String, serializer: KSerializer<T>): DecodeResult<T> {
        if (text.isBlank()) return DecodeResult.Error

        return try {
            DecodeResult.Ok(json.decodeFromString(ListSerializer(serializer), text))
        } catch (wholeListError: Exception) {
            val array = try {
                json.parseToJsonElement(text) as? JsonArray
            } catch (e: Exception) {
                warn("Data nejsou platné JSON pole", e)
                null
            } ?: return DecodeResult.Error

            if (array.isEmpty()) return DecodeResult.Ok(emptyList())

            var invalidEntries = false
            val out = buildList {
                array.forEach { element ->
                    try {
                        add(json.decodeFromJsonElement(serializer, element))
                    } catch (e: Exception) {
                        invalidEntries = true
                        warn("Přeskakuji poškozený záznam", e)
                    }
                }
            }

            if (out.isEmpty()) DecodeResult.Error
            else DecodeResult.Ok(out, hadInvalidEntries = invalidEntries)
        }
    }

    fun decodeRemindersResult(text: String): DecodeResult<Reminder> =
        decodeList(text, Reminder.serializer())

    fun decodeFavoritesResult(text: String): DecodeResult<FavoritePlace> =
        decodeList(text, FavoritePlace.serializer())

    /** Kompatibilní dekódování pro widget, který data pouze zobrazuje. */
    fun decodeReminders(text: String): List<Reminder> =
        (decodeRemindersResult(text) as? DecodeResult.Ok)?.value.orEmpty()

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
        if (!f.exists()) return ReadResult.Empty
        val atomicFile = android.util.AtomicFile(f)
        return try {
            val text = atomicFile.readFully().toString(Charsets.UTF_8)
            ReadResult.Ok(text)
        } catch (e: Exception) {
            warn("Čtení $filename selhalo", e)
            ReadResult.Error
        }
    }

    /** Kompatibilní čtení pro widget (jen zobrazuje): null = prázdno i chyba. */
    fun readText(context: Context, filename: String): String? =
        (read(context, filename) as? ReadResult.Ok)?.text

    /**
     * Atomický zápis přes android.util.AtomicFile: zapisuje přes dočasný soubor,
     * při selhání zachovává původní obsah. Zápisy jsou serializované
     * (@Synchronized), aby se souběžné zápisy z UI a z receiveru nepraly.
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
            warn("Zápis $filename selhal – změna zůstala jen v paměti", e)
            false
        }
    }
}
