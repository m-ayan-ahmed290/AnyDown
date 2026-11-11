package com.social_downloader

import com.social_downloader.engine.ErrorMapper
import com.social_downloader.engine.FormatLite
import com.social_downloader.engine.FormatSelector
import com.social_downloader.engine.Platform
import com.social_downloader.engine.QualityBuilder
import com.social_downloader.engine.UrlTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {
    @Test fun extractsLinkFromSharedText() {
        assertEquals(
            "https://x.com/user/status/123?s=20",
            UrlTools.extractUrl("Check this out https://x.com/user/status/123?s=20, wow"),
        )
    }

    @Test fun detectsPlatforms() {
        val cases = mapOf(
            "https://www.youtube.com/watch?v=abc" to Platform.YOUTUBE,
            "https://youtu.be/abc" to Platform.YOUTUBE,
            "https://twitter.com/a/status/1" to Platform.X,
            "https://x.com/a/status/1" to Platform.X,
            "https://www.reddit.com/r/x/comments/1/t/" to Platform.REDDIT,
            "https://www.instagram.com/reel/abc/" to Platform.INSTAGRAM,
            "https://www.threads.net/@a/post/b" to Platform.THREADS,
            "https://vm.tiktok.com/abc/" to Platform.TIKTOK,
            "https://fb.watch/abc/" to Platform.FACEBOOK,
            "https://vimeo.com/123" to Platform.VIMEO,
        )
        cases.forEach { (url, platform) -> assertEquals(url, platform, UrlTools.detect(url)) }
    }

    @Test fun recognizesExpandedSitesAndAdultHosts() {
        assertEquals(Platform.SOUNDCLOUD, UrlTools.detect("https://on.soundcloud.com/example"))
        assertEquals(Platform.PORNHUB, UrlTools.detect("https://www.pornhub.com/view_video.php?viewkey=test"))
        assertEquals(Platform.OTHER, UrlTools.detect("https://pornhub.com.example.org/video"))
        assertTrue(Platform.PORNHUB.adult)
        assertFalse(Platform.YOUTUBE.adult)
        assertEquals("Instagram", Platform.INSTAGRAM.folderName)
        assertTrue(Platform.named.all { !it.folderName.contains('/') && it.folderName != ".." })
    }

    @Test fun doesNotConfuseLookalikeHosts() {
        assertEquals(Platform.OTHER, UrlTools.detect("https://notyoutube.com/watch?v=1"))
        assertEquals(Platform.OTHER, UrlTools.detect("https://youtube.com.evil.io/x"))
    }

    @Test fun addsSchemeToBareDomains() {
        assertEquals("https://x.com/a/status/1", UrlTools.check("x.com/a/status/1").url)
    }

    @Test fun rejectsUnsafeInput() {
        listOf(
            "ftp://example.com/a", "http://localhost/x", "http://192.168.1.5/x", "http://10.0.0.2/",
            "http://127.0.0.1:8080/", "javascript:alert(1)", "--exec foo", "file:///etc/passwd", "notaurl",
        ).forEach { assertFalse(it, UrlTools.check(it).isValid) }
    }

    @Test fun emptyInputIsNotAnError() {
        val c = UrlTools.check("   ")
        assertNull(c.url)
        assertNull(c.error)
    }

    @Test fun allowsPublicIpHosts() {
        assertTrue(UrlTools.check("https://8.8.8.8/video").isValid)
    }
}

class ErrorMapperTest {
    @Test fun unsupportedUrl() {
        assertTrue(ErrorMapper.map("ERROR: Unsupported URL: https://x.com/home", Platform.X).contains("isn't supported"))
    }

    @Test fun loginRequired() {
        val m = ErrorMapper.map(
            "ERROR: [Instagram] abc: Requested content is not available, rate-limit reached or login required",
            Platform.INSTAGRAM,
        )
        assertTrue(m, m.contains("signed in"))
    }

    @Test fun rateLimited() {
        assertTrue(ErrorMapper.map("ERROR: HTTP Error 429: Too Many Requests", Platform.REDDIT).contains("rate-limiting"))
    }

    @Test fun botCheck() {
        assertTrue(
            ErrorMapper.map("ERROR: [youtube] x: Sign in to confirm you\u2019re not a bot", Platform.YOUTUBE)
                .contains("not a bot"),
        )
    }

    @Test fun privateVideo() {
        assertTrue(
            ErrorMapper.map("ERROR: Private video. Sign in if you've been granted access", Platform.YOUTUBE)
                .contains("private"),
        )
    }

    @Test fun summarizePicksErrorLine() {
        assertEquals("boom", ErrorMapper.summarize("WARNING: x\nERROR: boom\n"))
    }

    @Test fun fallbackIncludesDetail() {
        assertTrue(ErrorMapper.map("ERROR: something odd", Platform.OTHER).contains("something odd"))
    }
}

class QualityBuilderTest {
    private fun f(w: Int, h: Int, v: String?, a: String?, size: Long = 0, ext: String? = "mp4", abr: Int = 0, tbr: Int = 0) =
        FormatLite(w, h, v, a, abr, tbr, size, ext)

    @Test fun buildsLadderWithMergeAndAudio() {
        val formats = listOf(
            f(1920, 1080, "avc1.640028", "none", 40_000_000),
            f(1920, 1080, "vp09.00", "none", 30_000_000, "webm"),
            f(1280, 720, "avc1.4d401f", "none", 20_000_000),
            f(0, 0, "none", "mp4a.40.2", 4_000_000, "m4a", abr = 128),
        )
        val q = QualityBuilder.build(formats, 100)
        assertEquals(listOf("1080p", "720p", "Audio only"), q.map { it.label })
        assertEquals(44_000_000L, q[0].estimatedBytes)
        assertTrue(q[0].needsMerge)
        assertTrue(q[2].audioOnly)
    }

    @Test fun verticalVideoUsesShortSide() {
        val q = QualityBuilder.build(listOf(f(1080, 1920, "avc1", "mp4a")), 30)
        assertEquals("1080p", q.first().label)
        assertFalse(q.first().needsMerge)
    }

    @Test fun unknownHeightsFallBackToBest() {
        val q = QualityBuilder.build(emptyList(), 0)
        assertEquals("Best available", q.first().label)
        assertNotNull(q.last())
        assertTrue(q.last().audioOnly)
    }
}

class FormatSelectorTest {
    @Test fun sortOrder() {
        assertEquals("res,vcodec:h264,acodec:aac", FormatSelector.sortOrder(null))
        assertEquals("res:720,vcodec:h264,acodec:aac", FormatSelector.sortOrder(720))
    }
}

class CookieAndSpeedTest {
    @Test fun buildsNetscapeFile() {
        val text = com.social_downloader.engine.CookieFile.build(
            mapOf(".instagram.com" to "sessionid=abc; csrftoken=xyz", ".x.com" to null), 2000000000L,
        )
        assertNotNull(text)
        assertTrue(text!!.startsWith("# Netscape HTTP Cookie File"))
        assertTrue(text.contains(".instagram.com\tTRUE\t/\tTRUE\t2000000000\tsessionid\tabc"))
        assertTrue(com.social_downloader.engine.CookieFile.looksValid(text))
    }

    @Test fun emptyCookiesGiveNull() {
        assertNull(com.social_downloader.engine.CookieFile.build(mapOf(".a.com" to " "), 1L))
        assertFalse(com.social_downloader.engine.CookieFile.looksValid("hello world"))
    }

    @Test fun parsesSpeed() {
        assertEquals(2621440L, com.social_downloader.util.Format.parseSpeed("[download]  45.2% of  120.00MiB at    2.50MiB/s ETA 00:33"))
        assertNull(com.social_downloader.util.Format.parseSpeed("[download]  45.2% of 120MiB at Unknown B/s ETA Unknown"))
        assertEquals(512000L, com.social_downloader.util.Format.parseSpeed("at 500.00KiB/s"))
    }
}

class AccountCookieTest {
    @Test fun scopesImportsAndKeepsHttpOnlySessionCookies() {
        val text = "#HttpOnly_.instagram.com\tTRUE\t/\tTRUE\t0\tsessionid\tsecret\n" +
            ".evil.org\tTRUE\t/\tTRUE\t0\tsessionid\tunrelated\n" +
            ".instagram.com\tTRUE\t/\tTRUE\t1\tstale\told\n"
        val scoped = com.social_downloader.engine.CookieFile.forPlatform(text, Platform.INSTAGRAM, 100)!!
        assertTrue(scoped.contains("#HttpOnly_"))
        assertFalse(scoped.contains("unrelated"))
        assertFalse(scoped.contains("stale"))
        assertTrue(com.social_downloader.engine.CookieFile.hasSession(scoped, Platform.INSTAGRAM))
        assertNull(com.social_downloader.engine.CookieFile.forPlatform(text, Platform.REDDIT, 100))
    }
    @Test fun anonymousCookiesAreNotAnInstagramLogin() {
        val text = com.social_downloader.engine.CookieFile.build(mapOf(".instagram.com" to "csrftoken=anonymous"), 9999999999)!!
        assertFalse(com.social_downloader.engine.CookieFile.hasSession(text, Platform.INSTAGRAM))
    }
    @Test fun adultAccountsAreCompleteAndSeparate() {
        val adult = com.social_downloader.engine.SiteLogin.adult
        assertEquals(Platform.named.filter { it.adult }.toSet(), adult.map { it.platform }.toSet())
        assertTrue(com.social_downloader.engine.SiteLogin.all.none { it.platform.adult })
        assertEquals(Platform.EPORNER, UrlTools.detect("https://www.eporner.com/video-test"))
    }
}
