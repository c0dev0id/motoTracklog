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

    /**
     * Returns release info if the build this one follows differs from the installed build,
     * else null. Dev builds follow the newest push to the dist store, release builds the
     * latest GitHub release.
     */
    suspend fun check(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val installed = BuildConfig.VERSION_NAME
        val release = if (installed.startsWith(DEV_VERSION_PREFIX)) {
            parseDistFile(httpGet(DIST_LATEST, "application/json"), DIST_LATEST)
        } else {
            parseRelease(httpGet(RELEASES_LATEST, "application/vnd.github+json"))
        }
        release?.takeIf { isNewer(it, installed) }
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

    private fun httpGet(urlString: String, accept: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", accept)
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
        private const val RELEASES_LATEST = "https://api.github.com/repos/c0dev0id/motoTracklog/releases/latest"
        private const val DIST_LATEST = "https://home.codevoid.de/dist/a4wracka/latest"
        private const val DEV_VERSION_PREFIX = "dev-"
        private const val USER_AGENT = "motoTracklog"
        private const val TIMEOUT_MS = 15_000
        private const val APK_PREFIX = "motoTracklog-"

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

        /**
         * Reads the file a dist store `latest` answer describes, whose `url` is relative to
         * [latestUrl]. Null for a file without a version or address, or whose name is not a
         * bare APK file name, since the name becomes the download's file name.
         */
        fun parseDistFile(json: String, latestUrl: String): ReleaseInfo? {
            val file = JSONObject(json)
            val name = file.optString("name")
            if (!name.endsWith(".apk") || '/' in name || file.isNull("version") || file.isNull("url")) return null
            return ReleaseInfo(
                versionName = file.getString("version"),
                apkUrl = URL(URL(latestUrl), file.getString("url")).toString(),
                apkName = name,
            )
        }
    }
}
