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

    fun forPlatform(text: String, platform: Platform, now: Long = System.currentTimeMillis() / 1000): String? {
        val lines = text.lineSequence().filter { raw ->
            val fields = raw.removePrefix("#HttpOnly_").split('\t')
            val domain = fields[0].trimStart('.').lowercase()
            val matches = platform.matches(domain) ||
                (platform == Platform.THREADS && Platform.INSTAGRAM.matches(domain)) ||
                (platform == Platform.YOUTUBE && (domain == "google.com" || domain.endsWith(".google.com")))
            if (raw.startsWith("#") && !raw.startsWith("#HttpOnly_")) false
            else fields.size == 7 && matches && fields[1] in listOf("TRUE", "FALSE") && fields[3] in listOf("TRUE", "FALSE") &&
                fields[4].toLongOrNull()?.let { it == 0L || it > now } == true && fields[5].isNotBlank()
        }.toList()
        return if (lines.isEmpty()) null else HEADER + "\n" + lines.joinToString("\n") + "\n"
    }

    fun hasSession(text: String, platform: Platform): Boolean {
        val names = when (platform) {
            Platform.INSTAGRAM, Platform.THREADS, Platform.TIKTOK -> setOf("sessionid", "sessionid_ss", "sid_tt")
            Platform.FACEBOOK -> setOf("c_user", "xs")
            Platform.REDDIT -> setOf("reddit_session", "token_v2")
            Platform.X -> setOf("auth_token")
            else -> return true // Site-specific validity is checked by the next download request.
        }
        return text.lineSequence().map { it.removePrefix("#HttpOnly_").split('\t') }
            .any { it.size == 7 && it[5] in names && it[6].isNotBlank() }
    }

    private fun clean(s: String) = s.filter { it != '\t' && it != '\n' && it != '\r' }
}
