package com.social_downloader.download

import com.social_downloader.data.DownloadItem
import com.social_downloader.data.DownloadStatus

object QueueMetrics {
    fun speed(items: List<DownloadItem>): Long = items.filter { it.status == DownloadStatus.DOWNLOADING }
        .sumOf { it.speedBytes.coerceAtLeast(0) }
}
