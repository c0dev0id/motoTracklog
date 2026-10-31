package de.codevoid.mototracklog

import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.update.ReleaseInfo
import de.codevoid.mototracklog.update.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class UpdateCheckerTest {

    // The shape the dist store's latest address answers with.
    private fun distFile(name: String = "motoTracklog-dev-abc1234.apk", version: String? = "\"dev-abc1234\"") = """
        {"name":"$name","version":$version,"size":5242880,"sha256":"00ff",
         "uploaded":"2026-10-31T22:00:00Z","url":"/dist/k3y/$name"}
    """.trimIndent()

    private val latest = "https://home.codevoid.de/dist/k3y/latest"

    @Test
    fun parseDistFileTakesTheVersionAndResolvesTheAddress() {
        val info = UpdateChecker.parseDistFile(distFile(), latest)!!
        assertEquals("dev-abc1234", info.versionName)
        assertEquals("https://home.codevoid.de/dist/k3y/motoTracklog-dev-abc1234.apk", info.apkUrl)
        assertEquals("motoTracklog-dev-abc1234.apk", info.apkName)
    }

    @Test
    fun parseDistFileRejectsAFileWithoutAVersion() {
        assertNull(UpdateChecker.parseDistFile(distFile(version = "null"), latest))
        assertNull(UpdateChecker.parseDistFile("""{"name":"motoTracklog-dev-a.apk","url":"/dist/k3y/a"}""", latest))
    }

    @Test
    fun parseDistFileRejectsANameThatIsNotABareApk() {
        assertNull(UpdateChecker.parseDistFile(distFile(name = "notes.txt"), latest))
        assertNull(UpdateChecker.parseDistFile(distFile(name = "../motoTracklog-dev-evil.apk"), latest))
    }

    @Test
    fun parseDistFileRejectsAFileWithoutAnAddress() {
        assertNull(UpdateChecker.parseDistFile("""{"name":"motoTracklog-dev-a.apk","version":"dev-a"}""", latest))
    }

    @Test
    fun devBuildsFollowTheNightlyChannelAndReleasesTheReleaseChannel() {
        val dist = "https://home.codevoid.de/dist/a4wracka"
        assertEquals("$dist/nightly/latest", UpdateChecker.latestUrlFor("dev-abc1234"))
        assertEquals("$dist/nightly/latest", UpdateChecker.latestUrlFor("dev-local-debug"))
        assertEquals("$dist/release/latest", UpdateChecker.latestUrlFor("1.0.3"))
    }

    @Test
    fun deleteInstalledUpdateRemovesOnlyTheRunningBuildsApk() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val dir = File(context.cacheDir, "updates")
        dir.deleteRecursively()
        UpdateChecker(context).deleteInstalledUpdate() // missing dir is fine

        dir.mkdirs()
        val installed = File(dir, "motoTracklog-${BuildConfig.VERSION_NAME}.apk").apply { writeText("x") }
        val pending = File(dir, "motoTracklog-dev-other.apk").apply { writeText("y") }

        UpdateChecker(context).deleteInstalledUpdate()

        assertFalse(installed.exists())
        assertTrue(pending.exists())
    }

    @Test
    fun isNewerOnlyWhenVersionDiffers() {
        val remote = ReleaseInfo("dev-new", "url", "motoTracklog-dev-new.apk")
        assertTrue(UpdateChecker.isNewer(remote, "dev-old"))
        assertFalse(UpdateChecker.isNewer(remote, "dev-new"))
        assertFalse(UpdateChecker.isNewer(ReleaseInfo("", "u", "n"), "dev-old"))
    }
}
