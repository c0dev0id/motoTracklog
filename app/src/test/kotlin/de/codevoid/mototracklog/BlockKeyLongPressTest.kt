package de.codevoid.mototracklog

import android.content.Context
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import de.codevoid.mototracklog.ui.blockKeyLongPress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockKeyLongPressTest {

    private var clicks = 0
    private val view = View(ApplicationProvider.getApplicationContext<Context>()).apply {
        setOnClickListener { clicks++ }
        blockKeyLongPress()
    }

    private fun key(action: Int, keyCode: Int = KeyEvent.KEYCODE_ENTER, flags: Int = 0) {
        view.dispatchKeyEvent(
            KeyEvent(0L, 0L, action, keyCode, 0, 0, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, flags)
        )
    }

    @Test
    fun enterClicksOnRelease() {
        key(KeyEvent.ACTION_DOWN)
        assertEquals(0, clicks)
        key(KeyEvent.ACTION_UP)
        assertEquals(1, clicks)
    }

    // View treats Space as a confirm key as well; left out, it would arm the long press.
    @Test
    fun spaceClicksOnReleaseToo() {
        key(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        key(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)
        assertEquals(1, clicks)
    }

    // The press began in another window; the release that ends it is not this view's.
    @Test
    fun aReleaseWithoutAPressDoesNotClick() {
        key(KeyEvent.ACTION_UP)
        assertEquals(0, clicks)
    }

    // Focus left the window mid-press and the system ended the press with a canceled UP.
    @Test
    fun aCanceledReleaseDoesNotClick() {
        key(KeyEvent.ACTION_DOWN)
        key(KeyEvent.ACTION_UP, flags = KeyEvent.FLAG_CANCELED)
        assertEquals(0, clicks)
        assertFalse(view.isPressed)
    }
}
