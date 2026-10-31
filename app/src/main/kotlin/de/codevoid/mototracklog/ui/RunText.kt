package de.codevoid.mototracklog.ui

import android.content.Context
import de.codevoid.mototracklog.R
import de.codevoid.mototracklog.data.RunInfo

/** "1 h 23 min · 4,812 points", or "No points" for an empty run whose duration means nothing. */
fun Context.runSummary(run: RunInfo, formats: Formats): String {
    if (run.pointCount == 0L) return getString(R.string.run_no_points)
    val points = resources.getQuantityString(R.plurals.points, run.pointCount.toInt(), formats.count(run.pointCount))
    return formatDuration(run.durationMillis) + getString(R.string.list_separator) + points
}
