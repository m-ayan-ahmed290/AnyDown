package com.social_downloader.download

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import com.social_downloader.data.DownloadItem
import com.social_downloader.data.DownloadStatus
import com.social_downloader.data.CookieStore
import com.social_downloader.data.DownloadStore
import com.social_downloader.data.SettingsStore
import com.social_downloader.engine.DownloaderException
import com.social_downloader.engine.ErrorMapper
import com.social_downloader.engine.FormatSelector
import com.social_downloader.engine.MediaInfo
import com.social_downloader.engine.QualityOption
import com.social_downloader.engine.YtDlpEngine
import com.social_downloader.util.Format
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Owns the download queue + history. Runs yt-dlp (with FFmpeg merging) in the background,
 * stages files in the app cache, then publishes the finished .mp4 to the public Downloads folder.
 */
class DownloadManager(
    private val app: Application,
    private val engine: YtDlpEngine,
    private val settings: SettingsStore,
    private val cookies: CookieStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val store = DownloadStore(File(app.filesDir, "downloads.json"))
    private val lock = Any()
    private val jobs = ConcurrentHashMap<String, Job>()
    private val processIds = ConcurrentHashMap<String, String>()

    private val _items = MutableStateFlow(
        store.load()
            .map {
                // The process died mid-download last time.
                if (it.status.isPending) it.copy(
                    status = DownloadStatus.FAILED,
                    progress = -1f,
                    error = "Interrupted. Tap retry to download again.",
                ) else it
            }
            .sortedByDescending { it.createdAt },
    )
    val items: StateFlow<List<DownloadItem>> = _items.asStateFlow()

    private val migrationLock = Mutex()
    private val persistenceLock = Mutex()
    private suspend fun saveCurrent() { persistenceLock.withLock { store.save(_items.value) } }
    private var migrated = false
    private val _migrationNotice = MutableStateFlow<String?>(null)
    val migrationNotice: StateFlow<String?> = _migrationNotice.asStateFlow()
    private suspend fun migrateStorage(force: Boolean = false) = withContext(Dispatchers.IO) {
        migrationLock.withLock {
            if (!migrated || force) {
                _migrationNotice.value = null
                val result = runCatching {
                    StorageMigration.migrate(app, _items.value) { _migrationNotice.value = it }
                }.getOrElse {
                    _migrationNotice.value = "Some old downloads couldn't be organized. Retry migration, or use your file manager."
                    emptyMap()
                }
                _items.update { current -> current.map { item ->
                    result[item.id]?.let { item.copy(fileUri = it.fileUri, filePath = it.filePath, fileName = it.fileName) } ?: item
                } }
                if (result.isNotEmpty()) saveCurrent()
                migrated = _migrationNotice.value == null
            }
        }
    }
    fun retryStorageMigration() { scope.launch { migrateStorage(force = true) } }

    init {
        retryStorageMigration()
        scope.launch { settings.maxConcurrent.collect { pump() } }
    }

    // ---------------------------------------------------------------- public API

    /** Explicit document access works for files whose MediaStore ownership was lost on uninstall. */
    @Synchronized
    fun importOldFile(uri: android.net.Uri, selectedSite: com.social_downloader.engine.Platform = com.social_downloader.engine.Platform.OTHER): Boolean {
        val prefs = app.getSharedPreferences("imported_files", Context.MODE_PRIVATE)
        val prior = prefs.getString(uri.toString(), null)
        if (prior != null && MediaSaver.exists(app, prior, null)) return false
        val name = app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "imported-video.mp4"
        val known = _items.value.firstOrNull { it.fileName == name }
        val site = selectedSite.takeIf { it != com.social_downloader.engine.Platform.OTHER } ?: known?.let { com.social_downloader.engine.UrlTools.detect(it.url).takeIf { p -> p != com.social_downloader.engine.Platform.OTHER } ?: it.platform }
            ?: com.social_downloader.engine.Platform.OTHER
        val dir = File(app.cacheDir, "import-${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val temp = File(dir, MediaSaver.sanitize(name))
            app.contentResolver.openInputStream(uri)?.use { input -> temp.outputStream().use { input.copyTo(it, 256 * 1024) } }
                ?: throw java.io.IOException("Couldn't read selected file")
            val mime = app.contentResolver.getType(uri) ?: MediaSaver.mimeFor(temp)
            val saved = MediaSaver.save(app, temp, mime, true, site)
            val item = DownloadItem.fromJson(org.json.JSONObject()
                .put("id", UUID.randomUUID().toString()).put("url", known?.url ?: uri.toString())
                .put("title", known?.title ?: name).put("platform", site.name).put("status", "COMPLETED")
                .put("qualityLabel", "Imported").put("fileUri", saved.uri.toString())
                .put("filePath", saved.path).put("fileName", saved.displayName).put("mimeType", mime)
                .put("sizeBytes", saved.sizeBytes).put("createdAt", System.currentTimeMillis()).put("finishedAt", System.currentTimeMillis()))
            _items.update { listOf(item) + it }
            persist()
            prefs.edit().putString(uri.toString(), saved.uri.toString()).commit()
            return true
        } finally { dir.deleteRecursively() }
    }

    fun enqueue(info: MediaInfo, quality: QualityOption): String {
        val item = DownloadItem(
            id = UUID.randomUUID().toString(),
            url = info.url,
            title = info.title,
            uploader = info.uploader,
            thumbnail = info.thumbnail,
            platform = info.platform,
            qualityLabel = if (quality.audioOnly) "Audio (M4A)" else quality.label,
            heightCap = quality.heightCap,
            audioOnly = quality.audioOnly,
            expectedStreams = if (!quality.audioOnly && quality.needsMerge) 2 else 1,
            extractorArgs = info.extractorArgs,
            status = DownloadStatus.QUEUED,
            progress = -1f,
            etaSeconds = -1,
            statusDetail = null,
            error = null,
            fileUri = null,
            filePath = null,
            fileName = null,
            mimeType = null,
            sizeBytes = 0,
            createdAt = System.currentTimeMillis(),
            finishedAt = 0,
        )
        _items.update { listOf(item) + it }
        persist()
        DownloadService.start(app)
        pump()
        return item.id
    }

    fun retry(id: String) {
        val item = find(id) ?: return
        if (item.status != DownloadStatus.FAILED && item.status != DownloadStatus.CANCELED) return
        if (jobs.containsKey(id)) {
            scope.launch { jobs[id]?.join(); retry(id) }
            return
        }
        update(id) {
            it.copy(
                status = DownloadStatus.QUEUED, progress = -1f, etaSeconds = -1, error = null,
                statusDetail = null, createdAt = System.currentTimeMillis(), finishedAt = 0,
            )
        }
        persist()
        DownloadService.start(app)
        pump()
    }

    fun cancel(id: String) {
        val item = find(id) ?: return
        if (!item.status.isPending) return
        val wasActive = item.status.isActive
        update(id) {
            it.copy(
                status = DownloadStatus.CANCELED, progress = -1f, etaSeconds = -1,
                statusDetail = null, error = null, finishedAt = System.currentTimeMillis(),
            )
        }
        persist()
        if (wasActive) {
            val processId = processIds[id]
            if (processId != null) scope.launch(Dispatchers.IO) {
                runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }
            }
        }
    }

    fun cancelAll() {
        _items.value.filter { it.status.isPending }.forEach { cancel(it.id) }
    }

    fun remove(id: String, deleteFile: Boolean) {
        val item = find(id) ?: return
        if (item.status.isPending) cancel(id)
        if (deleteFile && item.status == DownloadStatus.COMPLETED) {
            scope.launch(Dispatchers.IO) { MediaSaver.delete(app, item.fileUri, item.filePath) }
        }
        _items.update { list -> list.filterNot { it.id == id } }
        persist()
    }

    fun clearFinished() {
        _items.update { list -> list.filter { it.status.isPending } }
        persist()
    }

    // ---------------------------------------------------------------- scheduling

    private fun pump() {
        synchronized(lock) {
            val max = settings.maxConcurrent.value
            var active = jobs.size
            val queued = _items.value.filter { it.status == DownloadStatus.QUEUED }.sortedBy { it.createdAt }
            for (item in queued) {
                if (active >= max) break
                update(item.id) { it.copy(status = DownloadStatus.DOWNLOADING, progress = -1f, etaSeconds = -1) }
                active++
                jobs[item.id] = scope.launch(Dispatchers.IO, start = kotlinx.coroutines.CoroutineStart.LAZY) {
                    try {
                        run(item.id)
                    } finally {
                        jobs.remove(item.id)
                        pump()
                    }
                }
                jobs[item.id]?.start()
            }
        }
    }

    private suspend fun run(id: String) {
        val item = find(id) ?: return
        val processId = "$id-${UUID.randomUUID()}"
        processIds[id] = processId
        val tmpDir = File(app.cacheDir, "dl/$id")
        val cookieFile = cookies.tempCopy(item.platform)
        try {
            migrateStorage()
            engine.ensureReady()
            if (settings.wifiOnly.value && isMetered()) {
                throw DownloaderException(
                    "Wi-Fi only is turned on and you're on mobile data. Connect to Wi-Fi (or change the setting) and retry.",
                )
            }
            if (find(id)?.status == DownloadStatus.CANCELED) return
            if (!tmpDir.exists()) tmpDir.mkdirs()

            val request = buildRequest(item, tmpDir, cookieFile)
            val totalStreams = item.expectedStreams.coerceAtLeast(1)
            var streamIdx = 0
            var best = 0f
            var lastEmit = 0L
            var lastSpeed = -1L

            YoutubeDL.getInstance().execute(request, processId, false) { _, eta, line ->
                if (line.startsWith("[download] Destination:")) {
                    streamIdx++
                } else if (isPostProcessing(line)) {
                    update(id) {
                        if (it.status == DownloadStatus.CANCELED) it else it.copy(
                            status = DownloadStatus.PROCESSING, progress = -1f, etaSeconds = -1,
                            speedBytes = -1,
                            statusDetail = if (line.startsWith("[Merger]")) "Merging video & audio…" else "Processing…",
                        )
                    }
                } else {
                    Format.parseSpeed(line)?.let { lastSpeed = it }
                    val m = PERCENT.find(line)
                    val pct = m?.groupValues?.get(1)?.toFloatOrNull()
                    if (pct != null) {
                        val idx = (streamIdx - 1).coerceIn(0, totalStreams - 1)
                        best = max(best, ((idx + pct / 100f) / totalStreams).coerceIn(0f, 1f))
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastEmit >= 400 || best >= 0.999f) {
                            lastEmit = now
                            val progress = best
                            val speed = lastSpeed
                            update(id) {
                                if (it.status == DownloadStatus.CANCELED) it
                                else it.copy(
                                    status = DownloadStatus.DOWNLOADING, progress = progress,
                                    etaSeconds = eta, statusDetail = null, speedBytes = speed,
                                )
                            }
                        }
                    }
                }
            }

            if (find(id)?.status != DownloadStatus.DOWNLOADING && find(id)?.status != DownloadStatus.PROCESSING) {
                return // cancelled while finishing
            }
            update(id) { it.copy(status = DownloadStatus.PROCESSING, progress = -1f, statusDetail = "Saving to Downloads…") }

            val output = findOutput(tmpDir, item.audioOnly)
                ?: throw DownloaderException("The download finished but no output file was produced.")
            val mime = MediaSaver.mimeFor(output)
            val saved = MediaSaver.save(app, output, mime, settings.useSubfolder.value, item.platform)

            if (find(id)?.status == DownloadStatus.CANCELED || find(id) == null) {
                MediaSaver.delete(app, saved.uri.toString(), saved.path)
                return
            }
            update(id) {
                it.copy(
                    status = DownloadStatus.COMPLETED, progress = 1f, etaSeconds = -1, statusDetail = null,
                    error = null, fileUri = saved.uri.toString(), filePath = saved.path,
                    fileName = saved.displayName, mimeType = mime, sizeBytes = saved.sizeBytes,
                    finishedAt = System.currentTimeMillis(),
                )
            }
            persist()
            find(id)?.let { Notifications.completed(app, it) }
        } catch (_: YoutubeDL.CanceledException) {
            // State was already set to CANCELED by cancel().
        } catch (e: CancellationException) {
            throw e
        } catch (e: DownloaderException) {
            fail(id, e.userMessage)
        } catch (e: Throwable) {
            fail(id, ErrorMapper.map(e.message, item.platform, cookies.has(item.platform)))
        } finally {
            processIds.remove(id, processId)
            cookieFile?.delete()
            tmpDir.deleteRecursively()
        }
    }

    private fun buildRequest(item: DownloadItem, dir: File, cookieFile: File?): YoutubeDLRequest =
        YoutubeDLRequest(item.url).apply {
            addOption("--no-playlist")
            addOption("--playlist-items", "1")
            addOption("--newline")
            addOption("--no-mtime")
            addOption("--windows-filenames")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--socket-timeout", "30")
            addOption("--concurrent-fragments", settings.fragments.value.toString())
            addOption("--retry-sleep", "http:exp=1:10")
            addOption("--retry-sleep", "fragment:exp=1:10")
            addOption("--buffer-size", "64K")
            addOption("-o", "${dir.absolutePath}/%(title).100B.%(ext)s")
            if (item.audioOnly) {
                addOption("-f", FormatSelector.AUDIO_FORMAT)
                addOption("-x")
                addOption("--audio-format", "m4a")
            } else {
                addOption("-f", FormatSelector.VIDEO_FORMAT)
                addOption("-S", FormatSelector.sortOrder(item.heightCap))
                addOption("--merge-output-format", "mp4")
                addOption("--remux-video", "mp4")
            }
            if (cookieFile != null) addOption("--cookies", cookieFile.absolutePath)
            item.extractorArgs?.let { addOption("--extractor-args", it) }
        }

    private fun findOutput(dir: File, audioOnly: Boolean): File? {
        val media = dir.listFiles { f -> f.isFile && f.extension.lowercase() in MEDIA_EXTENSIONS }
            ?.toList().orEmpty()
        val preferred = if (audioOnly) "m4a" else "mp4"
        return media.filter { it.extension.equals(preferred, true) }.maxByOrNull { it.length() }
            ?: media.maxByOrNull { it.length() }
    }

    private fun isPostProcessing(line: String): Boolean =
        line.startsWith("[Merger]") || line.startsWith("[VideoRemuxer]") || line.startsWith("[ExtractAudio]") ||
            line.startsWith("[Fixup") || line.startsWith("[ffmpeg]") || line.startsWith("[MoveFiles]")

    private fun isMetered(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return cm.isActiveNetworkMetered
    }

    // ---------------------------------------------------------------- state helpers

    private fun fail(id: String, message: String) {
        if (find(id)?.status == DownloadStatus.CANCELED) return
        update(id) {
            it.copy(
                status = DownloadStatus.FAILED, progress = -1f, etaSeconds = -1, statusDetail = null,
                error = message, finishedAt = System.currentTimeMillis(),
            )
        }
        persist()
        find(id)?.let { Notifications.failed(app, it) }
    }

    private fun find(id: String): DownloadItem? = _items.value.firstOrNull { it.id == id }

    private fun update(id: String, transform: (DownloadItem) -> DownloadItem) {
        _items.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun persist() {
        scope.launch(Dispatchers.IO) { saveCurrent() }
    }

    private companion object {
        val PERCENT = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
        val MEDIA_EXTENSIONS = setOf("mp4", "m4a", "mp3", "m4v", "mov", "webm", "mkv", "aac", "opus", "ogg")
    }
}
