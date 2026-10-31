package de.codevoid.mototracklog

import android.app.Activity
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import de.codevoid.mototracklog.ui.fitContentToSystemBars
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImmersiveTest {

    private fun fittedContent(): View {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(FrameLayout(activity))
        activity.window.fitContentToSystemBars()
        return activity.findViewById(android.R.id.content)
    }

    @Test
    fun contentIsPaddedByVisibleBarsAndCutout() {
        val content = fittedContent()
        val insets = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(32, 0, 0, 0))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, insets)

        assertEquals(listOf(32, 0, 0, 48), with(content) {
            listOf(paddingLeft, paddingTop, paddingRight, paddingBottom)
        })
    }

    @Test
    fun hidingTheNavBarDropsItsPadding() {
        val content = fittedContent()
        val navBarShown = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, 0, 48))
            .build()
        ViewCompat.dispatchApplyWindowInsets(content, navBarShown)
        ViewCompat.dispatchApplyWindowInsets(content, WindowInsetsCompat.Builder().build())

        assertEquals(0, content.paddingBottom)
    }
}
