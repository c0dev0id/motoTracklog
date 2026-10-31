package de.codevoid.mototracklog.ui

import de.codevoid.mototracklog.service.LoggingState

/** The one word the Record screen leads with. */
enum class RecordStatus {
    /** Not recording, ready to start. */
    Idle,
    /** Not recording: the internal source is chosen but this device has no GPS chipset. */
    NoDevice,
    /** Not recording and precise location is denied, so Start is refused. */
    Unavailable,
    Paused,
    /** Internal source: Location is switched off in system settings. Blocks Start while idle. */
    Disabled,
    /** The receiver is not delivering: Bluetooth link down, or the GNSS engine stopped. */
    ReceiverOff,
    Searching,
    /** Recording with a fix: points are arriving. */
    Fix,
}

/**
 * Derives the status from the live state. [external] matters for `gpsEnabled`: the service
 * initialises it as `isInternalSource && providerEnabled` and drops it on a Bluetooth disconnect,
 * so for an external receiver a false value means "receiver not connected", never "GPS disabled".
 *
 * [hasInternalGps] and [internalGpsEnabled] describe the internal source only, and both apply
 * only while idle — a run already recording reports what it is doing. The idle order is
 * outermost obstacle first: absent hardware, then the system-wide Location switch, then the
 * app's own permission, because that is the order in which fixing one can matter.
 *
 * Every idle answer but [RecordStatus.Idle] names something that stops a run, which is what lets
 * the Start tile be gated on this one value instead of on a second, drifting predicate.
 */
fun recordStatus(
    state: LoggingState,
    external: Boolean,
    preciseLocation: Boolean,
    hasInternalGps: Boolean,
    internalGpsEnabled: Boolean,
): RecordStatus = when {
    !state.isLogging -> when {
        !external && !hasInternalGps -> RecordStatus.NoDevice
        !external && !internalGpsEnabled -> RecordStatus.Disabled
        !preciseLocation -> RecordStatus.Unavailable
        else -> RecordStatus.Idle
    }
    state.isPaused -> RecordStatus.Paused
    !state.gpsEnabled -> if (external) RecordStatus.ReceiverOff else RecordStatus.Disabled
    !state.gnssRunning -> RecordStatus.ReceiverOff
    state.hasFix -> RecordStatus.Fix
    else -> RecordStatus.Searching
}
