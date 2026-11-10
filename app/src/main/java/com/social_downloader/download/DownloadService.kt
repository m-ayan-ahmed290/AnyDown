package com.social_downloader.download

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.social_downloader.SocialDownloaderApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service whose only job is to keep the process alive (and show a notification)
 * while the [DownloadManager] has queued or running work. It stops itself when idle.
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watcher: Job? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val manager = (application as SocialDownloaderApp).container.downloads

        if (intent?.action == ACTION_CANCEL_ALL) manager.cancelAll()

        ServiceCompat.startForeground(
            this,
            Notifications.ID_FOREGROUND,
            Notifications.foreground(this, manager.items.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )

        if (watcher?.isActive != true) {
            watcher = scope.launch {
                manager.items.collect { items ->
                    if (items.none { it.status.isPending }) {
                        ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf(lastStartId)
                    } else {
                        NotificationManagerCompat.from(this@DownloadService).apply {
                            if (areNotificationsEnabled()) {
                                @Suppress("MissingPermission")
                                notify(Notifications.ID_FOREGROUND, Notifications.foreground(this@DownloadService, items))
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL_ALL = "com.social_downloader.action.CANCEL_ALL"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
            } catch (_: Exception) {
                // Background-start restrictions: downloads still run in-process, just without the service.
            }
        }
    }
}
