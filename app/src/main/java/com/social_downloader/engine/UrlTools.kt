package com.social_downloader.engine

/**
 * Link extraction + validation. Only http(s) links to public hosts are ever handed to yt-dlp,
 * which also prevents option-injection ("--exec ...") and local-network probing.
 */
object UrlTools {
    private val URL_REGEX = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val URL_PARTS = Regex(
        """^(https?)://(\[[0-9a-fA-F:.]+]|[^/?#:@\s]+)(?::(\d{1,5}))?(?:[/?#]\S*)?$""",
        RegexOption.IGNORE_CASE,
    )
    private val IPV4 = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val TRAILING = charArrayOf('.', ',', ')', ']', '}', '>', '!', ';', ':', '"', '\'')

    data class Check(val url: String?, val error: String?) {
        val isValid: Boolean get() = url != null
    }

    /** Pulls the first http(s) link out of arbitrary shared text ("Check this out! https://… via @app"). */
    fun extractUrl(text: String): String? =
        URL_REGEX.find(text)?.value?.trimEnd(*TRAILING)?.takeIf { it.isNotEmpty() }

    fun check(raw: String): Check {
        val input = (extractUrl(raw) ?: raw.trim())
        if (input.isEmpty()) return Check(null, null)

        var candidate = input
        if (!candidate.contains("://")) {
            if (candidate.any { it.isWhitespace() } || !candidate.contains('.')) {
                return Check(null, "Enter a full link, e.g. https://…")
            }
            candidate = "https://$candidate"
        }
        val match = URL_PARTS.matchEntire(candidate)
            ?: return Check(null, "That doesn't look like a valid http(s) link.")
        val host = match.groupValues[2].lowercase()
        if (isBlockedHost(host)) {
            return Check(null, "Local and private network addresses aren't allowed.")
        }
        if (!host.contains('.')) return Check(null, "That doesn't look like a valid link.")
        return Check(candidate, null)
    }

    fun hostOf(url: String): String? = URL_PARTS.matchEntire(url)?.groupValues?.get(2)?.lowercase()

    fun detect(url: String): Platform = Platform.fromHost(hostOf(url))

    /** Detects a platform from free-form input (works while the user is still typing). */
    fun detectFromInput(raw: String): Platform? {
        val check = check(raw)
        return check.url?.let { detect(it) }
    }

    private fun isBlockedHost(host: String): Boolean {
        if (host.startsWith("[")) return true // IPv6 literals are never legitimate media hosts
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") ||
            host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home.arpa")
        ) return true
        val m = IPV4.matchEntire(host) ?: return false
        val o = m.groupValues.drop(1).map { it.toInt() }
        if (o.any { it > 255 }) return true
        return o[0] == 0 || o[0] == 10 || o[0] == 127 ||
            (o[0] == 169 && o[1] == 254) ||
            (o[0] == 172 && o[1] in 16..31) ||
            (o[0] == 192 && o[1] == 168) ||
            (o[0] == 100 && o[1] in 64..127) ||
            o[0] >= 224
    }
}
