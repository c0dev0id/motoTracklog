package de.codevoid.mototracklog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.data.ExportFilterStore
import de.codevoid.mototracklog.data.FilterSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExportFilterStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun defaultsAreTenMetresAccuracyAndNoThinning() {
        assertEquals(FilterSettings(accuracyMeters = 10, distanceMeters = 0f, timeSeconds = 0), ExportFilterStore(context).filters)
    }

    @Test
    fun filtersRoundTripThroughANewInstance() {
        val chosen = FilterSettings(accuracyMeters = 20, distanceMeters = 5f, timeSeconds = 30)
        ExportFilterStore(context).filters = chosen

        assertEquals(chosen, ExportFilterStore(context).filters)
    }
}
