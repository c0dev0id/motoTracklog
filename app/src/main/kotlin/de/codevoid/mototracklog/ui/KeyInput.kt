package de.codevoid.mototracklog.ui

import android.view.KeyEvent
import android.view.View
import android.view.ViewConfiguration

// Route DPAD_CENTER / ENTER through performClick() on ACTION_UP without arming the
// framework's long-press timer. Touch long-press (setOnLongClickListener) still works;
// keyboard/remote long-press does not, which stops a held remote button from reaching
// touch-only destinations (app info, configuration) that a rider can't navigate back
// out of without touching the screen.
fun View.blockKeyLongPress() {
    setOnKeyListener { view, keyCode, event ->
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) view.isPressed = true
                        true
                    }
                    KeyEvent.ACTION_UP -> {
                        view.isPressed = false
                        view.performClick()
                        true
                    }
                    else -> true
                }
            }
            else -> false
        }
    }
}

/**
 * ESC from the handlebar remote (and any USB/BT keyboard): a short press runs
 * [onShortPress] — the screen's own "back", since Android doesn't route ESC to the back
 * dispatcher — while holding the key for [longPressTimeoutMs] or more runs [onLongPress]
 * instead.
 *
 * Both decisions are made on ACTION_UP from the key's own timestamps
 * (`eventTime - downTime`), **not** from the framework's `onKeyLongPress`. That callback
 * is delivered off the first key *repeat*, so it only ever arrives from an input device
 * that auto-repeats while held. The handlebar remote does not — it reports a plain down
 * and up — so the framework never reported a long press and holding ESC did nothing,
 * while the identical code worked from a USB keyboard. Measuring the gap ourselves
 * behaves the same on every device, which is the point: this action must not depend on a
 * capability of whatever is plugged in.
 *
 * [onKeyDown] still claims the DOWN and calls `startTracking()` — that is what makes the
 * UP arrive with `isTracking` set, which is how an UP whose DOWN went to another window
 * (a press that began before this one had focus) is told apart from a real press and
 * ignored. Both methods must be wired from the activity; there is no longer an
 * `onKeyLongPress` to wire.
 *
 * A remote that emits an instantaneous down/up pair on release, rather than holding the
 * key down, still cannot produce a long press — there is no elapsed time to measure. The
 * short press works either way.
 */
class EscapeKeys(
    private val onLongPress: () -> Unit,
    private val onShortPress: () -> Unit = {},
    private val longPressTimeoutMs: Long = ViewConfiguration.getLongPressTimeout().toLong(),
) {
    fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        if (event.repeatCount == 0) event.startTracking()
        return true
    }

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_ESCAPE) return false
        // Not tracking: the DOWN went to someone else, so this UP ends a press that was
        // never ours. Canceled: something else already consumed the press.
        if (!event.isTracking || event.isCanceled) return true
        if (event.eventTime - event.downTime >= longPressTimeoutMs) onLongPress() else onShortPress()
        return true
    }
}
