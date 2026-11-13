package com.social_downloader.engine

/** A site where the user may sign in with their *own* account so yt-dlp can fetch content they can already see. */
data class SiteLogin(
    val platform: Platform,
    val loginUrl: String,
    /** (url to read cookies from, cookie-file domain) pairs. */
    val cookieSources: List<Pair<String, String>>,
    val embeddedLogin: Boolean = true,
) {
    val label: String get() = platform.label

    companion object {
        // Google sign-in stays in the browser; YouTube supports cookie import only.
        val all = listOf(
            SiteLogin(Platform.YOUTUBE, "https://www.youtube.com/", listOf("https://www.youtube.com" to ".youtube.com", "https://accounts.google.com" to ".google.com"), embeddedLogin = false),
            SiteLogin(
                Platform.INSTAGRAM, "https://www.instagram.com/accounts/login/",
                listOf("https://www.instagram.com" to ".instagram.com"),
            ),
            SiteLogin(
                Platform.X, "https://x.com/i/flow/login",
                listOf("https://x.com" to ".x.com", "https://twitter.com" to ".twitter.com"),
            ),
            SiteLogin(
                Platform.FACEBOOK, "https://www.facebook.com/login/",
                listOf("https://www.facebook.com" to ".facebook.com"),
            ),
            SiteLogin(
                Platform.THREADS, "https://www.threads.net/login",
                listOf("https://www.threads.net" to ".threads.net", "https://www.threads.com" to ".threads.com", "https://www.instagram.com" to ".instagram.com"),
            ),
            SiteLogin(
                Platform.TIKTOK, "https://www.tiktok.com/login",
                listOf("https://www.tiktok.com" to ".tiktok.com"),
            ),
            SiteLogin(
                Platform.REDDIT, "https://www.reddit.com/login/",
                listOf("https://www.reddit.com" to ".reddit.com"),
            ),
        )

        val adult: List<SiteLogin> get() = Platform.named.filter { it.adult }.map { platform ->
            val host = platform.hosts.first()
            SiteLogin(platform, "https://www.$host/", platform.hosts.map { "https://www.$it" to ".$it" })
        }

        fun forPlatform(p: Platform): SiteLogin? = (all + adult).firstOrNull { it.platform == p }
    }
}
