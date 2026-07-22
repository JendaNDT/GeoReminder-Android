package cz.jenda.georeminder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import cz.jenda.georeminder.model.Attachment
import cz.jenda.georeminder.model.AttachmentKind
import cz.jenda.georeminder.model.Reminder
import cz.jenda.georeminder.model.newUUID
import java.io.File

/**
 * Práce s přílohami připomínek. **Soubory se KOPÍRUJÍ** do privátního úložiště
 * aplikace (`filesDir/attachments/`) – rozhodnutí z 22. 7.: přílohy tak přežijí
 * smazání originálu i přeinstalaci (jsou v řízené záloze). V připomínce se drží
 * jen malý [Attachment] s názvem souboru.
 */
object Attachments {
    const val MAX_COUNT = 5
    const val MAX_BYTES = 10L * 1024 * 1024 // 10 MB

    private const val TAG = "Attachments"

    // Názvy souborů rozdělané přílohy (otevřené v editoru, ještě neuložené).
    // gc je nesmí uklidit, dokud uživatel úpravu neuloží nebo nezruší – jinak by
    // smazal přílohu, když ve výběru souboru strávíš přes minutu (ON_RESUME).
    private val pending = java.util.Collections.synchronizedSet(HashSet<String>())

    fun markPending(fileName: String) {
        pending.add(fileName)
    }

    fun unmarkPending(fileName: String) {
        pending.remove(fileName)
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "attachments").apply { if (!exists()) mkdirs() }

    fun fileFor(context: Context, att: Attachment): File = File(dir(context), att.fileName)

    /** Zobrazovaný název, MIME a velikost (v bajtech, -1 když neznámá) z content URI. */
    fun probe(context: Context, uri: Uri): Triple<String, String, Long> {
        var name = "příloha"
        var size = -1L
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (c.moveToFirst()) {
                    if (ni >= 0) c.getString(ni)?.let { name = it }
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nepodařilo se zjistit údaje o příloze", e)
        }
        return Triple(name, mime, size)
    }

    fun kindFor(mime: String): AttachmentKind = when {
        mime.startsWith("image/") -> AttachmentKind.PHOTO
        mime == "application/pdf" -> AttachmentKind.DOCUMENT
        else -> AttachmentKind.OTHER
    }

    /**
     * Zkopíruje obsah [uri] do úložiště aplikace. Vrací [Attachment], nebo null
     * při chybě. Volat na IO vlákně (kopíruje soubor).
     */
    fun copyIn(context: Context, uri: Uri, displayName: String, mime: String): Attachment? {
        val ext = displayName.substringAfterLast('.', "")
            .takeIf { it.isNotBlank() && it.length <= 5 }
        val id = newUUID()
        val fileName = if (ext != null) "$id.$ext" else id
        val target = File(dir(context), fileName)
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: run { target.delete(); return null }
            // Kopírovat po blocích a hlídat strop velikosti (i když provider
            // velikost předem nehlásí). Při překročení / chybě soubor smazat.
            var total = 0L
            stream.use { input ->
                target.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) {
                            throw java.io.IOException("Příloha překračuje limit")
                        }
                        output.write(buf, 0, n)
                    }
                }
            }
            markPending(fileName)
            Attachment(
                id = id,
                fileName = fileName,
                displayName = displayName,
                mime = mime,
                kind = kindFor(mime),
            )
        } catch (e: Exception) {
            Log.w(TAG, "Kopírování přílohy selhalo", e)
            try { target.delete() } catch (_: Exception) {}
            null
        }
    }

    /** Otevře přílohu ve vhodné aplikaci (přes FileProvider). Volat z hlavního vlákna. */
    fun open(context: Context, att: Attachment) {
        val file = fileFor(context, att)
        if (!file.exists()) {
            Toast.makeText(context, "Příloha už není dostupná", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = FileProvider.getUriForFile(
                context, context.packageName + ".fileprovider", file,
            )
            val view = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, att.mime.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(view)
        } catch (e: Exception) {
            Log.w(TAG, "Otevření přílohy selhalo", e)
            Toast.makeText(context, "Přílohu není čím otevřít", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Úklid: smaže soubory příloh, které už nepatří žádné připomínce. Čerstvě
     * přidané soubory (< 60 s) nechá být – mohou být z rozdělané úpravy, která
     * se ještě neuložila. Volat při návratu appky do popředí.
     */
    fun gc(context: Context, reminders: List<Reminder>) {
        // Referenced spočítat hned (z paměti), samotné mazání souborů udělat mimo
        // hlavní vlákno.
        val referenced = reminders.flatMap { it.attachments }.map { it.fileName }.toSet()
        Thread {
            try {
                val cutoff = System.currentTimeMillis() - 60_000L
                dir(context).listFiles()?.forEach { f ->
                    if (f.name !in referenced && f.name !in pending && f.lastModified() < cutoff) {
                        f.delete()
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Úklid příloh selhal", e)
            }
        }.start()
    }
}
