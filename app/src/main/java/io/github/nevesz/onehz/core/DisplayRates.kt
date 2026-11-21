package io.github.nevesz.onehz.core

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import kotlin.math.roundToInt

/**
 * Refresh rates exposed by the panel.
 *
 * Samsung publishes a static floating-feature list that includes rates which are not always
 * present in [Display.supportedModes]. HyperOS uses the framework mode list, which is the only
 * public source available before Shizuku is connected. Unsupported vendors return an empty
 * list so the gate can fail closed.
 */
object DisplayRates {

    private const val MAIN_KEY = "SEC_FLOATING_FEATURE_LCD_CONFIG_HFR_SUPPORTED_REFRESH_RATE"
    private const val COVER_KEY = "SEC_FLOATING_FEATURE_LCD_CONFIG_SUB_HFR_SUPPORTED_REFRESH_RATE"

    fun main(context: Context): List<Int> =
        main(context, DisplayProfileResolver.detect(context))

    fun main(context: Context, profile: DisplayProfile): List<Int> = when (profile.vendor) {
        DisplayVendor.SAMSUNG -> {
            val framework = frameworkModes(context)
            val feature = readFeature(MAIN_KEY)
            when {
                feature.size >= 2 -> feature
                feature.isNotEmpty() -> (feature + framework).distinct().sorted()
                else -> framework
            }
        }

        DisplayVendor.XIAOMI -> frameworkModes(context)
        DisplayVendor.UNSUPPORTED -> emptyList()
    }

    fun cover(context: Context): List<Int> =
        if (DisplayProfileResolver.detect(context).vendor == DisplayVendor.SAMSUNG) {
            readFeature(COVER_KEY)
        } else {
            emptyList()
        }

    fun all(context: Context): List<Int> =
        (main(context) + cover(context)).distinct().sorted()

    internal fun parseRates(value: String?): List<Int> = value.orEmpty()
        .split(',')
        .mapNotNull { part ->
            part.trim().toFloatOrNull()?.roundToInt()?.takeIf { hz -> hz > 0 }
        }
        .distinct()
        .sorted()

    private fun readFeature(key: String): List<Int> = try {
        val feature = Class.forName("com.samsung.android.feature.SemFloatingFeature")
            .getMethod("getInstance").invoke(null)
        val value = feature.javaClass.getMethod("getString", String::class.java)
            .invoke(feature, key) as? String
        parseRates(value)
    } catch (t: Throwable) {
        emptyList()
    }

    private fun frameworkModes(context: Context): List<Int> = try {
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
