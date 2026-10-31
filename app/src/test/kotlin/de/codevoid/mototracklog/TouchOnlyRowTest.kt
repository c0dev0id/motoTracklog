package de.codevoid.mototracklog

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import androidx.test.platform.app.InstrumentationRegistry
import de.codevoid.mototracklog.ui.TouchOnlyRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * A row above one other focusable view, in a window out of touch mode — the state after the
 * first remote key, and the one in which the framework hands out focus by itself. Each test
 * is one of the framework's ways of moving focus into a subtree without the child asking.
 */
@RunWith(RobolectricTestRunner::class)
class TouchOnlyRowTest {

    private lateinit var activity: Activity
    private lateinit var root: LinearLayout
    private lateinit var inside: View
    private lateinit var outside: View

    @Before
    fun showRowAboveAnotherView() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        inside = focusableView()
        outside = focusableView()
        root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TouchOnlyRow(activity).apply { addView(inside) })
            addView(outside)
        }
        activity.setContentView(root)
    }

    private fun focusableView() = View(activity).apply {
        layoutParams = LinearLayout.LayoutParams(SIZE, SIZE)
        isFocusable = true
    }

    @Test
    fun focusSearchSkipsTheRow() {
        val focusables = ArrayList<View>()
        root.addFocusables(focusables, View.FOCUS_DOWN)
        assertEquals(listOf(outside), focusables)
    }

    // The path the first remote key takes when it leaves touch mode.
    @Test
    fun defaultFocusSkipsTheRow() {
        root.restoreDefaultFocus()
        assertTrue(outside.isFocused)
    }

    // The path a RecyclerView rebind takes when it removes the focused tile.
    @Test
    fun refocusAfterTheFocusedViewIsRemovedSkipsTheRow() {
        val removed = focusableView()
        root.addView(removed)
        assertTrue(removed.requestFocus())
        root.removeView(removed)
        assertTrue(outside.isFocused)
    }

    // The window hands focus to a view that becomes focusable while nothing has focus.
    // The view outside the row shows that it would have.
    @Test
    fun aChildBecomingFocusableIsNotHandedFocus() {
        inside.isEnabled = false
        outside.isEnabled = false
        assertNull(root.findFocus())

        inside.isEnabled = true
        assertNull(root.findFocus())

        outside.isEnabled = true
        assertTrue(outside.isFocused)
    }

    // A touch focuses the touched view directly.
    @Test
    fun aChildStillTakesFocusWhenItAsks() {
        assertTrue(inside.requestFocus())
        assertTrue(inside.isFocused)
    }

    private companion object {
        const val SIZE = 100
    }
}
