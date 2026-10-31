package de.codevoid.mototracklog

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Precise location. A coarse-only grant still lets GPS_PROVIDER be requested, but its fixes are
 * fuzzed and throttled to one per ten minutes and GNSS status never arrives.
 */
fun Context.hasPreciseLocation(): Boolean = isGranted(Manifest.permission.ACCESS_FINE_LOCATION)

/** Location for a service started while no screen is visible: a runtime grant from Android 10. */
fun Context.hasBackgroundLocation(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

/** Paired devices and RFCOMM sockets: a runtime grant from Android 12, granted at install before. */
fun Context.hasBluetoothAccess(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S || isGranted(Manifest.permission.BLUETOOTH_CONNECT)

private fun Context.isGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
