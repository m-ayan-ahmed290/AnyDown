package com.social_downloader.engine

/** Sites we recognise by hostname. Anything else is still attempted through yt-dlp's 1000+ extractors. */
enum class Platform(val label: String, val brandColor: Long, private val hosts: List<String>, val adult: Boolean = false) {
    YOUTUBE("YouTube", 0xFFFF0033, listOf("youtube.com", "youtu.be", "youtube-nocookie.com")),
    X("X (Twitter)", 0xFF1D9BF0, listOf("twitter.com", "x.com", "t.co", "fxtwitter.com", "vxtwitter.com")),
    REDDIT("Reddit", 0xFFFF4500, listOf("reddit.com", "redd.it")),
    INSTAGRAM("Instagram", 0xFFE1306C, listOf("instagram.com", "instagr.am")),
    THREADS("Threads", 0xFF8E8E93, listOf("threads.net", "threads.com")),
    TIKTOK("TikTok", 0xFF00B3B3, listOf("tiktok.com")),
    FACEBOOK("Facebook", 0xFF1877F2, listOf("facebook.com", "fb.watch", "fb.com", "fb.me")),
    VIMEO("Vimeo", 0xFF548AAB, listOf("vimeo.com")),
    DAILYMOTION("Dailymotion", 0xFF548AAB, listOf("dailymotion.com","dai.ly")),
    TWITCH("Twitch", 0xFF548AAB, listOf("twitch.tv","clips.twitch.tv")),
    SOUNDCLOUD("SoundCloud", 0xFF548AAB, listOf("soundcloud.com","on.soundcloud.com")),
    PINTEREST("Pinterest", 0xFF548AAB, listOf("pinterest.com","pin.it")),
    SNAPCHAT("Snapchat", 0xFF548AAB, listOf("snapchat.com")),
    BILIBILI("Bilibili", 0xFF548AAB, listOf("bilibili.com","b23.tv")),
    VK("VK", 0xFF548AAB, listOf("vk.com","vkvideo.ru")),
    RUMBLE("Rumble", 0xFF548AAB, listOf("rumble.com")),
    TUMBLR("Tumblr", 0xFF548AAB, listOf("tumblr.com")),
    BANDCAMP("Bandcamp", 0xFF548AAB, listOf("bandcamp.com")),
    TED("TED", 0xFF548AAB, listOf("ted.com")),
    BLUESKY("Bluesky", 0xFF548AAB, listOf("bsky.app")),
    STREAMABLE("Streamable", 0xFF548AAB, listOf("streamable.com")),
    INTERNET_ARCHIVE("Internet Archive", 0xFF548AAB, listOf("archive.org")),
    PORNHUB("Pornhub", 0xFFEF8B32, listOf("pornhub.com","pornhub.org"), adult = true),
    XVIDEOS("XVideos", 0xFFEF8B32, listOf("xvideos.com"), adult = true),
    XNXX("XNXX", 0xFFEF8B32, listOf("xnxx.com"), adult = true),
    XHAMSTER("xHamster", 0xFFEF8B32, listOf("xhamster.com"), adult = true),
    OTHER("Other site", 0xFF7B7F93, emptyList());

    val folderName: String get() = when (this) {
        X -> "Twitter"
        OTHER -> "Other"
        else -> label
    }

    fun matches(host: String): Boolean = hosts.any { host == it || host.endsWith(".$it") }

    companion object {
        /** The platforms shown as "supported" chips on the home screen. */
        val named: List<Platform> get() = entries.filter { it != OTHER }

        fun directory(showAdult: Boolean, query: String = ""): List<Platform> =
            named.filter { (!it.adult || showAdult) && it.label.contains(query, ignoreCase = true) }

        fun fromHost(host: String?): Platform {
            val h = host?.lowercase() ?: return OTHER
            return named.firstOrNull { it.matches(h) } ?: OTHER
        }
    }
}
