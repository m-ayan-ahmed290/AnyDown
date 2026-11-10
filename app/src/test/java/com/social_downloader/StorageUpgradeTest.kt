package com.social_downloader

import android.Manifest
import android.app.Application
import android.os.Environment
import com.social_downloader.data.DownloadItem
import com.social_downloader.data.SettingsStore
import com.social_downloader.download.StorageMigration
import com.social_downloader.engine.Platform
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class StorageUpgradeTest {
    private val app get() = RuntimeEnvironment.getApplication<Application>()
    @Before fun prepare() {
        shadowOf(app).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        app.getSharedPreferences("settings", 0).edit().clear().commit()
    }

    @Test fun adultDirectoryDefaultsOffAndPreferencePersists() {
        val settings = SettingsStore(app)
        assertFalse(settings.showAdultSites.value)
        assertTrue(Platform.directory(false).none { it.adult })
        assertTrue(Platform.directory(false, "porn").isEmpty())
        settings.setShowAdultSites(true)
        assertTrue(SettingsStore(app).showAdultSites.value)
        assertTrue(Platform.directory(true).any { it.adult })
        settings.setShowAdultSites(false)
        assertFalse(SettingsStore(app).showAdultSites.value)
        assertTrue(Platform.directory(false).none { it.adult })
    }

    @Test fun migrationPreservesCollisionsAndUpdatesHistoryAndIsIdempotent() {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val old = File(base, "SocialDownloader").apply { mkdirs() }
        val source = File(old, "clip.mp4").apply { writeText("old content") }
        val existing = File(base, "AnyDowns/Instagram/clip.mp4").apply { parentFile!!.mkdirs(); writeText("existing") }
        val item = DownloadItem.fromJson(JSONObject().put("id", "one").put("url", "https://instagram.com/reel/a")
            .put("platform", "INSTAGRAM").put("status", "COMPLETED").put("filePath", source.absolutePath))
        val unknown = File(old, "unknown.mp4").apply { writeText("unknown") }
        val result = StorageMigration.migrate(app, listOf(item)).getValue("one")
        assertEquals("existing", existing.readText())
        assertEquals("old content", File(result.filePath!!).readText())
        assertEquals("clip (1).mp4", result.fileName)
        assertFalse(source.exists())
        assertFalse(unknown.exists())
        assertEquals("unknown", File(base, "AnyDowns/Other/unknown.mp4").readText())
        assertTrue(StorageMigration.migrate(app, listOf(result)).isEmpty())
        assertTrue(File(result.filePath).exists())
    }

    @Test fun deniedLegacyPermissionLeavesFilesUntouched() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val source = File(base, "SocialDownloader/keep.mp4").apply { parentFile!!.mkdirs(); writeText("keep") }
        assertTrue(StorageMigration.migrate(app, emptyList()).isEmpty())
        assertEquals("keep", source.readText())
    }
}
