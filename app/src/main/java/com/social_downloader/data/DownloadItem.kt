package com.social_downloader.data

import com.social_downloader.engine.Platform
import org.json.JSONObject

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELED;

    /** Occupies a download slot / shows in the Queue tab. */
    val isActive: Boolean get() = this == DOWNLOADING || this == PROCESSING
    val isPending: Boolean get() = this == QUEUED || isActive
}

data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val platform: Platform,
    val qualityLabel: String,
    val heightCap: Int?,
    val audioOnly: Boolean,
    /** 2 when video and audio arrive as separate streams, otherwise 1. Only used to scale the progress bar. */
    val expectedStreams: Int,
    val extractorArgs: String?,
    val status: DownloadStatus,
    /** 0..1, or a negative value when indeterminate. */
    val progress: Float,
    val etaSeconds: Long,
    val statusDetail: String?,
    val error: String?,
    val fileUri: String?,
    val filePath: String?,
    val fileName: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val createdAt: Long,
    val finishedAt: Long,
    /** Current speed in bytes/s while downloading; -1 when unknown. Not persisted. */
    val speedBytes: Long = -1,
    val audioFormat: com.social_downloader.engine.AudioFormat = com.social_downloader.engine.AudioFormat.M4A,
    val audioBitrateKbps: Int? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("url", url)
        put("title", title)
        put("uploader", uploader ?: JSONObject.NULL)
        put("thumbnail", thumbnail ?: JSONObject.NULL)
        put("platform", platform.name)
        put("qualityLabel", qualityLabel)
        put("heightCap", heightCap ?: JSONObject.NULL)
        put("audioOnly", audioOnly)
        put("audioFormat", audioFormat.name)
        put("audioBitrateKbps", audioBitrateKbps ?: JSONObject.NULL)
        put("expectedStreams", expectedStreams)
        put("extractorArgs", extractorArgs ?: JSONObject.NULL)
        put("status", status.name)
        put("error", error ?: JSONObject.NULL)
        put("fileUri", fileUri ?: JSONObject.NULL)
        put("filePath", filePath ?: JSONObject.NULL)
        put("fileName", fileName ?: JSONObject.NULL)
        put("mimeType", mimeType ?: JSONObject.NULL)
        put("sizeBytes", sizeBytes)
        put("createdAt", createdAt)
        put("finishedAt", finishedAt)
    }

    companion object {
        private fun JSONObject.str(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

        fun fromJson(o: JSONObject): DownloadItem = DownloadItem(
            id = o.getString("id"),
            url = o.getString("url"),
            title = o.optString("title", "Video"),
            uploader = o.str("uploader"),
            thumbnail = o.str("thumbnail"),
            platform = runCatching { Platform.valueOf(o.getString("platform")) }.getOrDefault(Platform.OTHER),
            qualityLabel = o.optString("qualityLabel", ""),
            heightCap = if (o.isNull("heightCap")) null else o.optInt("heightCap"),
            audioOnly = o.optBoolean("audioOnly", false),
            audioFormat = runCatching { com.social_downloader.engine.AudioFormat.valueOf(o.optString("audioFormat", "M4A")) }.getOrDefault(com.social_downloader.engine.AudioFormat.M4A),
            audioBitrateKbps = if (o.isNull("audioBitrateKbps") || !o.has("audioBitrateKbps")) null else o.optInt("audioBitrateKbps"),
            expectedStreams = o.optInt("expectedStreams", 1),
            extractorArgs = o.str("extractorArgs"),
            status = runCatching { DownloadStatus.valueOf(o.getString("status")) }
                .getOrDefault(DownloadStatus.FAILED),
            progress = if (o.optString("status") == DownloadStatus.COMPLETED.name) 1f else -1f,
            etaSeconds = -1,
            statusDetail = null,
            error = o.str("error"),
            fileUri = o.str("fileUri"),
            filePath = o.str("filePath"),
            fileName = o.str("fileName"),
            mimeType = o.str("mimeType"),
            sizeBytes = o.optLong("sizeBytes", 0),
            createdAt = o.optLong("createdAt", 0),
            finishedAt = o.optLong("finishedAt", 0),
        )
    }
}
