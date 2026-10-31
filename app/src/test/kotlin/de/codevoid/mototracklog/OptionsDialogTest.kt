package de.codevoid.mototracklog

import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.ui.showOptionsDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OptionsDialogTest {

    private val options = listOf("Landscape", "Portrait", "Reverse landscape", "Sensor")
    private var picked = mutableListOf<Int>()

    private fun show(selectedIndex: Int = 0) = showOptionsDialog(
        context = ApplicationProvider.getApplicationContext(),
        title = "Orientation",
        options = options,
        selectedIndex = selectedIndex,
        onPick = { picked.add(it) },
    )

    private fun rows(dialog: android.app.Dialog): List<TextView> {
        val container = dialog.findViewById<ViewGroup>(R.id.optionRows)
        return (0 until container.childCount).map { container.getChildAt(it) as TextView }
    }

    @Test
    fun showsTheTitleAndOneRowPerOptionInOrder() {
        val dialog = show()
        assertTrue(dialog.isShowing)
        assertEquals("Orientation", dialog.findViewById<TextView>(R.id.optionsTitle).text.toString())
        assertEquals(options, rows(dialog).map { it.text.toString() })
    }

    @Test
    fun pickingARowReportsItsIndexOnceAndDismisses() {
        val dialog = show()
        rows(dialog)[2].performClick()
        assertEquals(listOf(2), picked)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun pickingTheAlreadySelectedRowStillReportsIt() {
        // The caller decides whether re-applying the current value is a no-op; the dialog
        // does not silently swallow the choice.
        val dialog = show(selectedIndex = 1)
        rows(dialog)[1].performClick()
        assertEquals(listOf(1), picked)
        assertFalse(dialog.isShowing)
    }

    @Test
    fun onlyTheSelectedRowIsMarkedSelected() {
        // With the tile hidden behind the dialog, this is the only indication of where the
        // setting currently stands. tile_background and dialog_action_text draw the state.
        val dialog = show(selectedIndex = 3)
        assertEquals(listOf(false, false, false, true), rows(dialog).map { it.isSelected })
    }

    @Test
    fun theSelectedRowIsDrawnDifferentlyFromTheRest() {
        val dialog = show(selectedIndex = 3)
        val colours = rows(dialog).map { it.currentTextColor }
        assertNotEquals(colours[0], colours[3])
        assertEquals(colours[0], colours[1])
        assertEquals(colours[0], colours[2])
    }

    @Test
    fun anOutOfRangeSelectionSimplyMarksNothing() {
        val dialog = show(selectedIndex = -1)
        assertTrue(rows(dialog).none { it.isSelected })
    }

    @Test
    fun noRowIsClickedUntilOneIsClicked() {
        show()
        assertEquals(emptyList<Int>(), picked)
    }

    // A touch opens the picker; the remote must not be able to change the setting.
    @Test
    fun noRowCanTakeFocus() {
        assertTrue(rows(show()).none { it.isFocusable })
    }
}
