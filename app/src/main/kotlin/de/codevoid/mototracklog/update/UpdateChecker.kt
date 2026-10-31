package de.codevoid.mototracklog.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import de.codevoid.mototracklog.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val versionName: String,
    val apkUrl: String,
    val apkName: String,
)

class UpdateChecker(private val context: Context) {

    private val downloadDir = File(context.cacheDir, "updates")

    /** Returns release info if the release this build follows differs from the installed build, else null. */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val release = parseRelease(httpGet(releaseApiUrl(BuildConfig.VERSION_NAME))) ?: return@withContext null
        if (isNewer(release, BuildConfig.VERSION_NAME)) release else null
    }

    suspend fun download(
        release: ReleaseInfo,
        onProgress: ((bytesWritten: Long, totalBytes: Long) -> Unit)? = null,
    ): File = withContext(Dispatchers.IO) {
        val dir = downloadDir.apply { mkdirs() }
        val file = File(dir, release.apkName)
        // The directory holds at most the download in progress.
        dir.listFiles()?.filter { it != file }?.forEach { it.delete() }
        httpDownload(release.apkUrl, file, onProgress)
        file
    }

    /**
     * Deletes the APK of the build that is now running, i.e. the update that was just
     * installed. Only that file: a different version might still be open in the system
     * installer if this process was restarted to serve it through the FileProvider.
     */
    fun deleteInstalledUpdate() {
        File(downloadDir, "$APK_PREFIX${BuildConfig.VERSION_NAME}.apk").delete()
    }

    fun installIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun httpGet(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun httpDownload(
        urlString: String,
        dest: File,
        onProgress: ((Long, Long) -> Unit)? = null,
    ) {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val totalBytes = connection.contentLengthLong
            var written = 0L
            connection.inputStream.use { input ->
                FileOutputStream(dest).use { output ->
                    val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        written += n
                        onProgress?.invoke(written, totalBytes)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val RELEASES_API = "https://api.github.com/repos/c0dev0id/motoTracklog/releases"
        private const val DEV_VERSION_PREFIX = "dev-"
        private const val USER_AGENT = "motoTracklog"
        private const val TIMEOUT_MS = 15_000
        private const val APK_PREFIX = "motoTracklog-"

        /** Dev builds follow the `dev` pre-release, release builds the latest release. */
        fun releaseApiUrl(installedVersionName: String): String =
            if (installedVersionName.startsWith(DEV_VERSION_PREFIX)) "$RELEASES_API/tags/dev"
            else "$RELEASES_API/latest"

        fun isNewer(remote: ReleaseInfo, installedVersionName: String): Boolean =
            remote.versionName.isNotEmpty() && remote.versionName != installedVersionName

        /**
         * Extracts the APK asset from a GitHub release JSON. The version name is derived
         * from the asset filename (`motoTracklog-<versionName>.apk`), matching the build's
         * `versionName` so [isNewer] can compare them directly.
         */
        fun parseRelease(json: String): ReleaseInfo? {
            val assets = JSONObject(json).optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk")) continue
                return ReleaseInfo(
                    versionName = name.removeSuffix(".apk").removePrefix(APK_PREFIX),
                    apkUrl = asset.optString("browser_download_url"),
                    apkName = name,
                )
            }
            return null
        }
    }
}
