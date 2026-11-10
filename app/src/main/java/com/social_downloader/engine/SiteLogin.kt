package com.social_downloader.engine

/** A site where the user may sign in with their *own* account so yt-dlp can fetch content they can already see. */
data class SiteLogin(
    val platform: Platform,
    val loginUrl: String,
    /** (url to read cookies from, cookie-file domain) pairs. */
    val cookieSources: List<Pair<String, String>>,
) {
    val label: String get() = platform.label

    companion object {
        // YouTube/Google is deliberately absent: Google blocks WebView sign-in and automated
        // use of a Google session can get the account flagged.
        val all = listOf(
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
                listOf("https://www.threads.net" to ".threads.net", "https://www.threads.com" to ".threads.com"),
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

        fun forPlatform(p: Platform): SiteLogin? = all.firstOrNull { it.platform == p }
    }
}
