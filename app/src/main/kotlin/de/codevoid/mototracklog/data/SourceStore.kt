package de.codevoid.mototracklog.data

import android.content.Context

/** Where a run's fixes come from: the device's own GNSS chipset or a paired Bluetooth receiver. */
class SourceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The paired receiver's MAC address, or null for the internal GPS. */
    var bluetoothAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)
        set(value) {
            if (value != null) prefs.edit().putString(KEY_ADDRESS, value).apply()
            else prefs.edit().remove(KEY_ADDRESS).apply()
        }

    val isExternal: Boolean get() = bluetoothAddress != null

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ADDRESS = "source_bluetooth_address"
    }
}
