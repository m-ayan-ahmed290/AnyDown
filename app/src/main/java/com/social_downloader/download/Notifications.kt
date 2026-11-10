package com.social_downloader.download

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.social_downloader.MainActivity
import com.social_downloader.R
import com.social_downloader.data.DownloadItem
import com.social_downloader.data.DownloadStatus
import com.social_downloader.util.Format

object Notifications {
    private const val CHANNEL_PROGRESS = "downloads_progress"
    private const val CHANNEL_DONE = "downloads_done"
    const val ID_FOREGROUND = 1001

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Download progress", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows active downloads"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Download completed or failed"
            },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun foreground(context: Context, items: List<DownloadItem>): Notification {
        val pending = items.filter { it.status.isPending }
        val running = pending.filter { it.status.isActive }
        val builder = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                0, "Cancel all",
                PendingIntent.getService(
                    context, 1,
                    Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL_ALL),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )

        if (pending.size <= 1) {
            val item = pending.firstOrNull()
            builder.setContentTitle(item?.title ?: "AnyDown")
            val processing = item?.status == DownloadStatus.PROCESSING
            val p = item?.progress ?: -1f
            if (item == null) {
                builder.setContentText("Starting…")
                builder.setProgress(0, 0, true)
            } else if (item.status == DownloadStatus.QUEUED) {
                builder.setContentText("Queued")
                builder.setProgress(0, 0, true)
            } else if (processing || p < 0f) {
                builder.setContentText(if (processing) (item.statusDetail ?: "Processing…") else "Starting…")
                builder.setProgress(0, 0, true)
            } else {
                builder.setContentText(
                    listOfNotNull("${(p * 100).toInt()}%", Format.speed(item.speedBytes).takeIf { it.isNotEmpty() }, item.qualityLabel)
                        .joinToString(" · "),
                )
                builder.setProgress(100, (p * 100).toInt(), false)
            }
        } else {
            builder.setContentTitle("Downloading ${pending.size} videos")
            builder.setContentText("${running.size} active · ${pending.size - running.size} queued")
            builder.setProgress(0, 0, true)
        }
        return builder.build()
    }

    @SuppressLint("MissingPermission")
    fun completed(context: Context, item: DownloadItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = item.fileUri?.let { uri ->
            PendingIntent.getActivity(
                context, item.id.hashCode(),
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(uri), item.mimeType ?: "video/mp4")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } ?: openApp(context)
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Download complete")
            .setContentText(item.fileName ?: item.title)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), n)
    }

    @SuppressLint("MissingPermission")
    fun failed(context: Context, item: DownloadItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_stat_download)
            .setContentTitle("Download failed")
            .setContentText(item.title)
            .setStyle(NotificationCompat.BigTextStyle().bigText("${item.title}\n${item.error ?: ""}"))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), n)
    }
}
