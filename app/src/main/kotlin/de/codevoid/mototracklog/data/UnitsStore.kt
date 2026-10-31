package de.codevoid.mototracklog.data

import android.content.Context

class UnitsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isMetric: Boolean
        get() = prefs.getBoolean(KEY_METRIC, true)
        set(value) { prefs.edit().putBoolean(KEY_METRIC, value).apply() }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_METRIC = "units_metric"
    }
}
