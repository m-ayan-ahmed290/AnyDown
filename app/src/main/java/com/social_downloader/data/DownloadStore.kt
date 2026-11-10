package com.social_downloader.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Tiny JSON file store for queue + history (kept in app-private storage, excluded from backups). */
class DownloadStore(private val file: File) {

    @Synchronized
    fun load(): List<DownloadItem> {
        if (!file.exists()) return emptyList()
        return try {
            val arr = JSONObject(file.readText()).getJSONArray("items")
            (0 until arr.length()).mapNotNull { i ->
                runCatching { DownloadItem.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun save(items: List<DownloadItem>) {
        try {
            val arr = JSONArray()
            items.forEach { arr.put(it.toJson()) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("version", 1).put("items", arr).toString())
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Persisting history is best-effort; never crash a download because of it.
        }
    }
}
