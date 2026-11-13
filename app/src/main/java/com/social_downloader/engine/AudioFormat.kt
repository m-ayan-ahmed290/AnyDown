package com.social_downloader.engine

enum class AudioFormat(val label: String, val extension: String, val encoder: String, val bitrates: List<Int>, val defaultBitrate: Int?) {
    M4A("M4A", "m4a", "aac", listOf(96, 128, 192, 256), 192),
    MP3("MP3", "mp3", "libmp3lame", listOf(96, 128, 192, 256, 320), 192),
    OPUS("Opus", "opus", "libopus", listOf(64, 96, 128, 160, 192), 128),
    FLAC("FLAC", "flac", "flac", emptyList(), null),
    WAV("WAV", "wav", "pcm_s16le", emptyList(), null);

    val lossless: Boolean get() = bitrates.isEmpty()
    fun validBitrate(value: Int?): Int? = if (lossless) null else value?.takeIf { it in bitrates } ?: defaultBitrate
    fun qualityLabel(bitrate: Int?) = if (lossless) "Lossless output" else bitrate?.let { "$it kbps" } ?: "Best available"

    fun outputFile(staging: java.io.File, input: java.io.File): java.io.File =
        java.io.File(java.io.File(staging, "converted"), "${input.nameWithoutExtension}.$extension")

    fun ffmpegArguments(input: String, output: String, bitrate: Int?): List<String> =
        listOf("-nostdin", "-hide_banner", "-y", "-i", input, "-map", "0:a:0", "-vn", "-c:a", encoder) +
            (if (lossless) emptyList() else listOf("-b:a", "${validBitrate(bitrate)}k")) + listOf(output)
}
