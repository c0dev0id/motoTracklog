package de.codevoid.mototracklog.data

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

class ThemeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isDark: Boolean
        get() = prefs.getBoolean(KEY_DARK, true)
        set(value) {
            prefs.edit().putBoolean(KEY_DARK, value).apply()
            applyNightMode()
        }

    fun applyNightMode() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDark) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_DARK = "dark_theme"
    }
}
