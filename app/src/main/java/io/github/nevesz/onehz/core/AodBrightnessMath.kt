package io.github.nevesz.onehz.core

import kotlin.math.roundToInt

/**
 * Pure math for the AOD brightness feature.
 *
 * The AOD uses the same 0..255 platform brightness scale as [android.provider.Settings.System.SCREEN_BRIGHTNESS].
 * The user picks a percentage of the screen brightness at lock time and a safety band (also in
 * percent of the screen brightness). A hard floor keeps the AOD readable even when the screen is
 * at its lowest level.
 */
object AodBrightnessMath {
    const val DEFAULT_PCT = 10
    const val DEFAULT_MIN_PCT = 5
    const val DEFAULT_MAX_PCT = 25

    const val MIN_PCT = 1
    const val MAX_PCT = 50

    /** Observed stock minimum on the tested firmware; writing 0 would turn the AOD off. */
    const val MIN_RAW = 2
    const val MAX_RAW = 255

    fun compute(screenRaw: Int, pct: Int, minPct: Int, maxPct: Int): Int {
        val raw = screenRaw.coerceIn(0, MAX_RAW)
        val min = (raw * minPct.coerceIn(MIN_PCT, MAX_PCT) / 100.0).roundToInt()
        val max = (raw * maxPct.coerceIn(MIN_PCT, MAX_PCT) / 100.0).roundToInt()
        val chosen = (raw * pct.coerceIn(MIN_PCT, MAX_PCT) / 100.0).roundToInt()
        return chosen.coerceIn(min.coerceAtMost(max), max.coerceAtLeast(min)).coerceIn(MIN_RAW, MAX_RAW)
    }
}
