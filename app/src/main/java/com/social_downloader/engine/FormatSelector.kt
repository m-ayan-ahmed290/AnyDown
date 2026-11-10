package com.social_downloader.engine

/**
 * yt-dlp options that produce ONE .mp4 containing video + audio.
 *
 *  - `-f bv*+ba/b`   best video + best audio, merged by FFmpeg (or a single combined stream).
 *  - `-S res:N,...`  stay at or under N p, then prefer H.264 + AAC so the .mp4 plays everywhere.
 *  - `--merge-output-format mp4` / `--remux-video mp4` guarantee the final container is MP4.
 */
object FormatSelector {
    const val VIDEO_FORMAT = "bv*+ba/b"
    const val AUDIO_FORMAT = "ba/b"

    fun sortOrder(heightCap: Int?): String =
        if (heightCap == null) "res,vcodec:h264,acodec:aac" else "res:$heightCap,vcodec:h264,acodec:aac"
}
