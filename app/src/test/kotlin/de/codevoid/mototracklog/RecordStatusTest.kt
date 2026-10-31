package de.codevoid.mototracklog

import de.codevoid.mototracklog.service.LoggingState
import de.codevoid.mototracklog.ui.RecordStatus
import de.codevoid.mototracklog.ui.recordStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordStatusTest {

    private fun logging(
        paused: Boolean = false,
        gpsEnabled: Boolean = true,
        gnssRunning: Boolean = false,
        used: Int = 0,
    ) = LoggingState(
        runId = 1L,
        isPaused = paused,
        gpsEnabled = gpsEnabled,
        gnssRunning = gnssRunning,
        satellitesUsedInFix = used,
    )

    /** Defaults are "a working internal GPS on a permitted app", so each test names only its case. */
    private fun status(
        state: LoggingState = LoggingState(),
        external: Boolean = false,
        preciseLocation: Boolean = true,
        hasInternalGps: Boolean = true,
        internalGpsEnabled: Boolean = true,
    ) = recordStatus(state, external, preciseLocation, hasInternalGps, internalGpsEnabled)

    @Test
    fun idleIsReadyOnlyWhenNothingBlocksARun() {
        assertEquals(RecordStatus.Idle, status())
        assertEquals(RecordStatus.Unavailable, status(preciseLocation = false))
        assertEquals(RecordStatus.Disabled, status(internalGpsEnabled = false))
        assertEquals(RecordStatus.NoDevice, status(hasInternalGps = false))
    }

    /** Outermost obstacle first: absent hardware, then the Location switch, then the permission. */
    @Test
    fun theIdleObstaclesAreRankedOutermostFirst() {
        assertEquals(
            RecordStatus.NoDevice,
            status(preciseLocation = false, hasInternalGps = false, internalGpsEnabled = false),
        )
        assertEquals(
            RecordStatus.Disabled,
            status(preciseLocation = false, internalGpsEnabled = false),
        )
    }

    @Test
    fun anExternalReceiverIsUnaffectedByTheInternalChipset() {
        assertEquals(
            RecordStatus.Idle,
            status(external = true, hasInternalGps = false, internalGpsEnabled = false),
        )
    }

    @Test
    fun theIdleObstaclesNeverOverrideARunInProgress() {
        assertEquals(
            RecordStatus.Fix,
            status(
                state = logging(gnssRunning = true, used = 5),
                hasInternalGps = false,
                internalGpsEnabled = false,
            ),
        )
    }

    @Test
    fun pausedBeatsEverythingElse() {
        assertEquals(RecordStatus.Paused, status(state = logging(paused = true, gpsEnabled = false)))
    }

    @Test
    fun providerOffIsDisabledOnlyForTheInternalSource() {
        assertEquals(RecordStatus.Disabled, status(state = logging(gpsEnabled = false)))
        // The service starts an external run with gpsEnabled = false and drops it on a link loss.
        assertEquals(RecordStatus.ReceiverOff, status(state = logging(gpsEnabled = false), external = true))
    }

    @Test
    fun engineStoppedIsReceiverOffForBothSources() {
        assertEquals(RecordStatus.ReceiverOff, status(state = logging(gnssRunning = false)))
        assertEquals(RecordStatus.ReceiverOff, status(state = logging(gnssRunning = false), external = true))
    }

    @Test
    fun runningEngineIsSearchingUntilSatellitesAreUsed() {
        assertEquals(RecordStatus.Searching, status(state = logging(gnssRunning = true, used = 0)))
        assertEquals(RecordStatus.Fix, status(state = logging(gnssRunning = true, used = 5)))
    }

    @Test
    fun revokedPreciseLocationDoesNotHideTheLiveState() {
        assertEquals(
            RecordStatus.Fix,
            status(state = logging(gnssRunning = true, used = 5), preciseLocation = false),
        )
    }
}
