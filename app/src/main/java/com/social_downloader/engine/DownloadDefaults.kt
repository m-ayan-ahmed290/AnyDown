package com.social_downloader.engine

import com.social_downloader.data.Experience

object DownloadDefaults {
    fun choose(info: MediaInfo, settings: Experience): QualityOption? {
        if (settings.audioDefault) {
            val format = runCatching { AudioFormat.valueOf(settings.audioFormat) }.getOrDefault(AudioFormat.M4A)
            return info.qualities.firstOrNull { it.audioOnly }?.copy(audioFormat = format,
                audioBitrateKbps = format.validBitrate(settings.audioBitrate), estimatedBytes = null)
        }
        val video = info.qualities.filter { !it.audioOnly }
        return (if (settings.videoHeight > 0) video.filter { (it.heightCap ?: Int.MAX_VALUE) <= settings.videoHeight }.maxByOrNull { it.heightCap ?: 0 } else null)
            ?: (if(settings.videoHeight > 0) video.minByOrNull { it.heightCap ?: Int.MAX_VALUE } else video.firstOrNull()) ?: info.qualities.firstOrNull()
    }
    fun links(text: String): List<String> = text.lineSequence().flatMap { line ->
        val matches = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE).findAll(line).map { it.value }.toList()
        (if (matches.isEmpty()) listOf(line.trim()) else matches).asSequence()
    }.mapNotNull { UrlTools.check(it.trimEnd('.', ',', ')', ']', ';')).url }.distinct().take(25).toList()
}
