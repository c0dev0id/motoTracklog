package de.codevoid.mototracklog

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ColorContrastTest {

    // Every text colour on every fill it is drawn on: WCAG AA for normal text.
    private val textPairs = listOf(
        R.color.on_surface to R.color.background,
        R.color.on_surface to R.color.tile_default,
        R.color.on_surface to R.color.tile_pressed,
        R.color.on_surface_muted to R.color.background,
        R.color.on_surface_muted to R.color.tile_default,
        R.color.on_surface_muted to R.color.tile_pressed,
        R.color.on_tile_focused to R.color.tile_focused,
    )

    // The status word on the Record screen is large text: 3:1.
    private val largeTextPairs = listOf(
        R.color.tile_focused to R.color.background,
        R.color.alert to R.color.background,
    )

    // The focus ring is the remote's only position marker: 3:1 for a meaningful mark.
    private val markPairs = listOf(
        R.color.tile_focused to R.color.tile_default,
        R.color.tile_focused to R.color.background,
    )

    @Test
    fun theLightThemeMeetsEveryMinimum() {
        assertEquals(emptyList<String>(), failures(themed(night = false)))
    }

    @Test
    fun theDarkThemeMeetsEveryMinimum() {
        assertEquals(emptyList<String>(), failures(themed(night = true)))
    }

    @Test
    fun theTwoThemesResolveDifferentPalettes() {
        // Guards the two tests above against both silently reading the same theme.
        assertNotEquals(
            ContextCompat.getColor(themed(night = false), R.color.background),
            ContextCompat.getColor(themed(night = true), R.color.background),
        )
    }

    // An explicit uiMode, so the result does not depend on the night mode the Application
    // applies at start-up.
    private fun themed(night: Boolean): Context {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val nightBits = if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        val config = Configuration(app.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        }
        return app.createConfigurationContext(config)
    }

    private fun failures(context: Context): List<String> {
        fun ratio(pair: Pair<Int, Int>) = ColorUtils.calculateContrast(
            ContextCompat.getColor(context, pair.first),
            ContextCompat.getColor(context, pair.second),
        )
        fun describe(pair: Pair<Int, Int>) = "%s on %s: %.2f".format(
            context.resources.getResourceEntryName(pair.first),
            context.resources.getResourceEntryName(pair.second),
            ratio(pair),
        )
        return textPairs.filter { ratio(it) < 4.5 }.map(::describe) +
            largeTextPairs.filter { ratio(it) < 3.0 }.map(::describe) +
            markPairs.filter { ratio(it) < 3.0 }.map(::describe)
    }
}
