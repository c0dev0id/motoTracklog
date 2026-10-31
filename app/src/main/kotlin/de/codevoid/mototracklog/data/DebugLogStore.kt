package de.codevoid.mototracklog.data

import android.content.Context

/** Whether a run tees its raw NMEA stream to a shareable debug log. */
class DebugLogStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ENABLED = "debug_nmea_log"
    }
}
