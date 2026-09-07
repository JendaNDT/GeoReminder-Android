package cz.jenda.georeminder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Pomocník pro správu fotek a PDF příloh u připomínek. */
object AttachmentHelper {
    private const val DIR_ATTACHMENTS = "attachments"

    const val MAX_ATTACHMENT_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB

    internal fun isAllowedAttachmentSize(size: Long): Boolean =
        size in 1..MAX_ATTACHMENT_SIZE_BYTES

    private fun attachmentsDir(context: Context): File =
        File(context.applicationContext.filesDir, DIR_ATTACHMENTS)

    private fun ensureAttachmentsDir(context: Context): File? {
        val dir = attachmentsDir(context)
        if (dir.exists()) return dir.takeIf { it.isDirectory }
        return dir.takeIf { it.mkdirs() }
    }

    private fun managedAttachmentFile(context: Context, path: String): File? {
        return try {
            val base = attachmentsDir(context).canonicalFile
            val candidate = File(path).canonicalFile
            val prefix = base.path + File.separator
            candidate.takeIf { it.path.startsWith(prefix) }
        } catch (_: Exception) {
            null
        }
    }

    fun managedAttachmentForBackup(context: Context, path: String?): File? {
        if (path.isNullOrBlank()) return null
        return managedAttachmentFile(context, path)
            ?.takeIf { it.exists() && it.isFile && isAllowedAttachmentSize(it.length()) }
    }

    fun normalizeRestoredAttachmentPath(context: Context, path: String?): String? {
        if (path.isNullOrBlank()) return null
        val direct = managedAttachmentFile(context, path)
        if (direct != null && direct.exists() && direct.isFile) {
            return direct.absolutePath
        }
        return try {
            val fileName = File(path).name
            if (fileName.isBlank() || fileName == "." || fileName == "..") return null
            val base = attachmentsDir(context).canonicalFile
            val candidate = File(base, fileName).canonicalFile
            val prefix = base.path + File.separator
            candidate.takeIf {
                it.path.startsWith(prefix) && it.exists() && it.isFile
            }?.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun copyToInternal(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver

            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        val size = cursor.getLong(sizeIndex)
                        if (!isAllowedAttachmentSize(size)) {
                            android.util.Log.w("AttachmentHelper", "Příloha má nepovolenou velikost ($size B)")
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
            val dir = ensureAttachmentsDir(context) ?: run {
                android.util.Log.w("AttachmentHelper", "Nepodařilo se vytvořit adresář příloh")
                return null
            }

            val targetFile = File(dir, "${UUID.randomUUID()}.$ext")
            val inputStream = contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                copyStreamToManagedFile(targetFile, input)
            }
        } catch (e: Exception) {
            android.util.Log.e("AttachmentHelper", "Chyba při kopírování přílohy", e)
            null
        }
    }

    fun copyBackupEntryToInternal(
        context: Context,
        entryName: String,
        input: InputStream,
    ): String? {
        return try {
            val dir = ensureAttachmentsDir(context) ?: return null
            val extension = File(entryName).extension
                .lowercase()
                .takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }
                ?: "bin"
            val target = File(dir, "${UUID.randomUUID()}.$extension")
            copyStreamToManagedFile(target, input)
        } catch (e: Exception) {
            android.util.Log.w("AttachmentHelper", "Import přílohy ze zálohy selhal", e)
            null
        }
    }

    private fun copyStreamToManagedFile(targetFile: File, input: InputStream): String? {
        var bytesCopied = 0L
        return try {
            targetFile.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    bytesCopied += read
                    if (bytesCopied > MAX_ATTACHMENT_SIZE_BYTES) {
                        throw AttachmentTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
            }
            if (!isAllowedAttachmentSize(bytesCopied)) {
                targetFile.delete()
                null
            } else {
                targetFile.absolutePath
            }
        } catch (_: AttachmentTooLargeException) {
            targetFile.delete()
            android.util.Log.w("AttachmentHelper", "Příloha přesahuje limit 10 MB během kopírování")
            null
        } catch (e: Exception) {
            targetFile.delete()
            throw e
        }
    }

    private class AttachmentTooLargeException : RuntimeException()

    fun deleteAttachment(context: Context, path: String?) {
        if (path.isNullOrBlank()) return
        val file = managedAttachmentFile(context, path) ?: run {
            android.util.Log.w("AttachmentHelper", "Odmítnuto mazání cesty mimo attachments")
            return
        }
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun deleteAttachments(context: Context, paths: Iterable<String>) {
        paths.forEach { deleteAttachment(context, it) }
    }

    fun cleanupOrphanedAttachments(context: Context, activeReminders: List<cz.jenda.georeminder.model.Reminder>) {
        try {
            val dir = attachmentsDir(context)
            if (!dir.exists() || !dir.isDirectory) return
            val activePaths = activeReminders.mapNotNull { reminder ->
                reminder.attachmentPath?.let { managedAttachmentFile(context, it)?.absolutePath }
            }.toSet()
            dir.listFiles()?.forEach { file ->
                if (!activePaths.contains(file.canonicalFile.absolutePath)) {
                    android.util.Log.i("AttachmentHelper", "Mazání osiřelé přílohy: ${file.name}")
                    file.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun openAttachment(context: Context, path: String) {
        try {
            val file = managedAttachmentFile(context, path) ?: return
            if (!file.exists() || !file.isFile) return

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val mimeType = when {
                file.name.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                file.name.endsWith(".png", ignoreCase = true) -> "image/png"
                file.name.endsWith(".jpg", ignoreCase = true) || file.name.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
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
