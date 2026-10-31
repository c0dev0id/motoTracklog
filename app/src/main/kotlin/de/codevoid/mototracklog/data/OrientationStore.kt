package de.codevoid.mototracklog.data

import android.content.Context
import android.content.pm.ActivityInfo

class OrientationStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // Sensor by default: the device is landscape-mounted, but an app that cannot
    // follow the screen it is on is worse out of the box than one that can. An install
    // that has never set the option follows the accelerometer; anything explicitly chosen
    // is already stored and is unaffected.
    var orientation: Int
        get() = prefs.getInt(KEY, ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR)
        set(value) { prefs.edit().putInt(KEY, value).apply() }

    companion object {
        private const val PREFS = "settings"
        private const val KEY = "orientation"
    }
}
