package de.codevoid.mototracklog.data

import android.content.Context

/**
 * The run being recorded, kept so a process kill or a reboot resumes the same run. Only the
 * user's Stop clears it. Written and read by the service on its logger thread; the boot receiver
 * reads it once.
 */
class ActiveRunStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Run id currently being recorded, or null when no run is active. */
    val runId: Long?
        get() = prefs.getLong(KEY_RUN, NO_RUN).takeIf { it != NO_RUN }

    /** A newly started run is never paused, so this clears any flag a killed run left behind. */
    fun start(id: Long) {
        prefs.edit().putLong(KEY_RUN, id).putBoolean(KEY_PAUSED, false).apply()
    }

    fun clear() {
        prefs.edit().remove(KEY_RUN).remove(KEY_PAUSED).apply()
    }

    /**
     * Persisted next to the run id so a reboot or a process kill comes back paused instead of
     * silently recording again.
     */
    var isPaused: Boolean
        get() = prefs.getBoolean(KEY_PAUSED, false)
        set(value) { prefs.edit().putBoolean(KEY_PAUSED, value).apply() }

    private companion object {
        const val PREFS = "recording"
        const val KEY_RUN = "active_run_id"
        const val KEY_PAUSED = "active_run_paused"
        const val NO_RUN = -1L
    }
}
