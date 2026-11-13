package com.social_downloader

import android.app.Application
import com.social_downloader.data.*
import com.social_downloader.engine.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],application=Application::class)
class ExperienceTest {
    @Test fun pausedQueueCanBePrioritizedWithoutChangingHistoryDates() {
        val app=RuntimeEnvironment.getApplication(); val settings=SettingsStore(app); val cookies=CookieStore(app)
        val manager=com.social_downloader.download.DownloadManager(app,YtDlpEngine(app,cookies),settings,cookies)
        manager.pauseQueue(true)
        val q=QualityOption("v720","720p",null,720,false,false,null)
        val info=MediaInfo("https://example.com/a","A",null,10,null,Platform.OTHER,null,listOf(q),null)
        val first=manager.enqueue(info,q); val second=manager.enqueue(info.copy(url="https://example.com/b"),q)
        val created=manager.items.value.first {it.id==second}.createdAt
        manager.next(second)
        assertTrue(manager.items.value.all {it.status==DownloadStatus.QUEUED})
        assertEquals(created,manager.items.value.first {it.id==second}.createdAt)
        assertTrue(manager.items.value.first {it.id==second}.queuePriority < manager.items.value.first {it.id==first}.queuePriority)
        manager.cancelAll()
    }
    @Test fun customizationBackupRoundTripsAndRejectsWrongFiles() {
        val profile=ProfileBackup.Profile(Experience(appTitle="My downloads",palette=3,audioFormat="OPUS"),ThemeMode.DARK,false)
        assertEquals(profile,ProfileBackup.read(ProfileBackup.encode(profile).byteInputStream()))
        assertTrue(runCatching { ProfileBackup.read("{}".byteInputStream()) }.isFailure)
        assertTrue(runCatching { ProfileBackup.read("x".repeat(17000).byteInputStream()) }.isFailure)
    }
    @Test fun customizationPersistsAndResetKeepsFavorites() {
        val app=RuntimeEnvironment.getApplication()
        val settings=SettingsStore(app)
        settings.customize { it.copy(palette=4,textScale=1.2f,typeface=2,compact=true,startTab="LIBRARY",audioDefault=true,audioFormat="MP3",audioBitrate=320) }
        settings.favorite("saved-file")
        val restored=SettingsStore(app)
        assertEquals(settings.experience.value,restored.experience.value)
        assertTrue("saved-file" in restored.favorites.value)
        restored.resetCustomization()
        assertEquals(Experience(),restored.experience.value)
        assertTrue("saved-file" in restored.favorites.value)
    }
    @Test fun settingsAreClampedAndMalformedDataDoesNotCrash() {
        val v=Experience.parse("""{"palette":99,"textScale":99,"width":1,"corners":-7}""")
        assertEquals(6,v.palette); assertEquals(1.35f,v.textScale); assertEquals(480,v.width); assertEquals(0,v.corners)
        assertEquals(Experience(),Experience.parse("not json"))
    }
    @Test fun batchValidatesDeduplicatesAndLimitsLinks() {
        val links=DownloadDefaults.links("https://example.com/test\nhttps://example.com/test\nhttp://127.0.0.1/private\nyoutu.be/abc\nMore https://vimeo.com/123 and https://example.org/media")
        assertEquals(listOf("https://example.com/test","https://youtu.be/abc","https://vimeo.com/123","https://example.org/media"),links)
        assertEquals(25,DownloadDefaults.links((1..40).joinToString("\n"){"https://example.com/$it"}).size)
    }
    @Test fun presetsChooseResolutionOrSelectedAudioCodec() {
        val video=listOf(1080,720,480).map { QualityOption("v$it","${it}p",null,it,false,false,null) }
        val info=MediaInfo("https://example.com/video","Title",null,20,null,Platform.OTHER,null,video+QualityOption("audio","Audio",null,null,true,false,null),null)
        assertEquals(720,DownloadDefaults.choose(info,Experience(videoHeight=720))!!.heightCap)
        assertEquals(480,DownloadDefaults.choose(info,Experience(videoHeight=360))!!.heightCap)
        val audio=DownloadDefaults.choose(info,Experience(audioDefault=true,audioFormat="MP3",audioBitrate=320))!!
        assertTrue(audio.audioOnly); assertEquals(AudioFormat.MP3,audio.audioFormat); assertEquals(320,audio.audioBitrateKbps)
    }
}
