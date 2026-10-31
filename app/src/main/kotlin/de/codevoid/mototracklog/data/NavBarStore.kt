package de.codevoid.mototracklog.data

import android.content.Context

class NavBarStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var showNavBar: Boolean
        get() = prefs.getBoolean(KEY_SHOW_NAV_BAR, false)
        set(value) { prefs.edit().putBoolean(KEY_SHOW_NAV_BAR, value).apply() }

    companion object {
        private const val PREFS = "settings"
        const val KEY_SHOW_NAV_BAR = "show_nav_bar"
    }
}
