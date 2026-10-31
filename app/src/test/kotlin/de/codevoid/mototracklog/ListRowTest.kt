package de.codevoid.mototracklog

import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.databinding.ItemListRowBinding
import de.codevoid.mototracklog.ui.ListRowItem
import de.codevoid.mototracklog.ui.bind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Binding the same row twice is what a recycled view holder does. */
@RunWith(RobolectricTestRunner::class)
class ListRowTest {

    private val row = ItemListRowBinding.inflate(LayoutInflater.from(ApplicationProvider.getApplicationContext()))

    @Test
    fun tapAndLongPressReachTheirCallbacks() {
        var taps = 0
        var longPresses = 0
        row.bind(ListRowItem(title = "Run", onClick = { taps++ }, onLongClick = { longPresses++ }))

        row.root.performClick()
        row.root.performLongClick()

        assertEquals(1, taps)
        assertEquals(1, longPresses)
    }

    @Test
    fun aRowWithoutAMenuIsNotLongClickableAfterOneWithAMenu() {
        row.bind(ListRowItem(title = "Older run", onLongClick = {}))
        row.bind(ListRowItem(title = "Run being recorded"))

        assertFalse(row.root.isLongClickable)
    }

    @Test
    fun theSubtitleIsHiddenWhenTheNextItemHasNone() {
        row.bind(ListRowItem(title = "Run", subtitle = "1 h 23 min"))
        assertEquals(View.VISIBLE, row.rowSubtitle.visibility)

        row.bind(ListRowItem(title = "Run"))
        assertEquals(View.GONE, row.rowSubtitle.visibility)
    }
}
