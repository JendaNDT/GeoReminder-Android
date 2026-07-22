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

    /** Zkopíruje vybraný URI soubor do interního úložiště aplikace. */
    fun copyToInternal(context: Context, uri: Uri): String? {
        return try {
            val contentResolver = context.contentResolver
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
            contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (_: Exception) {
            null
        }
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
