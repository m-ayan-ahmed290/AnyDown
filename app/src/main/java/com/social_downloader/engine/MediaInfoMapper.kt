package com.social_downloader.engine

import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo

internal object MediaInfoMapper {

    fun map(url: String, info: VideoInfo, extractorArgs: String?): MediaInfo {
        val duration = info.duration.toLong().coerceAtLeast(0L)
        val formats = info.formats.orEmpty().map { it.toLite() }
        val qualities = QualityBuilder.build(formats, duration)
        return MediaInfo(
            url = url,
            title = (info.title ?: info.fulltitle)?.takeIf { it.isNotBlank() } ?: "Untitled video",
            uploader = info.uploader?.takeIf { it.isNotBlank() },
            durationSec = duration,
            thumbnail = info.thumbnail?.takeIf { it.startsWith("https://") || it.startsWith("http://") },
            platform = UrlTools.detect(url),
            extractor = info.extractor,
            qualities = qualities,
            extractorArgs = extractorArgs,
        )
    }

    private fun VideoFormat.toLite() = FormatLite(
        width = width,
        height = height,
        vcodec = vcodec,
        acodec = acodec,
        abr = abr,
        tbr = tbr,
        sizeBytes = if (fileSize > 0) fileSize else fileSizeApproximate,
        ext = ext,
    )
}
