package com.social_downloader.engine

import android.app.Application
import java.io.File
import java.util.UUID
import com.social_downloader.data.CookieStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface EngineState {
    data object Initializing : EngineState
    data object Ready : EngineState
    data class Failed(val message: String) : EngineState
}

enum class UpdateChannel { STABLE, NIGHTLY }

/**
 * Thin wrapper around the on-device yt-dlp (bundled Python + FFmpeg + QuickJS).
 * Everything runs locally; no backend server is involved.
 */
class YtDlpEngine(private val app: Application, private val cookies: CookieStore,
    private val recoveryChannel: () -> UpdateChannel = { UpdateChannel.STABLE }) {

    private val _state = MutableStateFlow<EngineState>(EngineState.Initializing)
    val state: StateFlow<EngineState> = _state.asStateFlow()
    private val initMutex = Mutex()
    private val recoveryMutex = Mutex()
    private val updateMutex = Mutex()
    private var recoveryAttempted = false
    private val snapshots = PreviewSnapshotStore(File(app.cacheDir, "preview-info"))
    fun clearSnapshots() = snapshots.clear()
    fun copySnapshot(url: String, destination: File) = snapshots.copyTo(url, destination)

    suspend fun recoverTikTok(raw: String?): Boolean {
        if (!ExtractionRecovery.isTikTokParseFailure(Platform.TIKTOK, raw)) return false
        return recoveryMutex.withLock {
            if (recoveryAttempted) false else {
                recoveryAttempted = true
                try { updateEngine(recoveryChannel()); true }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { false }
            }
        }
    }

    private val _version = MutableStateFlow<String?>(null)
    val version: StateFlow<String?> = _version.asStateFlow()

    /** Unpacks Python/FFmpeg on first launch (a few seconds), then is a no-op. */
    suspend fun ensureReady() {
        if (_state.value is EngineState.Ready) return
        withContext(Dispatchers.IO) {
            initMutex.withLock {
                if (_state.value is EngineState.Ready) return@withLock
                try {
                    YoutubeDL.getInstance().init(app)
                    FFmpeg.getInstance().init(app)
                    _version.value = runCatching { YoutubeDL.versionName(app) }.getOrNull()
                    _state.value = EngineState.Ready
                } catch (t: Throwable) {
                    _state.value = EngineState.Failed(t.message ?: "Engine failed to start")
                    throw DownloaderException(
                        "The download engine failed to start (${t.message ?: t.javaClass.simpleName}). " +
                            "Try restarting the app or reinstalling it.",
                        t,
                    )
                }
            }
        }
    }

    suspend fun fetchInfo(url: String): MediaInfo {
        try { return fetchInfoOnce(url) }
        catch (e: DownloaderException) {
            if (UrlTools.detect(url) == Platform.TIKTOK && recoverTikTok(e.cause?.message)) return fetchInfoOnce(url)
            throw e
        }
    }

    private suspend fun fetchInfoOnce(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
        val platform = UrlTools.detect(url)
        val attempts = buildList<String?> {
            add(null)
            if (platform == Platform.YOUTUBE) add(YOUTUBE_FALLBACK_CLIENTS)
        }
        var lastError: Throwable? = null
        val cookieFile = cookies.tempCopy(platform)
        val infoDir = File(app.cacheDir, "info-${UUID.randomUUID()}")
        try {
            for (extractorArgs in attempts) {
                try {
                    val request = YoutubeDLRequest(url).apply {
                        addOption("--no-playlist")
                        addOption("--playlist-items", "1")
                        addOption("--no-warnings")
                        addOption("--socket-timeout", "20")
                        addOption("--retries", "3")
                        if (cookieFile != null) addOption("--cookies", cookieFile.absolutePath)
                        if (extractorArgs != null) addOption("--extractor-args", extractorArgs)
                    }
                    if (platform == Platform.TIKTOK) {
                        infoDir.mkdirs()
                        request.addOption("--skip-download")
                        request.addOption("--no-simulate")
                        request.addOption("--write-info-json")
                        request.addOption("-o", "${infoDir.absolutePath}/preview.%(ext)s")
                    }
                    val info = YoutubeDL.getInstance().getInfo(request)
                    if (platform == Platform.TIKTOK) {
                        infoDir.listFiles()?.firstOrNull { it.name.endsWith(".info.json") }?.let { snapshots.put(url, it) }
                    }
                    return@withContext MediaInfoMapper.map(url, info, extractorArgs)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    lastError = t
                }
            }
        } finally {
            cookieFile?.delete()
            infoDir.deleteRecursively()
        }
        throw DownloaderException(ErrorMapper.map(lastError?.message, platform, cookies.has(platform)), lastError)
    }

    /** Updates the bundled yt-dlp from the official GitHub releases (extractors break often). */
    suspend fun updateEngine(channel: UpdateChannel): String = withContext(Dispatchers.IO) {
        ensureReady()
        updateMutex.withLock {
            try {
                val ch = when (channel) {
                    UpdateChannel.STABLE -> YoutubeDL.UpdateChannel.STABLE
                    UpdateChannel.NIGHTLY -> YoutubeDL.UpdateChannel.NIGHTLY
                }
                val status = YoutubeDL.getInstance().updateYoutubeDL(app, ch)
                _version.value = runCatching { YoutubeDL.versionName(app) }.getOrNull()
                when (status) {
                    YoutubeDL.UpdateStatus.DONE -> "Updated to yt-dlp ${_version.value ?: ""}".trim()
                    YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already up to date (${_version.value ?: "latest"})"
                    null -> "Update finished"
                }
            } catch (e: CancellationException) { throw e }
            catch (t: Throwable) {
                throw DownloaderException("Update failed: ${ErrorMapper.summarize(t.message)}", t)
            }
        }
    }

    companion object {
        /** Used only if the default YouTube clients fail; avoids needing a PO token on some videos. */
        const val YOUTUBE_FALLBACK_CLIENTS = "youtube:player_client=android_vr,mweb"
    }
}
