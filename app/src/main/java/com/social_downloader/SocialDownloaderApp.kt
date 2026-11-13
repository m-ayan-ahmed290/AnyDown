package com.social_downloader

import android.app.Application
import com.social_downloader.data.CookieStore
import com.social_downloader.data.SettingsStore
import com.social_downloader.download.DownloadManager
import com.social_downloader.download.Notifications
import com.social_downloader.engine.YtDlpEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Manual dependency container – small enough that a DI framework would be overkill. */
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val cookies = CookieStore(app)
    val engine = YtDlpEngine(app, cookies) { settings.updateChannel.value }
    val downloads = DownloadManager(app, engine, settings, cookies)
}

class SocialDownloaderApp : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannels(this)
        // Unpack Python/FFmpeg in the background so the first "Fetch" is instant.
        appScope.launch { runCatching { container.engine.ensureReady() } }
    }
}
