package com.social_downloader.engine

/** Sites we recognise by hostname. Anything else is still attempted through yt-dlp's 1000+ extractors. */
enum class Platform(val label: String, val brandColor: Long, private val hosts: List<String>) {
    YOUTUBE("YouTube", 0xFFFF0033, listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    X("X (Twitter)", 0xFF1D9BF0, listOf("twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com")),
    REDDIT("Reddit", 0xFFFF4500, listOf("reddit.com", "redd.it")),
    INSTAGRAM("Instagram", 0xFFE1306C, listOf("instagram.com", "instagr.am")),
    THREADS("Threads", 0xFF8E8E93, listOf("threads.net", "threads.com")),
    TIKTOK("TikTok", 0xFF00B3B3, listOf("tiktok.com")),
    FACEBOOK("Facebook", 0xFF1877F2, listOf("facebook.com", "fb.watch", "fb.com", "fb.me")),
    OTHER("Other site", 0xFF7B7F93, emptyList());

    fun matches(host: String): Boolean = hosts.any { host == it || host.endsWith(".$it") }

    companion object {
        /** The platforms shown as "supported" chips on the home screen. */
        val named: List<Platform> get() = entries.filter { it != OTHER }

        fun fromHost(host: String?): Platform {
            val h = host?.lowercase() ?: return OTHER
            return named.firstOrNull { it.matches(h) } ?: OTHER
        }
    }
}
