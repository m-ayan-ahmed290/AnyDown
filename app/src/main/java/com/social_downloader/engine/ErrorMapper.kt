package com.social_downloader.engine

/** Turns raw yt-dlp stderr into short, actionable messages for the UI. */
object ErrorMapper {

    fun summarize(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown error"
        val line = raw.lines().lastOrNull { it.contains("ERROR:") }
            ?: raw.lines().lastOrNull { it.isNotBlank() }
            ?: raw
        return line.substringAfter("ERROR:").trim()
            .replace(Regex("""\u001B\[[0-9;]*m"""), "")
            .let { if (it.length > 220) it.take(220) + "…" else it }
    }

    fun map(raw: String?, platform: Platform, signedIn: Boolean = false): String {
        val msg = raw.orEmpty().lowercase()
        val site = if (platform == Platform.OTHER) "This site" else platform.label

        return when {
            ExtractionRecovery.isTikTokParseFailure(platform, raw) ->
                "TikTok returned a page the engine couldn't read. Update yt-dlp in Settings → Download engine, then retry. " +
                    "Cookies alone won't fix this. If Stable still fails, try Nightly."

            "unsupported url" in msg ->
                "This link isn't supported. Open the video or post itself and share that link " +
                    "(not a profile, search or home page)."

            "private video" in msg || "this video is private" in msg || "is private" in msg ->
                "This content is private. Only the owner can access it."

            "not a bot" in msg || "confirm you" in msg && "bot" in msg ->
                "$site is asking to verify you're not a bot. Try again later or on a different network."

            "age-restricted" in msg || "confirm your age" in msg || "age restricted" in msg ->
                "This content is age-restricted and requires a signed-in account. If this site is listed under Settings \u2192 Accounts, sign in there."

            "login required" in msg || "sign in" in msg || "log in" in msg || "logged in" in msg ||
                "cookies" in msg || "empty media response" in msg || "rate-limit reached" in msg ->
                if (signedIn) {
                    "$site rejected your saved session. It may have expired, or this content is private. " +
                        "Sign in again under Settings \u2192 Accounts."
                } else {
                    "$site requires you to be signed in to view this. Sign in with your own account under " +
                        "Settings \u2192 Accounts. Private content you can't normally view still won't work."
                }

            "drm" in msg ->
                "This video is DRM-protected and can't be downloaded."

            "http error 429" in msg || "too many requests" in msg ->
                "$site is rate-limiting requests. Wait a few minutes and try again."

            "http error 403" in msg || "forbidden" in msg ->
                "$site refused access (HTTP 403). The video may be restricted or the site is blocking downloads."

            "http error 404" in msg || "not found" in msg && "http" in msg ||
                "does not exist" in msg || "has been removed" in msg || "been deleted" in msg ||
                "video unavailable" in msg || "no longer available" in msg ->
                "This video is unavailable. It may have been deleted or the link is wrong."

            "geo" in msg && ("restrict" in msg || "block" in msg) || "not available in your country" in msg ||
                "uploader has not made this video available" in msg ->
                "This video isn't available in your region."

            "no video could be found" in msg || "no video formats found" in msg ||
                "there is no video" in msg || "does not contain any video" in msg ||
                "no media" in msg ->
                "This post doesn't contain a downloadable video."

            "live event" in msg || "is live" in msg || "live stream" in msg && "not" in msg ->
                "Live streams aren't supported. Try again once the broadcast has ended."

            "requested format is not available" in msg ->
                "That quality isn't available for this video. Pick another quality."

            "no space left" in msg || "enospc" in msg ->
                "Not enough free storage on this device."

            "ffmpeg" in msg || "postprocess" in msg || "merger" in msg ->
                "Merging audio and video failed. Try another quality."

            "unable to download webpage" in msg || "name or service not known" in msg ||
                "timed out" in msg || "network is unreachable" in msg || "urlopen error" in msg ||
                "temporary failure" in msg || "connection" in msg ->
                "Network problem while contacting $site. Check your connection and try again."

            "permission denied" in msg || "read-only file system" in msg ->
                "Couldn't write to storage. Check the app's permissions."

            else -> "Couldn't process this link: ${summarize(raw)}"
        }
    }
}
