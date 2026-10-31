package de.codevoid.mototracklog.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

// Hides its children from the dpad focus finder without blocking touch focus.
// Overriding addFocusables keeps descendants out of the focusable list the finder walks,
// so dpad can never land on them; touch focus keeps working because
// View.requestFocus() (called from onTouchEvent) checks descendantFocusability, not this.
class TouchOnlyRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {
    override fun addFocusables(views: ArrayList<View>, direction: Int, focusableMode: Int) {
        // Intentionally empty: neither self nor descendants participate in dpad traversal.
    }
}
