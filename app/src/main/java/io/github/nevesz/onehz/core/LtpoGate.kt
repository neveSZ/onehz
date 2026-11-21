package io.github.nevesz.onehz.core

import android.content.Context
import android.os.Build

/** Samsung LTPO eligibility check. The app fails closed when this returns false. */
object LtpoGate {

    data class Result(
        val eligible: Boolean,
        val manufacturer: String,
        val hfrMode: Int,
        val supportedRates: List<Int>,
        val reason: String
    ) {
        val summary: String
            get() = buildString {
                append(if (eligible) "LTPO OK" else "LTPO not available")
                if (supportedRates.isNotEmpty()) {
                    append(" · ").append(supportedRates.joinToString("/")).append(" Hz")
                }
            }
    }

    fun evaluate(context: Context): Result {
        val manufacturer = Build.MANUFACTURER ?: ""
        if (!manufacturer.equals("samsung", ignoreCase = true)) {
            return Result(false, manufacturer, -1, emptyList(), "manufacturer=$manufacturer")
        }

        val hfrMode = readHfrMode()
        if (hfrMode <= 1) {
            return Result(false, manufacturer, hfrMode, emptyList(), "HFR=$hfrMode")
        }

        val rates = SamsungDisplayRates.main(context)
        val lowest = rates.minOrNull() ?: Int.MAX_VALUE
        if (lowest > 30) {
            return Result(false, manufacturer, hfrMode, rates, "min=${lowest} Hz")
        }

        return Result(true, manufacturer, hfrMode, rates, "")
    }

    private fun readHfrMode(): Int = try {
        val feature = Class.forName("com.samsung.android.feature.SemFloatingFeature")
            .getMethod("getInstance").invoke(null)
        val value = feature.javaClass.getMethod("getString", String::class.java)
            .invoke(feature, "SEC_FLOATING_FEATURE_LCD_CONFIG_HFR_MODE") as? String
        value?.toIntOrNull() ?: -1
    } catch (t: Throwable) {
        -1
    }

}
