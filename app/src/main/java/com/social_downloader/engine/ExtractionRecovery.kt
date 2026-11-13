package com.social_downloader.engine

/** Restrict automatic recovery to extractor-shape errors, not access restrictions or bad credentials. */
object ExtractionRecovery {
    fun needsFreshSnapshot(raw: String?): Boolean {
        val msg = raw.orEmpty().lowercase()
        return "http error 403" in msg || "http error 410" in msg || "url has expired" in msg
    }

    fun isTikTokParseFailure(platform: Platform, raw: String?): Boolean {
        if (platform != Platform.TIKTOK) return false
        val message = raw.orEmpty().lowercase()
        return listOf("unable to extract webpage video data", "unable to extract universal data",
            "unable to extract sigi", "unable to extract hydration").any { it in message }
    }
}
