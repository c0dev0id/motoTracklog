package de.codevoid.mototracklog.ui

import android.content.Context
import android.content.res.Configuration

val Context.isPortrait: Boolean
    get() = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
