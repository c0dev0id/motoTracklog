package de.codevoid.mototracklog.data

import android.content.Context

/** Where a run's fixes come from: the device's own GNSS chipset or a paired Bluetooth receiver. */
class SourceStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The paired receiver's MAC address, or null for the internal GPS. */
    val bluetoothAddress: String?
        get() = prefs.getString(KEY_ADDRESS, null)

    /** The receiver's name as it was when picked; shown instead of the address. */
    val bluetoothName: String?
        get() = prefs.getString(KEY_NAME, null)

    val isExternal: Boolean get() = bluetoothAddress != null

    fun useInternal() {
        prefs.edit().remove(KEY_ADDRESS).remove(KEY_NAME).apply()
    }

    fun useBluetooth(address: String, name: String) {
        prefs.edit().putString(KEY_ADDRESS, address).putString(KEY_NAME, name).apply()
    }

    companion object {
        private const val PREFS = "settings"
        private const val KEY_ADDRESS = "source_bluetooth_address"
        private const val KEY_NAME = "source_bluetooth_name"
    }
}
