package de.codevoid.mototracklog

import de.codevoid.mototracklog.ui.DASH
import de.codevoid.mototracklog.ui.Formats
import de.codevoid.mototracklog.ui.displaySpeed
import de.codevoid.mototracklog.ui.formatDuration
import de.codevoid.mototracklog.ui.reductionPercent
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.util.Locale

class FormatTest {

    private val us = Formats(Locale.US, ZoneId.of("UTC"))
    private val de = Formats(Locale.GERMANY, ZoneId.of("UTC"))

    @Test
    fun countGroupsDigitsPerLocale() {
        assertEquals("0", us.count(0))
        assertEquals("4,812", us.count(4_812))
        assertEquals("1,234,567", us.count(1_234_567))
        assertEquals("4.812", de.count(4_812))
    }

    @Test
    fun decimalRoundsHalfUpToOnePlace() {
        assertEquals("3.2", us.decimal(3.24f))
        assertEquals("3.3", us.decimal(3.25f))
        assertEquals("0.0", us.decimal(0f))
        assertEquals("13.0", us.decimal(12.96f))
        assertEquals("123.5", us.decimal(123.46f))
        assertEquals("3,2", de.decimal(3.24f))
        assertEquals(DASH, us.decimal(null))
    }

    @Test
    fun clockFormatsTimeOfDayInTheGivenZone() {
        assertEquals("14:23:07", us.clock(51_787_000L))
        assertEquals("00:00:00", us.clock(0L))
        assertEquals(DASH, us.clock(null))
        assertEquals("14:23", us.clockShort(51_787_000L))
        assertEquals("15:23:07", Formats(Locale.US, ZoneId.of("UTC+1")).clock(51_787_000L))
    }

    @Test
    fun runTitleIsAnAbsoluteDate() {
        // 2026-09-12T08:14:00Z
        assertEquals("12 Sep 2026, 08:14", us.runTitle(1_789_200_840_000L))
    }

    @Test
    fun fileStampIsLocalStartTimeWithoutSeparatorsThatNeedEscaping() {
        assertEquals("20260912-0814", us.fileStamp(1_789_200_840_000L))
        assertEquals("20260912-1014", Formats(Locale.US, ZoneId.of("UTC+2")).fileStamp(1_789_200_840_000L))
        // Locale-independent digits, so a file name never depends on the device language.
        assertEquals("20260912-0814", Formats(Locale.forLanguageTag("ar-EG"), ZoneId.of("UTC")).fileStamp(1_789_200_840_000L))
    }

    @Test
    fun durationUsesTheLargestUnitThatApplies() {
        assertEquals("0 s", formatDuration(0L))
        assertEquals("42 s", formatDuration(42_000L))
        assertEquals("59 s", formatDuration(59_999L))
        assertEquals("1 min", formatDuration(60_000L))
        assertEquals("12 min", formatDuration(754_000L))
        assertEquals("1 h 0 min", formatDuration(3_600_000L))
        assertEquals("1 h 23 min", formatDuration(4_980_000L))
        assertEquals("26 h 5 min", formatDuration(93_900_000L))
        assertEquals("0 s", formatDuration(-5L))
    }

    @Test
    fun reductionPercentRoundsInsteadOfFlooring() {
        assertEquals(0, reductionPercent(0, 0))
        assertEquals(62, reductionPercent(100, 38))
        assertEquals(1, reductionPercent(1_000, 995))
        assertEquals(0, reductionPercent(10, 10))
        assertEquals(100, reductionPercent(10, 0))
        assertEquals(67, reductionPercent(3, 1))
    }

    @Test
    fun speedIsWholeKilometresOrMilesPerHour() {
        assertEquals(0, displaySpeed(0f, metric = true))
        assertEquals(36, displaySpeed(10f, metric = true))
        assertEquals(22, displaySpeed(10f, metric = false))
        // 27.78 m/s is 100.0 km/h and 62.1 mph.
        assertEquals(100, displaySpeed(27.78f, metric = true))
        assertEquals(62, displaySpeed(27.78f, metric = false))
        // Rounded, not truncated: 2.9 m/s is 10.44 km/h, 3.0 m/s is 10.8 km/h.
        assertEquals(10, displaySpeed(2.9f, metric = true))
        assertEquals(11, displaySpeed(3.0f, metric = true))
    }
}
