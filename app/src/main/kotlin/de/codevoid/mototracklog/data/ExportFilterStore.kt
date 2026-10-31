package de.codevoid.mototracklog.data

import android.content.Context

/** Export filter values. Defaults: accuracy 10 m, distance and time off. */
data class FilterSettings(
    val accuracyMeters: Int = 10,
    val distanceMeters: Float = 0f,
    val timeSeconds: Int = 0,
)

class ExportFilterStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var filters: FilterSettings
        get() = FilterSettings(
            accuracyMeters = prefs.getInt(KEY_ACCURACY, DEFAULTS.accuracyMeters),
            distanceMeters = prefs.getFloat(KEY_DISTANCE, DEFAULTS.distanceMeters),
            timeSeconds = prefs.getInt(KEY_TIME, DEFAULTS.timeSeconds),
        )
        set(value) {
            prefs.edit()
                .putInt(KEY_ACCURACY, value.accuracyMeters)
                .putFloat(KEY_DISTANCE, value.distanceMeters)
                .putInt(KEY_TIME, value.timeSeconds)
                .apply()
        }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ACCURACY = "filter_accuracy_m"
        private const val KEY_DISTANCE = "filter_distance_m"
        private const val KEY_TIME = "filter_time_s"
        private val DEFAULTS = FilterSettings()
    }
}
