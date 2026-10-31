package de.codevoid.mototracklog

import android.app.Application
import de.codevoid.mototracklog.data.ThemeStore
import de.codevoid.mototracklog.update.UpdateChecker
import kotlin.concurrent.thread

class MotoTracklogApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Before any activity is created, so the saved theme survives restarts.
        ThemeStore(this).applyNightMode()
        // The update that was just installed has served its purpose. A plain thread keeps a
        // possibly cold cache-dir read off the first frame.
        thread(name = "delete-installed-update") { UpdateChecker(this).deleteInstalledUpdate() }
    }
}
