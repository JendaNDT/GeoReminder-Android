package cz.jenda.georeminder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/** Pomocník pro správu fotek a PDF příloh u připomínek. */
object AttachmentHelper {
    private const val DIR_ATTACHMENTS = "attachments"

    private const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

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

            val mimeType = contentResolver.getType(uri) ?: ""
            val ext = when {
                mimeType.contains("pdf") -> "pdf"
                mimeType.contains("png") -> "png"
                mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                else -> "bin"
            }
            val dir = File(context.filesDir, DIR_ATTACHMENTS)
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

            targetFile.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("AttachmentHelper", "Chyba při kopírování přílohy", e)
            null
        }
    }

    /** Smaže soubor přílohy z interního úložiště. */
    fun deleteAttachment(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        try {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    /** Smaže soubory příloh, které už nepatří žádné aktivní připomínce. */
    fun cleanupOrphanedAttachments(context: Context, activeReminders: List<cz.jenda.georeminder.model.Reminder>) {
        try {
            val dir = File(context.filesDir, DIR_ATTACHMENTS)
            if (!dir.exists() || !dir.isDirectory) return
            val activePaths = activeReminders.mapNotNull { it.attachmentPath }.toSet()
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
            val file = File(path)
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
