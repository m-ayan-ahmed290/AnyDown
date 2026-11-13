package com.social_downloader.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.social_downloader.engine.DownloaderException
import java.io.File
import java.io.IOException

/**
 * Publishes finished files into the public Downloads folder.
 *  - Android 10+ : MediaStore.Downloads (scoped storage, no permission required)
 *  - Android 9   : direct file write (needs WRITE_EXTERNAL_STORAGE) + media scan
 */
object MediaSaver {
    const val SUBFOLDER = "AnyDowns"

    data class Saved(val uri: Uri, val path: String?, val displayName: String, val sizeBytes: Long)

    fun save(context: Context, src: File, mime: String, useSubfolder: Boolean, platform: com.social_downloader.engine.Platform): Saved {
        val name = sanitize(src.name)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(context, src, name, mime, useSubfolder, platform)
        } else {
            saveLegacy(context, src, name, mime, useSubfolder, platform)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun saveScoped(context: Context, src: File, name: String, mime: String, useSubfolder: Boolean, platform: com.social_downloader.engine.Platform): Saved {
        val resolver = context.contentResolver
        val relativePath = if (useSubfolder) "${Environment.DIRECTORY_DOWNLOADS}/$SUBFOLDER/${platform.folderName}"
        else Environment.DIRECTORY_DOWNLOADS
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Couldn't create a file in Downloads")
        try {
            resolver.openOutputStream(uri, "w")?.use { out ->
                src.inputStream().use { it.copyTo(out, 256 * 1024) }
            } ?: throw IOException("Couldn't open the destination file")
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        // MediaStore may have appended " (1)" if the name already existed.
        val finalName = resolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: name
        return Saved(uri, null, finalName, src.length())
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, src: File, name: String, mime: String, useSubfolder: Boolean, platform: com.social_downloader.engine.Platform): Saved {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw DownloaderException("Storage permission is required on Android 9 to save into Downloads. Grant it and retry.")
        }
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val dir = if (useSubfolder) File(base, "$SUBFOLDER/${platform.folderName}") else base
        if (!dir.exists() && !dir.mkdirs() && !dir.isDirectory) throw IOException("Couldn't create ${dir.absolutePath}")

        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var target = File(dir, name)
        var n = 1
        while (!target.createNewFile()) {
            target = File(dir, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            n++
        }
        try {
            src.inputStream().use { input -> target.outputStream().use { input.copyTo(it, 256 * 1024) } }
        } catch (t: Throwable) {
            target.delete()
            throw t
        }
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
        return Saved(uri, target.absolutePath, target.name, target.length())
    }

    fun exists(context: Context, uri: String?, path: String?): Boolean {
        if (path != null) return File(path).exists()
        if (uri == null) return false
        return try {
            context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun delete(context: Context, uri: String?, path: String?) {
        try {
            if (path != null) File(path).delete()
            else if (uri != null) context.contentResolver.delete(Uri.parse(uri), null, null)
        } catch (_: Exception) {
            // Already gone, or we no longer own it (e.g. after a reinstall) – nothing more to do.
        }
    }

    fun mimeFor(file: File): String = when (val ext = file.extension.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "opus" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    fun sanitize(name: String): String {
        val cleaned = name.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), "_").trim().trimStart('.')
        val limited = if (cleaned.length > 150) {
            val ext = cleaned.substringAfterLast('.', "")
            cleaned.take(140).trimEnd() + if (ext.isNotEmpty() && ext.length <= 5) ".$ext" else ""
        } else cleaned
        return limited.ifBlank { "video.mp4" }
    }
}
