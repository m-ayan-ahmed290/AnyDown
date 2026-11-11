package com.social_downloader.engine

/** An error whose message is safe to show directly to the user. */
class DownloaderException(val userMessage: String, cause: Throwable? = null) : Exception(userMessage, cause)

/**
 * One selectable output. Resolution is the *short side* of the frame (1080p works for both
 * landscape and vertical videos), which matches yt-dlp's `res` sort field.
 */
data class QualityOption(
    val id: String,
    val label: String,
    val detail: String?,
    val heightCap: Int?,
    val audioOnly: Boolean,
    /** True when the best matching video stream has no audio and must be merged with an audio stream. */
    val needsMerge: Boolean,
    val estimatedBytes: Long?,
    val audioFormat: AudioFormat = AudioFormat.M4A,
    val audioBitrateKbps: Int? = null,
)

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnail: String?,
    val platform: Platform,
    val extractor: String?,
    val qualities: List<QualityOption>,
    /** Non-null when a fallback YouTube client had to be used to read this video. */
    val extractorArgs: String?,
)
