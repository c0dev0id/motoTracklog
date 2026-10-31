package de.codevoid.mototracklog

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.ui.TouchOnlyRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A row first in the tree, then a sibling outside it: the order in which the platform's default
 * focus would otherwise pick the row's child. The test views are focusable in touch mode, so the
 * results do not depend on the touch mode Robolectric starts in.
 */
@RunWith(RobolectricTestRunner::class)
class TouchOnlyRowTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val offered = mutableListOf<View>()

    // Stands in for the window, which focuses the views offered to it while nothing has focus.
    private val root = object : LinearLayout(context) {
        override fun focusableViewAvailable(v: View) {
            offered += v
        }
    }
    private val row = TouchOnlyRow(context)
    private val inside = focusableView()
    private val outside = focusableView()

    init {
        row.addView(inside, SIZE, SIZE)
        root.addView(row, SIZE, SIZE)
        root.addView(outside, SIZE, SIZE)
    }

    @Test
    fun dpadSearchFindsOnlyTheViewOutsideTheRow() {
        val focusables = ArrayList<View>()
        root.addFocusables(focusables, View.FOCUS_DOWN)

        assertEquals(listOf(outside), focusables)
    }

    @Test
    fun defaultFocusPassesTheRowBy() {
        assertTrue(root.requestFocus())

        assertTrue(outside.isFocused)
        assertFalse(inside.isFocused)
    }

    @Test
    fun aChildStillTakesFocusWhenItAsksForIt() {
        assertTrue(inside.requestFocus())
    }

    @Test
    fun aChildThatBecomesFocusableIsNotOfferedForFocus() {
        val laterInside = View(context)
        val laterOutside = View(context)
        row.addView(laterInside, SIZE, SIZE)
        root.addView(laterOutside, SIZE, SIZE)
        val spec = MeasureSpec.makeMeasureSpec(SIZE * 4, MeasureSpec.EXACTLY)
        root.measure(spec, spec)
        root.layout(0, 0, SIZE * 4, SIZE * 4)
        offered.clear()

        laterInside.isFocusable = true
        laterOutside.isFocusable = true

        assertEquals(listOf(laterOutside), offered)
    }

    private fun focusableView() = View(context).apply {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    private companion object {
        const val SIZE = 10
    }
}
