package de.roadcam.app

import android.os.PowerManager
import kotlin.math.roundToInt

object ThermalPolicy {
    const val DEFAULT_FPS = 30
    const val HOT_FPS = 25

    fun levelForStatus(status: Int): Int = when {
        status >= PowerManager.THERMAL_STATUS_SEVERE -> 2
        status >= PowerManager.THERMAL_STATUS_MODERATE -> 1
        else -> 0
    }

    fun fps(level: Int): Int = if (level >= 2) HOT_FPS else DEFAULT_FPS

    fun bitrate(level: Int, base: Int): Int = when (level) {
        2 -> minOf(base, RecordQuality.SPAREND.bitrate)
        1 -> (base * 0.75f).roundToInt()
        else -> base
    }

    fun overlapAllowed(level: Int): Boolean = level == 0
}
