package de.codevoid.mototracklog.ui

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import de.codevoid.mototracklog.R
import de.codevoid.mototracklog.update.ReleaseInfo
import de.codevoid.mototracklog.update.UpdateChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// User-triggered update check: the caller is disabled while the flow is in flight
// (setClickable false → true) and each outcome — latest build, update available with an
// install prompt, failure — is a dialog. Off-bike, touch-only flow, so stock alert
// buttons are acceptable.
//
// CancellationException is an Exception, so the catches rethrow it: a job cancelled
// at ON_DESTROY must complete as cancelled, not as "handled". Dialogs reached after
// the activity is gone are dropped by showImmersive().
fun AppCompatActivity.runUpdateFlow(
    setClickable: (Boolean) -> Unit = {},
    setSubtitle: (String) -> Unit = {},
) {
    val checker = UpdateChecker(this)

    fun showMessage(text: String) {
        AlertDialog.Builder(this)
            .setMessage(text)
            .setPositiveButton(R.string.ok, null)
            .create()
            .showImmersive()
    }

    fun showError(e: Exception) =
        showMessage(getString(R.string.update_failed, e.message ?: e.javaClass.simpleName))

    fun downloadAndInstall(release: ReleaseInfo) {
        setClickable(false)
        lifecycleScope.launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var lastUpdate = 0L
            try {
                startActivity(checker.installIntent(checker.download(release) { written, total ->
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate >= 500) {
                        val elapsed = (now - lastTime).coerceAtLeast(1)
                        val speedMBs = (written - lastBytes) * 1000.0 / elapsed / (1024.0 * 1024)
                        lastBytes = written
                        lastTime = now
                        lastUpdate = now
                        val pct = if (total > 0) "${written * 100 / total}% \u00b7 " else ""
                        runOnUiThread { setSubtitle(pct + "%.1f MB/s".format(speedMBs)) }
                    }
                }))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showError(e)
            } finally {
                setClickable(true)
            }
        }
    }

    fun promptInstall(release: ReleaseInfo) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.update_available, release.versionName))
            .setPositiveButton(R.string.update_download) { _, _ -> downloadAndInstall(release) }
            .setNegativeButton(R.string.cancel, null)
            .create()
            .showImmersive()
    }

    setClickable(false)
    lifecycleScope.launch {
        try {
            val release = checker.check()
            if (release == null) showMessage(getString(R.string.update_none)) else promptInstall(release)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            showError(e)
        } finally {
            setClickable(true)
        }
    }
}
