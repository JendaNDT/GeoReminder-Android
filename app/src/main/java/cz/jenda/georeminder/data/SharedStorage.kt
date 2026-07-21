package cz.jenda.georeminder.data

import android.content.Context
import kotlinx.serialization.json.Json
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

    fun file(context: Context, filename: String): File =
        File(context.applicationContext.filesDir, filename)

    fun readText(context: Context, filename: String): String? {
        val f = file(context, filename)
        return try {
            if (f.exists()) f.readText() else null
        } catch (_: Exception) {
            null
        }
    }

    /** Atomický zápis: nejdřív do dočasného souboru, pak přejmenování. */
    fun writeText(context: Context, filename: String, content: String) {
        try {
            val f = file(context, filename)
            val tmp = File(f.parentFile, "$filename.tmp")
            tmp.writeText(content)
            if (!tmp.renameTo(f)) {
                f.writeText(content)
            }
        } catch (_: Exception) {
        }
    }
}
