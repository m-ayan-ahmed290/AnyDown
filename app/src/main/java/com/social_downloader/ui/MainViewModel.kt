package com.social_downloader.ui

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.social_downloader.SocialDownloaderApp
import com.social_downloader.data.DownloadItem
import com.social_downloader.download.MediaSaver
import com.social_downloader.engine.DownloaderException
import com.social_downloader.engine.MediaInfo
import com.social_downloader.engine.CookieFile
import com.social_downloader.engine.Platform
import com.social_downloader.engine.QualityOption
import com.social_downloader.engine.SiteLogin
import com.social_downloader.engine.UrlTools
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Success(val info: MediaInfo) : FetchState
    data class Error(val message: String) : FetchState
}

enum class Tab(val label: String) {
    HOME("Download"), QUEUE("Queue"), LIBRARY("Library"), HISTORY("History"), SETTINGS("Settings")
}

data class UiMessage(val text: String, val goToQueue: Boolean = false)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val container = (app as SocialDownloaderApp).container
    val settings = container.settings
    val engine = container.engine
    val downloads = container.downloads
    val cookies = container.cookies

    var tab by mutableStateOf(Tab.HOME)
    var input by mutableStateOf("")
        private set
    var fetchState by mutableStateOf<FetchState>(FetchState.Idle)
        private set
    var selectedQuality by mutableStateOf<QualityOption?>(null)
        private set
    /** Non-null while the in-app sign-in screen is showing. */
    var loginSite by mutableStateOf<SiteLogin?>(null)
    var updating by mutableStateOf(false)
        private set
    var updateResult by mutableStateOf<String?>(null)
        private set

    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages.asSharedFlow()

    private var fetchJob: Job? = null

    val inputCheck: UrlTools.Check get() = UrlTools.check(input)

    fun say(text: String, goToQueue: Boolean = false) {
        _messages.tryEmit(UiMessage(text, goToQueue))
    }

    fun onInputChange(value: String) {
        input = value
        if (fetchState !is FetchState.Idle) {
            fetchJob?.cancel()
            fetchState = FetchState.Idle
            selectedQuality = null
        }
    }

    fun clearInput() = onInputChange("")

    /** Clipboard paste: extract the link and fetch straight away. */
    fun onPasted(text: String) {
        val url = UrlTools.extractUrl(text) ?: text.trim()
        onInputChange(url)
        if (UrlTools.check(url).isValid) fetch()
    }

    /** Android Share sheet → "Social Downloader". */
    fun onSharedText(text: String?) {
        val url = text?.let { UrlTools.extractUrl(it) }
        tab = Tab.HOME
        if (url == null) {
            say("No link found in the shared text.")
            return
        }
        onInputChange(url)
        fetch()
    }

    fun fetch() {
        val check = UrlTools.check(input)
        val url = check.url
        if (url == null) {
            fetchState = FetchState.Error(check.error ?: "Paste a video link first.")
            return
        }
        fetchJob?.cancel()
        fetchState = FetchState.Loading
        selectedQuality = null
        fetchJob = viewModelScope.launch {
            try {
                val info = engine.fetchInfo(url)
                fetchState = FetchState.Success(info)
                selectedQuality = info.qualities.firstOrNull()
            } catch (e: CancellationException) {
                throw e
            } catch (e: DownloaderException) {
                fetchState = FetchState.Error(e.userMessage)
            } catch (e: Throwable) {
                fetchState = FetchState.Error("Something went wrong: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    fun selectQuality(q: QualityOption) {
        selectedQuality = q
    }

    fun download() {
        val state = fetchState as? FetchState.Success ?: return
        val quality = selectedQuality ?: return
        downloads.enqueue(state.info, quality)
        say("Added to queue · ${if (quality.audioOnly) "Audio" else quality.label}", goToQueue = true)
        input = ""
        fetchState = FetchState.Idle
        selectedQuality = null
    }

    fun openFile(context: Context, item: DownloadItem) {
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) { MediaSaver.exists(context, item.fileUri, item.filePath) }
            if (!exists || item.fileUri == null) {
                say("File not found. It may have been moved or deleted.")
                return@launch
            }
            val intent = Intent(Intent.ACTION_VIEW)
                .setDataAndType(Uri.parse(item.fileUri), item.mimeType ?: "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                say("No app found to play this file.")
            }
        }
    }

    fun shareFile(context: Context, item: DownloadItem) {
        viewModelScope.launch {
            val exists = withContext(Dispatchers.IO) { MediaSaver.exists(context, item.fileUri, item.filePath) }
            if (!exists || item.fileUri == null) {
                say("File not found. It may have been moved or deleted.")
                return@launch
            }
            val send = Intent(Intent.ACTION_SEND)
                .setType(item.mimeType ?: "video/mp4")
                .putExtra(Intent.EXTRA_STREAM, Uri.parse(item.fileUri))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            try {
                context.startActivity(Intent.createChooser(send, "Share video"))
            } catch (_: ActivityNotFoundException) {
                say("No app available to share with.")
            }
        }
    }

    fun saveCookies(platform: Platform, text: String) {
        cookies.save(platform, text)
        say("Signed in to ${platform.label}. Try your link again.")
    }

    fun signOut(platform: Platform) {
        cookies.clear(platform)
        say("Signed out of ${platform.label}.")
    }

    /** Import a Netscape-format cookies.txt exported from a browser, for one site. */
    fun importCookies(context: Context, platform: Platform, uri: Uri) {
        viewModelScope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val limit = 2 * 1024 * 1024
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > limit) return@use null
                            out.write(buf, 0, n)
                        }
                        String(out.toByteArray(), Charsets.UTF_8)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            if (text == null || !CookieFile.looksValid(text)) {
                say("That doesn't look like a valid cookies.txt (Netscape format, under 2 MB).")
            } else {
                saveCookies(platform, text)
            }
        }
    }

    fun updateEngine() {
        if (updating) return
        updating = true
        updateResult = null
        viewModelScope.launch {
            updateResult = try {
                engine.updateEngine(settings.updateChannel.value)
            } catch (e: DownloaderException) {
                e.userMessage
            } catch (e: Throwable) {
                "Update failed: ${e.message}"
            }
            updating = false
        }
    }
}
