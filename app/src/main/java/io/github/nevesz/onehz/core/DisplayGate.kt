package io.github.nevesz.onehz.core

import android.content.Context
import android.os.Build

/**
 * Strict eligibility gate. Only Samsung and Xiaomi/Redmi/POCO devices with a display that
 * exposes at least two refresh rates are accepted. Any other device fails closed: no engine
 * starts and no settings are written.
 */
object DisplayGate {

    data class Result(
        val eligible: Boolean,
        val manufacturer: String,
        val profile: DisplayProfile,
        val supportedRates: List<Int>,
        val reason: String
    ) {
        val summary: String
            get() = buildString {
                append(if (eligible) "Multi-rate OK" else "Display not supported")
                append(" · ").append(profile.label)
                if (supportedRates.isNotEmpty()) {
                    append(" · ").append(supportedRates.joinToString("/")).append(" Hz")
                }
                if (!eligible && reason.isNotBlank()) {
                    append(" · ").append(reason)
                }
            }
    }

    fun evaluate(context: Context): Result {
        val manufacturer = Build.MANUFACTURER ?: ""
        val profile = DisplayProfileResolver.detect(context)
        val rates = if (profile.supported) DisplayRates.main(context, profile) else emptyList()
        val eligible = profile.supported && rates.size >= 2
        val reason = when {
            !profile.supported -> profile.unsupportedReason ?: "unsupported device"
            rates.isEmpty() -> "no refresh rates"
            rates.size < 2 -> "only ${rates.size} rate"
            else -> ""
        }
        return Result(
            eligible = eligible,
            manufacturer = manufacturer,
            profile = profile,
            supportedRates = rates,
            reason = reason
        )
    }
}
