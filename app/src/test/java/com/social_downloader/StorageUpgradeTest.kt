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
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun prepare() {
        val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
        org.robolectric.shadows.ShadowEnvironment.setExternalStoragePublicDirectory(Environment.getExternalStorageDirectory().toPath())
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

    @Test fun restoresHistoryAfterProcessDiesBetweenMoveAndHistorySave() {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val source = File(base, "SocialDownloader/recover.mp4")
        val target = File(base, "AnyDowns/Instagram/recover.mp4").apply { parentFile!!.mkdirs(); writeText("saved") }
        val item = DownloadItem.fromJson(JSONObject().put("id", "recover").put("url", "https://instagram.com/reel/a")
            .put("platform", "INSTAGRAM").put("status", "COMPLETED").put("filePath", source.absolutePath))
        app.getSharedPreferences("storage_migration_paths", 0).edit().putString("recover",
            JSONObject().put("source", source.absolutePath).put("target", target.absolutePath).toString()).commit()
        val recovered = StorageMigration.migrate(app, listOf(item)).getValue("recover")
        assertEquals(target.absolutePath, recovered.filePath)
        assertTrue(StorageMigration.migrate(app, listOf(recovered)).isEmpty())
    }

    @Test fun documentImportKeepsOriginalAndSkipsRepeatedSelection() {
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val original = File(base, "OldInstallation/import.mp4").apply { parentFile!!.mkdirs(); writeText("video bytes") }
        val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", original)
        val cookies = com.social_downloader.data.CookieStore(app)
        val manager = com.social_downloader.download.DownloadManager(app,
            com.social_downloader.engine.YtDlpEngine(app, cookies), SettingsStore(app), cookies)
        assertTrue(manager.importOldFile(uri))
        val saved = manager.items.value.first()
        assertEquals("video bytes", File(saved.filePath!!).readText())
        assertEquals("video bytes", original.readText())
        assertFalse(manager.importOldFile(uri))
    }

    @Test fun deniedLegacyPermissionLeavesFilesUntouched() {
        shadowOf(app).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val source = File(base, "SocialDownloader/keep.mp4").apply { parentFile!!.mkdirs(); writeText("keep") }
        assertTrue(StorageMigration.migrate(app, emptyList()).isEmpty())
        assertEquals("keep", source.readText())
    }
}
