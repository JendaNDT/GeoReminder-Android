package cz.jenda.georeminder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.util.Collections
import java.util.UUID

/** Pomocník pro správu fotek a PDF příloh u připomínek. */
object AttachmentHelper {
    private const val DIR_ATTACHMENTS = "attachments"

    private const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

    /** Nově zkopírované soubory, které ještě nejsou potvrzené uložením reminderu. */
    private val pendingPaths = Collections.synchronizedSet(mutableSetOf<String>())

    private fun attachmentsDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_ATTACHMENTS)

    /**
     * Vrátí soubor pouze tehdy, když leží uvnitř naší složky příloh.
     * Chrání mazání i FileProvider před cestou podstrčenou importovaným JSONem.
     */
    private fun managedFile(context: Context, path: String): File? = try {
        val root = attachmentsDir(context).canonicalFile
        val candidate = File(path).canonicalFile
        val rootPrefix = root.path + File.separator
        candidate.takeIf { it.path.startsWith(rootPrefix) }
    } catch (_: Exception) {
        null
    }

    /** Zkopíruje vybraný URI soubor do interního úložiště aplikace. Povoleno max 10 MB. */
    fun copyToInternal(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
            
            // Kontrola velikosti přes ContentResolver query pokud je k dispozici
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        val size = cursor.getLong(sizeIndex)
                        if (size > MAX_ATTACHMENT_SIZE_BYTES) {
                            android.util.Log.w("AttachmentHelper", "Příloha přesahuje limit 10 MB ($size B)")
                            return null
                        }
                    }
                }
            }

            val mimeType = contentResolver.getType(uri)?.lowercase() ?: return null
            val ext = when {
                mimeType == "application/pdf" -> "pdf"
                mimeType == "image/png" -> "png"
                mimeType == "image/jpeg" || mimeType == "image/jpg" -> "jpg"
                else -> return null
            }
            val dir = attachmentsDir(context)
            if (!dir.exists()) dir.mkdirs()

            val targetFile = File(dir, "${UUID.randomUUID()}.$ext")
            var bytesCopied = 0L

            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                targetFile.delete()
                return null
            }

            inputStream.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        bytesCopied += read
                        if (bytesCopied > MAX_ATTACHMENT_SIZE_BYTES) {
                            output.close()
                            targetFile.delete()
                            android.util.Log.w("AttachmentHelper", "Příloha přesahuje limit 10 MB během kopírování")
                            return null
                        }
                        output.write(buffer, 0, read)
                    }
                }
            }

            if (bytesCopied == 0L) {
                targetFile.delete()
                return null
            }
            if (!hasExpectedSignature(targetFile, ext)) {
                targetFile.delete()
                Log.w("AttachmentHelper", "Obsah přílohy neodpovídá deklarovanému typu")
                return null
            }

            targetFile.absolutePath.also { pendingPaths.add(it) }
        } catch (e: Exception) {
            android.util.Log.e("AttachmentHelper", "Chyba při kopírování přílohy", e)
            null
        }
    }

    private fun hasExpectedSignature(file: File, extension: String): Boolean = try {
        val header = ByteArray(8)
        val count = file.inputStream().use { it.read(header) }
        when (extension) {
            "pdf" -> count >= 5 && header.copyOfRange(0, 5).contentEquals("%PDF-".toByteArray())
            "png" -> count >= 8 && header.contentEquals(
                byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            )
            "jpg" -> count >= 3 && header[0] == 0xFF.toByte() &&
                header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()
            else -> false
        }
    } catch (_: Exception) {
        false
    }

    /** Smaže soubor přílohy z interního úložiště. */
    fun deleteAttachment(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = managedFile(context, path) ?: return
            pendingPaths.remove(file.absolutePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    /** Příloha už je bezpečně zapsaná v reminders.json. */
    fun markPersisted(path: String) {
        pendingPaths.remove(path)
    }

    /** Smaže soubory příloh, které už nepatří žádné aktivní připomínce. */
    fun cleanupOrphanedAttachments(context: Context, activeReminders: List<cz.jenda.georeminder.model.Reminder>) {
        try {
            val dir = attachmentsDir(context)
            if (!dir.exists() || !dir.isDirectory) return
            val activePaths = activeReminders.mapNotNull { reminder ->
                reminder.attachmentPath?.let { managedFile(context, it)?.absolutePath }
            }.toMutableSet()
            synchronized(pendingPaths) {
                activePaths.addAll(pendingPaths)
            }
            dir.listFiles()?.forEach { file ->
                if (!activePaths.contains(file.absolutePath)) {
                    android.util.Log.i("AttachmentHelper", "Mazání osiřelé přílohy: ${file.name}")
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    /** Otevře přílohu v systémové aplikaci přes FileProvider. */
    fun openAttachment(context: Context, path: String) {
        try {
            val file = managedFile(context, path) ?: return
            if (!file.exists()) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when {
                path.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                path.endsWith(".png", ignoreCase = true) -> "image/png"
                path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
