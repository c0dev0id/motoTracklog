package de.codevoid.mototracklog.ui

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Placeholder for a value that does not exist yet (missing optional fields, a paused run). */
const val DASH = "—"

/**
 * Locale- and zone-aware formatting for every number and time the screens display. Pure JVM code,
 * so it is unit-tested; words around the numbers come from string resources at the call site.
 */
class Formats(locale: Locale, zone: ZoneId) {

    private val integer: NumberFormat = NumberFormat.getIntegerInstance(locale)
    private val oneDecimal: NumberFormat = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        roundingMode = RoundingMode.HALF_UP
    }
    private val clockFmt = DateTimeFormatter.ofPattern("HH:mm:ss", locale).withZone(zone)
    private val clockShortFmt = DateTimeFormatter.ofPattern("HH:mm", locale).withZone(zone)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale).withZone(zone)
    private val fileStampFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm", Locale.ROOT).withZone(zone)

    /** Grouped integer: 4812 → "4,812" (US) / "4.812" (DE). */
    fun count(n: Long): String = integer.format(n)

    /** One decimal, half-up: 3.24 → "3.2", 12.96 → "13.0"; null → [DASH]. */
    fun decimal(v: Float?): String = v?.let { oneDecimal.format(it.toDouble()) } ?: DASH

    /** Wall-clock time of day with seconds; null → [DASH]. */
    fun clock(millis: Long?): String = millis?.let { clockFmt.format(Instant.ofEpochMilli(it)) } ?: DASH

    /** Wall-clock time of day without seconds. */
    fun clockShort(millis: Long): String = clockShortFmt.format(Instant.ofEpochMilli(millis))

    /** Absolute run title: "12 Sep 2026, 08:14". Deterministic — no "Today"/"Yesterday". */
    fun runTitle(millis: Long): String = dateTimeFmt.format(Instant.ofEpochMilli(millis))

    /** Local start time for file names: "20260912-0814". */
    fun fileStamp(millis: Long): String = fileStampFmt.format(Instant.ofEpochMilli(millis))
}

/** "42 s", "12 min", "1 h 23 min". Negative input reads as zero. Locale-free by design. */
fun formatDuration(millis: Long): String {
    val totalSeconds = millis.coerceAtLeast(0L) / 1000L
    val hours = totalSeconds / 3600L
    val minutes = totalSeconds % 3600L / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "$hours h $minutes min"
        minutes > 0L -> "$minutes min"
        else -> "$seconds s"
    }
}

/** Whole-percent reduction from [total] to [kept], rounded (995 of 1000 kept → 1). 0 when nothing to reduce. */
fun reductionPercent(total: Long, kept: Long): Int =
    if (total <= 0L) 0 else ((total - kept) * 100.0 / total).roundToInt().coerceIn(0, 100)

/** Speed in whole km/h, or mph when [metric] is false. */
fun displaySpeed(metersPerSecond: Float, metric: Boolean): Int =
    (metersPerSecond * if (metric) MPS_TO_KMH else MPS_TO_MPH).roundToInt()

private const val MPS_TO_KMH = 3.6f
private const val MPS_TO_MPH = 2.236936f
