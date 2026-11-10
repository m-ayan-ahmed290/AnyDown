package com.social_downloader.data

import android.content.Context
import com.social_downloader.engine.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID

/**
 * Per-site session cookies from the user's OWN sign-in, kept in app-private storage (excluded from
 * backups). They are only handed to yt-dlp for requests to the matching site, via a short-lived copy.
 */
class CookieStore(private val context: Context) {
    private val dir = File(context.filesDir, "cookies").apply { mkdirs() }

    private val _signedIn = MutableStateFlow(load())
    val signedIn: StateFlow<Set<Platform>> = _signedIn.asStateFlow()

    private fun fileFor(p: Platform) = File(dir, "${p.name}.txt")

    private fun load(): Set<Platform> = Platform.entries.filter { fileFor(it).isFile }.toSet()

    fun has(p: Platform): Boolean = fileFor(p).isFile

    @Synchronized
    fun save(p: Platform, content: String) {
        fileFor(p).writeText(content)
        _signedIn.value = load()
    }

    @Synchronized
    fun clear(p: Platform) {
        fileFor(p).delete()
        _signedIn.value = load()
    }

    /** yt-dlp rewrites its cookie file on exit, so every run gets its own throw-away copy. */
    fun tempCopy(p: Platform): File? {
        val src = fileFor(p)
        if (!src.isFile) return null
        val copy = File(context.cacheDir, "ck_${UUID.randomUUID()}.txt")
        return try {
            src.copyTo(copy, overwrite = true)
            copy
        } catch (_: Exception) {
            null
        }
    }
}
