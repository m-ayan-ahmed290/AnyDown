package com.social_downloader.util

import java.text.DateFormat
import java.util.Date
import java.util.Locale

object Format {
    private val SPEED = Regex("""\bat\s+([\d.]+)\s*([KMGT]?)i?B/s""")

    /** Parses yt-dlp's "at 2.50MiB/s" into bytes per second, or null if not present. */
    fun parseSpeed(line: String): Long? {
        val m = SPEED.find(line) ?: return null
        val v = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult = when (m.groupValues[2]) {
            "K" -> 1024.0
            "M" -> 1024.0 * 1024
            "G" -> 1024.0 * 1024 * 1024
            "T" -> 1024.0 * 1024 * 1024 * 1024
            else -> 1.0
        }
        return (v * mult).toLong()
    }

    fun speed(bytesPerSec: Long): String = if (bytesPerSec <= 0) "" else bytes(bytesPerSec) + "/s"

    fun duration(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun bytes(bytes: Long): String {
        if (bytes <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB")
        var value = bytes.toDouble()
        var i = 0
        while (value >= 1024 && i < units.lastIndex) {
            value /= 1024
            i++
        }
        return if (i == 0) "${bytes} B" else String.format(Locale.US, "%.1f %s", value, units[i])
    }

    fun eta(seconds: Long): String = when {
        seconds < 0 -> ""
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }

    fun date(epochMs: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
}
