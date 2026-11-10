package com.social_downloader.engine

/** Builds/validates Netscape-format cookie files, the format yt-dlp's `--cookies` expects. */
object CookieFile {
    private const val HEADER = "# Netscape HTTP Cookie File"

    /**
     * @param sources map of cookie domain (".instagram.com") to the raw `Cookie` header string
     *                returned by Android's CookieManager ("a=1; b=2").
     * @return file text, or null if there were no cookies at all.
     */
    fun build(sources: Map<String, String?>, expiryEpochSec: Long): String? {
        val lines = mutableListOf<String>()
        for ((domain, header) in sources) {
            if (header.isNullOrBlank()) continue
            for (pair in header.split(';')) {
                val idx = pair.indexOf('=')
                if (idx <= 0) continue
                val name = clean(pair.substring(0, idx).trim())
                val value = clean(pair.substring(idx + 1).trim())
                if (name.isEmpty()) continue
                lines += listOf(domain, "TRUE", "/", "TRUE", expiryEpochSec.toString(), name, value).joinToString("\t")
            }
        }
        if (lines.isEmpty()) return null
        return (listOf(HEADER, "") + lines).joinToString("\n") + "\n"
    }

    /** True if the text has at least one well-formed cookie line (7 tab-separated fields). */
    fun looksValid(text: String): Boolean = text.lineSequence().any { line ->
        val l = line.removePrefix("#HttpOnly_")
        !l.startsWith("#") && l.split('\t').size >= 7
    }

    private fun clean(s: String) = s.filter { it != '\t' && it != '\n' && it != '\r' }
}
