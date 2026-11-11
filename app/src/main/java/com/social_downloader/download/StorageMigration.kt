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
    fun migrate(context: Context, items: List<DownloadItem>, onIssue: (String) -> Unit = {}): Map<String, DownloadItem> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) scoped(context, items, onIssue)
        else legacy(context, items, onIssue)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun scoped(context: Context, items: List<DownloadItem>, onIssue: (String) -> Unit): Map<String, DownloadItem> {
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
                    }.onFailure { onIssue("Some old downloads couldn't be moved. Retry, or move them with your file manager.") }
                }
            }
        }.onFailure { onIssue("Old downloads couldn't be checked. Retry, or move them with your file manager.") }
        return emptyMap() // URI references remain valid after a MediaStore move.
    }

    @Suppress("DEPRECATION")
    private fun legacy(context: Context, items: List<DownloadItem>, onIssue: (String) -> Unit): Map<String, DownloadItem> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            onIssue("Allow storage access on Android 9 to organize old downloads.")
            return emptyMap()
        }
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val old = File(base, "SocialDownloader")
        val journal = context.getSharedPreferences("storage_migration_paths", Context.MODE_PRIVATE)
        val updated = mutableMapOf<String, DownloadItem>()
        // Recover the history reference if the process died after rename but before history was saved.
        items.forEach { item ->
            val record = journal.getString(item.id, null)?.let { runCatching { org.json.JSONObject(it) }.getOrNull() }
                ?: return@forEach
            val target = File(record.getString("target"))
            val source = File(record.getString("source"))
            if (item.filePath == target.absolutePath) journal.edit().remove(item.id).commit()
            else if (item.filePath == source.absolutePath && !source.exists() && target.isFile) {
                runCatching {
                    updated[item.id] = item.copy(filePath = target.absolutePath,
                        fileUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString(), fileName = target.name)
                }.onFailure { onIssue("Couldn't restore access to a previously moved file. Retry migration.") }
            }
        }
        if (!old.isDirectory) return updated
        val byPath = items.associateBy { it.filePath }
        old.walkTopDown().filter { it.isFile }.toList().forEach { source ->
            val item = byPath[source.absolutePath]
            val relative = source.relativeTo(old)
            val folder = item?.platform?.folderName ?: relative.parent.orEmpty().ifBlank { "Other" }
            val dir = File(base, "${MediaSaver.SUBFOLDER}/$folder")
            if (!dir.exists() && !dir.mkdirs()) {
                onIssue("Some folders couldn't be created. Retry after freeing space.")
                return@forEach
            }
            var target = File(dir, source.name)
            var index = 1
            while (target.exists()) {
                target = File(dir, "${source.nameWithoutExtension} (${index++})${source.extension.takeIf { it.isNotEmpty() }?.let { ".$it" }.orEmpty()}")
            }
            val targetUri = if (item != null) runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target).toString()
            }.getOrElse {
                onIssue("Couldn't prepare access to an old file; it was left in place.")
                return@forEach
            } else null
            if (item != null && !journal.edit().putString(item.id, org.json.JSONObject()
                    .put("source", source.absolutePath).put("target", target.absolutePath).toString()).commit()) {
                onIssue("Couldn't record migration progress. Old files were left in place.")
                return@forEach
            }
            // Same-volume rename is atomic. Failed moves leave source + history intact.
            if (source.renameTo(target)) {
                MediaScannerConnection.scanFile(context, arrayOf(source.absolutePath, target.absolutePath), null, null)
                if (item != null) updated[item.id] = item.copy(filePath = target.absolutePath,
                    fileUri = targetUri, fileName = target.name)
            } else onIssue("Some old files couldn't be moved. Retry, or use your file manager.")
        }
        old.walkBottomUp().filter { it.isDirectory }.forEach { it.delete() } // only empty dirs delete
        return updated
    }
}
