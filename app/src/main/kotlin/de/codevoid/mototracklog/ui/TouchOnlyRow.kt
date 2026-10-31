package de.codevoid.mototracklog.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * A row whose children are reachable by touch only. Each override closes one path by which the
 * platform hands focus to a view without a touch; a child's own requestFocus(), which a tap on a
 * text field makes, still works.
 */
class TouchOnlyRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    /** Dpad and Tab search. */
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) = Unit

    /** Default focus: window start, leaving touch mode, the focused view going away. */
    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean = false

    /** A child that becomes focusable while nothing has focus. */
    override fun focusableViewAvailable(v: View) = Unit
}
