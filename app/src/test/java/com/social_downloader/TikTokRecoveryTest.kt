package com.social_downloader

import com.social_downloader.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File

class TikTokRecoveryTest {
    @get:Rule val files = TemporaryFolder()
    @Test fun parsingFailureIsNotMisreportedAsExpiredCookies() {
        val error = "ERROR: [TikTok] 7643629243930348831: Unable to extract webpage video data; please report this issue on https://github.com/yt-dlp/yt-dlp/issues"
        assertTrue(ExtractionRecovery.isTikTokParseFailure(Platform.TIKTOK, error))
        assertFalse(ExtractionRecovery.isTikTokParseFailure(Platform.INSTAGRAM, error))
        val message = ErrorMapper.map(error, Platform.TIKTOK, true)
        assertTrue(message.lowercase().contains("update yt-dlp"))
        assertFalse(message.contains("please report this issue"))
        assertFalse(message.contains("rejected your saved session"))
    }
    @Test fun doesNotAutoUpdateForAccessRestrictionsOrNetworkErrors() {
        listOf("HTTP Error 403", "login required", "HTTP Error 429", "network is unreachable")
            .forEach { assertFalse(ExtractionRecovery.isTikTokParseFailure(Platform.TIKTOK, it)) }
        assertTrue(ExtractionRecovery.needsFreshSnapshot("HTTP Error 403: Forbidden"))
        assertFalse(ExtractionRecovery.needsFreshSnapshot("HTTP Error 429: Too Many Requests"))
    }
    @Test fun snapshotPreservesFullInfoAndExpiresInsteadOfReusingStaleUrls() {
        var clock = 1000000L
        val store = PreviewSnapshotStore(files.newFolder(), { clock })
        val source = files.newFile().apply { writeText("""{"formats":[{"url":"https://cdn.example/video"}],"http_headers":{"Referer":"https://www.tiktok.com/"}}""") }
        store.put("https://tiktok.com/video/1", source)
        val copied = File(files.root, "copy.json")
        assertTrue(store.copyTo("https://tiktok.com/video/1", copied))
        assertEquals(source.readText(), copied.readText())
        assertFalse(store.copyTo("https://tiktok.com/video/2", copied))
        clock += 300001
        assertFalse(store.copyTo("https://tiktok.com/video/1", copied))
    }
    @Test fun signingOutClearsSnapshots() {
        val store = PreviewSnapshotStore(files.newFolder())
        val source = files.newFile().apply { writeText("{}") }
        store.put("video", source)
        store.clear()
        assertFalse(store.copyTo("video", File(files.root, "copy.json")))
    }
}
