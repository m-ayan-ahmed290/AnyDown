package com.social_downloader.engine

import java.io.File
import java.security.MessageDigest

/** Private, short-lived yt-dlp info JSON, including format URLs and required request headers. */
class PreviewSnapshotStore(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private val lifetime = 5 * 60 * 1000L
    private fun path(url: String) = File(directory, MessageDigest.getInstance("SHA-256")
        .digest(url.toByteArray()).joinToString("") { "%02x".format(it) } + ".json")

    @Synchronized fun put(url: String, source: File) {
        directory.mkdirs()
        directory.listFiles()?.filter { now() - it.lastModified() > lifetime }?.forEach { it.delete() }
        val target = path(url)
        val temp = File(directory, target.name + ".tmp")
        source.copyTo(temp, overwrite = true)
        check(temp.renameTo(target)) { "Couldn't save preview" }
        target.setLastModified(now())
        directory.listFiles()?.filter { it.extension == "json" }?.sortedByDescending { it.lastModified() }
            ?.drop(30)?.forEach { it.delete() }
    }

    @Synchronized fun copyTo(url: String, target: File): Boolean {
        val source = path(url)
        val age = now() - source.lastModified()
        if (!source.isFile || age < 0 || age > lifetime) { source.delete(); return false }
        source.copyTo(target, overwrite = true)
        return true
    }

    @Synchronized fun clear() { directory.deleteRecursively() }
}
