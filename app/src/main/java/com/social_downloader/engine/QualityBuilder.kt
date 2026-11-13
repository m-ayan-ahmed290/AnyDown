package com.social_downloader.engine

import kotlin.math.max
import kotlin.math.min

/** Library-independent view of one yt-dlp format entry (keeps this logic unit-testable). */
data class FormatLite(
    val width: Int,
    val height: Int,
    val vcodec: String?,
    val acodec: String?,
    val abr: Int,
    val tbr: Int,
    val sizeBytes: Long,
    val ext: String?,
)

object QualityBuilder {

    fun build(formats: List<FormatLite>, durationSec: Long): List<QualityOption> {
        val video = formats.filter { hasVideo(it) }
        val audioOnly = formats.filter { it.vcodec == "none" && it.acodec != "none" }
        val bestAudio = audioOnly.maxWithOrNull(
            compareBy<FormatLite>({ if (it.ext == "m4a") 1 else 0 }, { it.abr }, { it.tbr }),
        )
        val audioBytes = bestAudio?.let { sizeOf(it, durationSec) }

        val bySide = video.groupBy { side(it) }.filterKeys { it > 0 }
        val options = mutableListOf<QualityOption>()

        for (side in bySide.keys.sortedDescending().take(10)) {
            val rep = bySide.getValue(side).sortedWith(
                compareByDescending<FormatLite> { isH264(it) }.thenByDescending { it.tbr },
            ).first()
            val needsMerge = rep.acodec == "none"
            var bytes = sizeOf(rep, durationSec)
            if (needsMerge && bytes != null && audioBytes != null) bytes += audioBytes
            options += QualityOption(
                id = "v$side",
                label = "${side}p",
                detail = badge(side),
                heightCap = side,
                audioOnly = false,
                needsMerge = needsMerge,
                estimatedBytes = bytes,
            )
        }

        if (options.isEmpty()) {
            // Heights unknown (some generic extractors) – let yt-dlp choose the best.
            val needsMerge = video.any { it.acodec == "none" } && audioOnly.isNotEmpty()
            options += QualityOption("best", "Best available", null, null, false, needsMerge, null)
        }

        val combinedBytes = bestAudio?.let { audioBytes }
            ?: formats.mapNotNull { sizeOf(it, durationSec) }.minOrNull()
        options += QualityOption("audio", "Audio only", null, null, true, false, combinedBytes)
        return options
    }

    private fun hasVideo(f: FormatLite) = f.vcodec != "none" && (f.height > 0 || f.vcodec != null)

    private fun isH264(f: FormatLite): Boolean {
        val v = f.vcodec?.lowercase() ?: return false
        return v.startsWith("avc") || v.startsWith("h264")
    }

    private fun side(f: FormatLite): Int = when {
        f.width > 0 && f.height > 0 -> min(f.width, f.height)
        else -> max(f.width, f.height)
    }

    private fun sizeOf(f: FormatLite, durationSec: Long): Long? = when {
        f.sizeBytes > 0 -> f.sizeBytes
        f.tbr > 0 && durationSec > 0 -> f.tbr * 1000L / 8L * durationSec
        else -> null
    }

    private fun badge(side: Int): String? = when {
        side >= 4320 -> "8K"
        side >= 2160 -> "4K"
        side >= 1440 -> "2K"
        side >= 1080 -> "Full HD"
        side >= 720 -> "HD"
        else -> null
    }
}
