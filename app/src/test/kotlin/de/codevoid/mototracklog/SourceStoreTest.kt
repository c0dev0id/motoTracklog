package de.codevoid.mototracklog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.data.SourceStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun theInternalGpsIsTheDefault() {
        val store = SourceStore(context)
        assertFalse(store.isExternal)
        assertNull(store.bluetoothAddress)
    }

    @Test
    fun aReceiverIsStoredWithItsName() {
        SourceStore(context).useBluetooth("00:11:22:33:44:55", "GNS 3000")

        val store = SourceStore(context)
        assertTrue(store.isExternal)
        assertEquals("00:11:22:33:44:55", store.bluetoothAddress)
        assertEquals("GNS 3000", store.bluetoothName)
    }

    @Test
    fun switchingBackToInternalForgetsTheReceiver() {
        val store = SourceStore(context)
        store.useBluetooth("00:11:22:33:44:55", "GNS 3000")

        store.useInternal()

        assertFalse(store.isExternal)
        assertNull(store.bluetoothName)
    }
}
