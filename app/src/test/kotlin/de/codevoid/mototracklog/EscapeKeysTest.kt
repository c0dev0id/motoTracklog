package de.codevoid.mototracklog

import android.view.KeyEvent
import android.view.ViewConfiguration
import de.codevoid.mototracklog.ui.EscapeKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Events go through `KeyEvent.dispatch` with a real `KeyEvent.DispatcherState` — the same
 * path an activity's key handling takes — rather than through the two methods directly
 * with hand-set flags, so the framework's tracking machinery is exercised rather than
 * assumed.
 *
 * The case that matters most is [holdIsRecognisedWithoutAnyKeyRepeat]: the handlebar
 * remote reports a plain down and up with no repeats, which is why the framework's
 * `onKeyLongPress` never fired for it and holding ESC did nothing on the bike while
 * working from a USB keyboard.
 */
@RunWith(RobolectricTestRunner::class)
class EscapeKeysTest {

    private var shortPresses = 0
    private var longPresses = 0
    private val timeout = ViewConfiguration.getLongPressTimeout().toLong()
    private val keys = EscapeKeys(
        onLongPress = { longPresses++ },
        onShortPress = { shortPresses++ },
    )

    private val state = KeyEvent.DispatcherState()

    // Wired exactly as the activities wire it: onKeyLongPress is left at
    // the framework default, since EscapeKeys no longer uses it.
    private val callback = object : KeyEvent.Callback {
        override fun onKeyDown(keyCode: Int, event: KeyEvent) = keys.onKeyDown(keyCode, event)
        override fun onKeyLongPress(keyCode: Int, event: KeyEvent) = false
        override fun onKeyUp(keyCode: Int, event: KeyEvent) = keys.onKeyUp(keyCode, event)
        override fun onKeyMultiple(keyCode: Int, count: Int, event: KeyEvent) = false
    }

    private fun dispatch(
        action: Int,
        keyCode: Int = KeyEvent.KEYCODE_ESCAPE,
        repeatCount: Int = 0,
        flags: Int = 0,
        heldMs: Long = 0L,
    ): Boolean {
        // downTime 0, eventTime heldMs — the gap EscapeKeys measures.
        val event = KeyEvent(0L, heldMs, action, keyCode, repeatCount, 0, 0, 0, flags)
        return event.dispatch(callback, state, this)
    }

    private fun down() = dispatch(KeyEvent.ACTION_DOWN)

    private fun up(heldMs: Long = 0L) = dispatch(KeyEvent.ACTION_UP, heldMs = heldMs)

    @Test
    fun tapRunsTheShortActionOnKeyUp() {
        assertTrue(down())
        assertEquals("nothing may happen before the key comes back up", 0, shortPresses)

        assertTrue(up(heldMs = 80))
        assertEquals(1, shortPresses)
        assertEquals(0, longPresses)
    }

    @Test
    fun holdIsRecognisedWithoutAnyKeyRepeat() {
        // Exactly what the handlebar remote sends: down, nothing, up.
        down()
        up(heldMs = timeout + 100)
        assertEquals(1, longPresses)
        assertEquals(0, shortPresses)
    }

    @Test
    fun holdIsRecognisedWithKeyRepeatsToo() {
        // What a USB keyboard sends. The repeats change nothing; the UP still decides,
        // and it must decide exactly once.
        down()
        dispatch(KeyEvent.ACTION_DOWN, repeatCount = 1, flags = KeyEvent.FLAG_LONG_PRESS)
        dispatch(KeyEvent.ACTION_DOWN, repeatCount = 2)
        up(heldMs = timeout + 100)
        assertEquals(1, longPresses)
        assertEquals(0, shortPresses)
    }

    @Test
    fun theThresholdItselfCountsAsALongPress() {
        down()
        up(heldMs = timeout)
        assertEquals(1, longPresses)
        assertEquals(0, shortPresses)
    }

    @Test
    fun justUnderTheThresholdIsAShortPress() {
        down()
        up(heldMs = timeout - 1)
        assertEquals(1, shortPresses)
        assertEquals(0, longPresses)
    }

    @Test
    fun anUntrackedUpRunsNothing() {
        // No DOWN reached this handler — the press started before the window had focus.
        assertTrue(up(heldMs = timeout + 100))
        assertEquals(0, shortPresses)
        assertEquals(0, longPresses)
    }

    @Test
    fun otherKeysAreLeftAlone() {
        val code = KeyEvent.KEYCODE_DPAD_CENTER
        assertFalse(dispatch(KeyEvent.ACTION_DOWN, keyCode = code))
        assertFalse(dispatch(KeyEvent.ACTION_UP, keyCode = code, heldMs = 1000))
        assertEquals(0, shortPresses)
        assertEquals(0, longPresses)
    }
}
