package de.codevoid.mototracklog

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.databinding.ActivityExportBinding
import de.codevoid.mototracklog.databinding.ItemListRowBinding
import de.codevoid.mototracklog.databinding.ItemTileBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * Off the Record screen the remote reaches nothing: Runs, Export and the settings grid are
 * operated by touch, and Escape leaves them.
 */
@RunWith(RobolectricTestRunner::class)
class TouchOnlyScreensTest {

    private val inflater = LayoutInflater.from(ApplicationProvider.getApplicationContext<Context>())

    // RecyclerView makes itself focusable in its constructor; the screen has to undo that.
    @Test
    fun theRunsScreenOffersTheRemoteNothing() {
        val activity = Robolectric.buildActivity(RunsActivity::class.java).setup().get()
        val focusables = ArrayList<View>()
        activity.window.decorView.addFocusables(focusables, View.FOCUS_DOWN, View.FOCUSABLES_ALL)
        assertEquals(emptyList<View>(), focusables)
    }

    @Test
    fun runRowsAreTouchOnly() {
        assertFalse(ItemListRowBinding.inflate(inflater).root.isFocusable)
    }

    @Test
    fun settingsAndFilterTilesAreTouchOnly() {
        assertFalse(ItemTileBinding.inflate(inflater).root.isFocusable)
    }

    @Test
    fun shareGpxIsTouchOnly() {
        assertFalse(ActivityExportBinding.inflate(inflater).shareButton.isFocusable)
    }
}
