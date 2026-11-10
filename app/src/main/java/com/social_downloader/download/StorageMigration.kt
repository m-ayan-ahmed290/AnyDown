package com.social_downloader.download

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.social_downloader.data.DownloadItem
import com.social_downloader.engine.Platform
import java.io.File

/** Idempotent upgrade: MediaStore moves retain their content URI; legacy moves update history.
 * Unknown files retain their old subfolder or go to Other. Never guess their originating site.
 * No all-files permission: inaccessible files remain untouched and can be retried on next launch.
 */
object StorageMigration {
    fun migrate(context: Context, items: List<DownloadItem>): Map<String, DownloadItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) scoped(context, items)
        else legacy(context, items)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun scoped(context: Context, items: List<DownloadItem>): Map<String, DownloadItem> {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val byUri = items.associateBy { it.fileUri }
        // OWNER_PACKAGE_NAME prevents moving downloads belonging to other apps.
        runCatching {
            resolver.query(collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME} = ?",
                arrayOf("${Environment.DIRECTORY_DOWNLOADS}/SocialDownloader/%", context.packageName), null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val uri = ContentUris.withAppendedId(collection, cursor.getLong(0))
                    val old = cursor.getString(1)
                    val known = byUri[uri.toString()]?.platform
                        ?: items.firstOrNull { it.fileUri?.substringAfterLast('/') == uri.lastPathSegment }?.platform
                    val suffix = old.substringAfter("/SocialDownloader/", "").trim('/')
                    val folder = known?.folderName ?: suffix.ifBlank { Platform.OTHER.folderName }
                    runCatching {
                        resolver.update(uri, ContentValues().apply {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/${MediaSaver.SUBFOLDER}/$folder/")
                        }, null, null)
                    }
                }
            }
        }
        return emptyMap() // URI references remain valid after a MediaStore move.
    }

    @Suppress("DEPRECATION")
    private fun legacy(context: Context, items: List<DownloadItem>): Map<String, DownloadItem> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) return emptyMap()
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val old = File(base, "SocialDownloader")
        if (!old.isDirectory) return emptyMap()
        val byPath = items.associateBy { it.filePath }
        val updated = mutableMapOf<String, DownloadItem>()
        old.walkTopDown().filter { it.isFile }.toList().forEach { source ->
            val item = byPath[source.absolutePath]
            val relative = source.relativeTo(old)
            val folder = item?.platform?.folderName ?: relative.parent.orEmpty().ifBlank { "Other" }
            val dir = File(base, "${MediaSaver.SUBFOLDER}/$folder")
            if (!dir.exists() && !dir.mkdirs()) return@forEach
            var target = File(dir, source.name)
            var index = 1
            while (target.exists()) {
                target = File(dir, "${source.nameWithoutExtension} (${index++})${source.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()}")
            }
            // Same-volume rename is atomic. Failed moves leave source + history intact.
            if (source.renameTo(target)) {
                MediaScannerConnection.scanFile(context, arrayOf(source.absolutePath, target.absolutePath), null, null)
                if (item != null) updated[item.id] = item.copy(filePath = target.absolutePath,
                    fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString(), fileName = target.name)
            }
        }
        old.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() } // only empty dirs delete
        return updated
    }
}
