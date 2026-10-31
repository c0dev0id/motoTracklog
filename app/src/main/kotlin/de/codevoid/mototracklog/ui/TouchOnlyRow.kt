package de.codevoid.mototracklog.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

// A LinearLayout whose children take focus only by asking for it themselves, as a touch
// does. Focus search, default focus and a child becoming focusable never move focus in,
// so the remote cannot reach anything inside.
class TouchOnlyRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {}

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean =
        false

    override fun focusableViewAvailable(v: View) {}
}
