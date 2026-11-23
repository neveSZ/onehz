package io.github.nevesz.onehz.core

import android.content.Context
import android.os.Build
import io.github.nevesz.onehz.BuildConfig
import io.github.nevesz.onehz.core.profile.ProfileStore
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.engine.SessionArbiter
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import io.github.nevesz.onehz.shizuku.ShizukuState

/**
 * Read-only view of the system values OneHZ may write, plus the captured originals when a
 * session kept a backup. Used by the "System changes" screen and the panel report.
 */
data class SystemChange(
    val key: String,
    val current: String?,
    val original: String? = null
)

data class SystemChangesSnapshot(
    val vendor: String,
    val changes: List<SystemChange>,
    val shizukuReady: Boolean,
    val externalChange: Boolean,
    val dozeWhitelisted: Boolean?
)

object SystemChanges {

    private const val SAMSUNG_MODE = "refresh_rate_mode"
    private const val SAMSUNG_PEAK = "peak_refresh_rate"
    private const val SAMSUNG_MIN = "min_refresh_rate"

    private val SAMSUNG_ECO_KEYS = listOf(
        "pms_settings_refresh_rate_enabled",
        "pms_settings_refresh_rate_cover_enabled",
        "psm_refresh_rate_tag",
        "psm_refresh_rate_cover_tag"
    )

    fun read(context: Context): SystemChangesSnapshot {
        val display = DisplayProfileResolver.detect(context)
        val ready = ShizukuGateway.state.value == ShizukuState.READY
        val changes = mutableListOf<SystemChange>()
        if (ready) {
            when (display.vendor) {
                DisplayVendor.SAMSUNG -> {
                    changes += SystemChange(
                        "secure/$SAMSUNG_MODE",
                        ShizukuGateway.readMode(SAMSUNG_MODE).takeIf { it >= 0 }?.toString()
                    )
                    changes += SystemChange(
                        "system/$SAMSUNG_PEAK",
                        ShizukuGateway.readSystemRate(SAMSUNG_PEAK)
                    )
                    changes += SystemChange(
                        "system/$SAMSUNG_MIN",
                        ShizukuGateway.readSystemRate(SAMSUNG_MIN)
                    )
                    if (display.supportsAodBrightness) {
                        val value = ShizukuGateway.readAodBrightness()
                        changes += SystemChange(
                            "system/debug_aod_brightness",
                            value.takeIf { it != Int.MIN_VALUE }?.toString()
                        )
                    }
                    val ecoOriginal = OneHzPreferences.ecoGlobalBackup(context)
                    SAMSUNG_ECO_KEYS.forEach { key ->
                        changes += SystemChange(
                            "global/$key",
                            ShizukuGateway.readGlobalString(key),
                            ecoOriginal[key]
                        )
                    }
                }

                DisplayVendor.XIAOMI -> {
                    val key = display.hyperOsKey ?: "miui_refresh_rate"
                    changes += SystemChange(
                        "secure/$key",
                        ShizukuGateway.readSecureSetting(key),
                        OneHzPreferences.rateBackup(context)[key]
                    )
                }

                DisplayVendor.UNSUPPORTED -> Unit
            }
        }
        val doze = if (ready) {
            runCatching {
                ShizukuGateway.isDozeWhitelisted(context.packageName)
            }.getOrNull()
        } else {
            null
        }
        return SystemChangesSnapshot(
            vendor = display.label,
            changes = changes,
            shizukuReady = ready,
            externalChange = Diagnostics.externalChange.value,
            dozeWhitelisted = doze
        )
    }

    /**
     * Stops control and restores the values captured by the current session, then clears
     * the Samsung AOD brightness key. The master switch is turned off so the engine cannot
     * overwrite the restore immediately.
     */
    fun restoreAll(context: Context) {
        OneHzPreferences.setEnabled(context, false)
        OneHzKeepAliveService.stop(context)
        OneHzEngine.stop()
        AodBrightnessController.restoreSystem(context)
        SessionArbiter.restorePending(context)
        Diagnostics.externalChange.value = false
    }

    fun report(context: Context): String {
        val snapshot = read(context)
        val gate = DisplayGate.evaluate(context)
        val rates = ProfileStore.rates(context)
        return buildString {
            appendLine("OneHZ panel report")
            appendLine("App: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine("Vendor: ${snapshot.vendor}")
            appendLine("Rates: ${rates.joinToString("/")} Hz")
            appendLine("Gate: ${gate.summary}")
            appendLine("Shizuku: ${if (snapshot.shizukuReady) "ready" else "unavailable"}")
            appendLine("External change detected: ${snapshot.externalChange}")
            appendLine("Doze exception: ${snapshot.dozeWhitelisted ?: "unknown"}")
            appendLine()
            appendLine("Keys:")
            if (snapshot.changes.isEmpty()) {
                appendLine("none")
            } else {
                snapshot.changes.forEach { change ->
                    appendLine("${change.key}=${change.current ?: "-"}")
                    change.original?.let { appendLine("  original=$it") }
                }
            }
        }
    }
}
