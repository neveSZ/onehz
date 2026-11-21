package io.github.nevesz.onehz.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

/**
 * Samsung exposes the panel refresh rates through its static floating-feature list.
 * [Display.supportedModes] changes with the current resolution/mode and can hide rates
 * (for example, 120 Hz), so the feature list is the primary source.
 */
object SamsungDisplayRates {

    private const val MAIN_KEY = "SEC_FLOATING_FEATURE_LCD_CONFIG_HFR_SUPPORTED_REFRESH_RATE"
    private const val COVER_KEY = "SEC_FLOATING_FEATURE_LCD_CONFIG_SUB_HFR_SUPPORTED_REFRESH_RATE"

    fun main(context: Context): List<Int> =
        readFeature(MAIN_KEY).ifEmpty { fallbackSupportedModes(context) }

    fun cover(context: Context): List<Int> = readFeature(COVER_KEY)

    fun all(context: Context): List<Int> =
        (main(context) + cover(context)).distinct().sorted()

    private fun readFeature(key: String): List<Int> = try {
        val feature = Class.forName("com.samsung.android.feature.SemFloatingFeature")
            .getMethod("getInstance").invoke(null)
        val value = feature.javaClass.getMethod("getString", String::class.java)
            .invoke(feature, key) as? String
        value.orEmpty()
            .split(',')
            .mapNotNull { it.trim().toFloatOrNull()?.roundToInt()?.takeIf { hz -> hz > 0 } }
            .distinct()
            .sorted()
    } catch (t: Throwable) {
        emptyList()
    }

    private fun fallbackSupportedModes(context: Context): List<Int> = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY).supportedModes
            .map { it.refreshRate.roundToInt() }
            .filter { it > 0 }
            .distinct()
            .sorted()
    } catch (t: Throwable) {
        emptyList()
    }
}
