package de.codevoid.mototracklog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.data.ActiveRunStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActiveRunStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun noRunIsActiveOnAFreshInstall() {
        assertNull(ActiveRunStore(context).runId)
        assertFalse(ActiveRunStore(context).isPaused)
    }

    @Test
    fun theRunAndItsPauseSurviveANewInstance() {
        // A second instance is what a reboot or a sticky restart reads.
        ActiveRunStore(context).apply {
            start(42L)
            isPaused = true
        }

        val after = ActiveRunStore(context)
        assertEquals(42L, after.runId)
        assertTrue(after.isPaused)
    }

    @Test
    fun startingARunClearsAPauseLeftBehind() {
        // A Force-Stop never reaches clear(), so a stale pause flag can outlive its run.
        val store = ActiveRunStore(context)
        store.start(1L)
        store.isPaused = true

        store.start(2L)

        assertEquals(2L, store.runId)
        assertFalse(store.isPaused)
    }

    @Test
    fun clearDropsTheRunAndItsPause() {
        val store = ActiveRunStore(context)
        store.start(1L)
        store.isPaused = true

        store.clear()

        assertNull(store.runId)
        assertFalse(store.isPaused)
    }
}
