package io.github.nevesz.onehz.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.view.Display
import io.github.nevesz.onehz.core.DisplayProfile
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.DisplayRates
import io.github.nevesz.onehz.core.DisplayVendor
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import kotlin.math.roundToInt

/**
 * Applies the requested rate through the route that actually exists on the device.
 *
 * Samsung uses the One UI mode plus the System peak/min pair. HyperOS uses a single Secure
 * cap (`miui_refresh_rate`, or `user_refresh_rate` on HyperOS 1). Anything else is blocked
 * before a single write. There is no generic/AOSP fallback by design.
 */
object SessionArbiter {

    private const val KEY_MODE = "refresh_rate_mode"
    private const val KEY_PEAK = "peak_refresh_rate"
    private const val KEY_MIN = "min_refresh_rate"
    private const val MODE_ADAPTIVE = 2
    private const val AUDIT_WINDOW_MS = 15_000L

    private var sessionOpen = false
    private var ecoKeysWritten = false

    private var profile: DisplayProfile? = null

    private var appliedMode: Int? = null
    private var appliedPeak: Float? = null
    private var appliedMin: Float? = null
    private var appliedCap: Int? = null
    private var lastAuditAt = 0L

    /** Drops the cache so the next apply rewrites everything after a real state change. */
    @Synchronized
    fun bumpEpoch() {
        appliedMode = null
        appliedPeak = null
        appliedMin = null
        appliedCap = null
    }

    @Synchronized
    fun apply(context: Context, request: TierRequest, screenOff: Boolean, ecoActive: Boolean) {
        if (ShizukuGateway.service == null) {
            Diagnostics.lastWrite.value = "failed: UserService unavailable"
            return
        }
        val activeProfile = profile ?: DisplayProfileResolver.detect(context).also { profile = it }
        if (!activeProfile.supported) {
            val reason = activeProfile.unsupportedReason ?: "unsupported device"
            Diagnostics.controlMethod.value = "blocked"
            Diagnostics.lastWrite.value = "blocked: $reason"
            Diagnostics.log("arbiter", "blocked: $reason")
            return
        }
        if (!sessionOpen) openSession(context, activeProfile)

        // Battery saver turned off: return Samsung's own rate-cap keys immediately.
        if (!ecoActive && ecoKeysWritten) {
            restoreEcoKeys(context)
            ecoKeysWritten = false
        }

        val wrote = when (activeProfile.vendor) {
            DisplayVendor.SAMSUNG -> applySamsung(context, request, screenOff, ecoActive)
            DisplayVendor.XIAOMI -> applyHyperOs(activeProfile, request)
            DisplayVendor.UNSUPPORTED -> false
        }

        Diagnostics.appliedHz.value = request.hz
        val observed = observedHz(context)
        if (observed > 0) Diagnostics.observedHz.value = observed
        val route = when (activeProfile.vendor) {
            DisplayVendor.SAMSUNG -> "secure/$KEY_MODE=2 + system peak/min"
            DisplayVendor.XIAOMI -> "secure/${activeProfile.hyperOsKey}"
            DisplayVendor.UNSUPPORTED -> "blocked"
        }
        Diagnostics.controlMethod.value = route
        Diagnostics.lastWrite.value = if (wrote) {
            "$route · ${request.hz} Hz requested · observed ${if (observed > 0) "$observed Hz" else "?"} · ${request.source}"
        } else {
            "no change (dedupe) · ${request.hz} Hz · ${request.source}"
        }
        if (wrote) {
            Diagnostics.log(
                "arbiter",
                "${request.source} -> ${request.hz} Hz · screen ${if (screenOff) "off/AOD" else "on"} · obs $observed · $route"
            )
        }
    }

    private fun applySamsung(
        context: Context,
        request: TierRequest,
        screenOff: Boolean,
        ecoActive: Boolean
    ): Boolean {
        val peak = request.hz.toFloat()
        var wrote = false

        if (appliedMode != MODE_ADAPTIVE) {
            if (ShizukuGateway.writeMode(KEY_MODE, MODE_ADAPTIVE)) {
                appliedMode = MODE_ADAPTIVE
                wrote = true
            }
        }
        if (appliedPeak != peak) {
            if (ShizukuGateway.writeSystemRate(KEY_PEAK, peak)) {
                appliedPeak = peak
                wrote = true
            }
        }
        if (appliedMin != peak) {
            if (ShizukuGateway.writeSystemRate(KEY_MIN, peak)) {
                appliedMin = peak
                wrote = true
            }
        }

        // Cold path: disable Samsung's own ECO rate limit, after the rate is already applied.
        if (!screenOff && ecoActive) writeEcoKeysOnce(context)
        return wrote
    }

    private fun applyHyperOs(activeProfile: DisplayProfile, request: TierRequest): Boolean {
        val key = activeProfile.hyperOsKey ?: return false
        if (appliedCap == request.hz) return false
        if (!ShizukuGateway.writeSecureSetting(key, request.hz.toString())) return false
        appliedCap = request.hz
        return true
    }

    /** Samsung global settings that cap the refresh rate while battery saver is active. */
    private val SAMSUNG_ECO_KEYS = listOf(
        "pms_settings_refresh_rate_enabled",
        "pms_settings_refresh_rate_cover_enabled",
        "psm_refresh_rate_tag",
        "psm_refresh_rate_cover_tag"
    )

    /** Called when the privileged service reconnects: the remote state is unknown. */
    @Synchronized
    fun invalidate() {
        sessionOpen = false
        ecoKeysWritten = false
        lastAuditAt = 0L
        bumpEpoch()
    }

    @Synchronized
    fun release(context: Context?) {
        if (!sessionOpen) return
        if (ShizukuGateway.service == null) {
            // Cannot restore now; keep the pending values for the next connection.
            sessionOpen = false
            ecoKeysWritten = false
            lastAuditAt = 0L
            bumpEpoch()
            return
        }
        val activeProfile = context?.let { DisplayProfileResolver.detect(it) } ?: profile
        if (activeProfile != null) {
            when (activeProfile.vendor) {
                DisplayVendor.XIAOMI -> context?.let { restoreRateBackup(it, activeProfile) }
                DisplayVendor.SAMSUNG -> resetToSystemDefault()
                DisplayVendor.UNSUPPORTED -> Unit
            }
            if (context != null && activeProfile.supportsSamsungEcoKeys) {
                restoreEcoKeys(context)
            }
        }
        sessionOpen = false
        ecoKeysWritten = false
        lastAuditAt = 0L
        context?.let {
            OneHzPreferences.clearSessionPending(it)
            OneHzPreferences.setRateBackup(it, emptyMap())
        }
        bumpEpoch()
        Diagnostics.log("arbiter", "session closed; ${activeProfile?.label ?: "display"} default restored")
    }

    /**
     * Restores values left behind by a previous session that ended without a clean release
     * (process death, app update). Called on every fresh service connection.
     */
    @Synchronized
    fun restorePending(context: Context) {
        val hasPending = OneHzPreferences.hasPendingSession(context)
        val hasEcoBackup = OneHzPreferences.ecoGlobalBackup(context).isNotEmpty()
        val hasRateBackup = OneHzPreferences.hasRateBackup(context)
        if (!hasPending && !hasEcoBackup && !hasRateBackup) return
        if (ShizukuGateway.service == null) return

        val activeProfile = DisplayProfileResolver.detect(context)
        profile = activeProfile
        if (!activeProfile.supported) {
            OneHzPreferences.clearSessionPending(context)
            OneHzPreferences.setRateBackup(context, emptyMap())
            Diagnostics.log("arbiter", "pending restore skipped: ${activeProfile.unsupportedReason}")
            return
        }
        when (activeProfile.vendor) {
            DisplayVendor.XIAOMI -> restoreRateBackup(context, activeProfile)
            DisplayVendor.SAMSUNG -> resetToSystemDefault()
            DisplayVendor.UNSUPPORTED -> Unit
        }
        if (activeProfile.supportsSamsungEcoKeys) restoreEcoKeys(context)
        OneHzPreferences.clearSessionPending(context)
        OneHzPreferences.setRateBackup(context, emptyMap())
        bumpEpoch()
        Diagnostics.log("arbiter", "previous session restored (${activeProfile.label})")
    }

    /**
     * Cheap self-healing check: if something else changed the active route, drop the cache so
     * the next apply rewrites it. Costs a few reads and only runs on resume.
     */
    @Synchronized
    fun audit() {
        if (!sessionOpen || ShizukuGateway.service == null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastAuditAt < AUDIT_WINDOW_MS) return
        lastAuditAt = now
        val activeProfile = profile ?: return
        if (!activeProfile.supported) return
        val mismatch = when (activeProfile.vendor) {
            DisplayVendor.XIAOMI -> {
                val key = activeProfile.hyperOsKey ?: return
                val cap = ShizukuGateway.readSecureSetting(key)?.toIntOrNull()
                appliedCap != null && cap != appliedCap
            }

            DisplayVendor.SAMSUNG -> {
                val mode = ShizukuGateway.readMode(KEY_MODE)
                val peak = ShizukuGateway.readSystemRate(KEY_PEAK)?.toFloatOrNull()
                val min = ShizukuGateway.readSystemRate(KEY_MIN)?.toFloatOrNull()
                (appliedMode != null && mode != appliedMode) ||
                    (appliedPeak != null && peak != appliedPeak) ||
                    (appliedMin != null && min != appliedMin)
            }

            DisplayVendor.UNSUPPORTED -> false
        }
        if (mismatch) {
            Diagnostics.externalChange.value = true
            Diagnostics.log("arbiter", "external settings detected; reapplying")
            bumpEpoch()
        }
    }

    private fun openSession(context: Context, activeProfile: DisplayProfile) {
        sessionOpen = true
        bumpEpoch()
        if (activeProfile.vendor == DisplayVendor.XIAOMI) {
            val key = activeProfile.hyperOsKey
            if (key != null) {
                val current = ShizukuGateway.readSecureSetting(key) ?: ""
                OneHzPreferences.setRateBackup(context, mapOf(key to current))
            }
        }
        OneHzPreferences.markSessionPending(context)
        Diagnostics.log("arbiter", "session opened (${activeProfile.label})")
    }

    /**
     * Returns the Samsung display to the stock adaptive range instead of replaying captured
     * values, which could be our own AOD state and would pin the screen at a low rate.
     */
    private fun resetToSystemDefault() {
        ShizukuGateway.writeMode(KEY_MODE, MODE_ADAPTIVE)
        ShizukuGateway.deleteSystemRate(KEY_PEAK)
        ShizukuGateway.deleteSystemRate(KEY_MIN)
    }

    /**
     * HyperOS restore: put back the exact values captured when the session opened. If the
     * session predates the snapshot (or the key was absent), return to the panel ceiling so a
     * low cap can never be left behind.
     */
    private fun restoreRateBackup(context: Context, activeProfile: DisplayProfile) {
        val key = activeProfile.hyperOsKey ?: return
        val backup = OneHzPreferences.rateBackup(context)
        if (backup.isNotEmpty()) {
            var restored = true
            for ((backupKey, value) in backup) {
                val ok = if (value.isEmpty()) {
                    ShizukuGateway.deleteSecureSetting(backupKey)
                } else {
                    ShizukuGateway.writeSecureSetting(backupKey, value)
                }
                if (!ok) restored = false
            }
            if (restored) {
                OneHzPreferences.setRateBackup(context, emptyMap())
                Diagnostics.log("arbiter", "HyperOS rate snapshot restored")
            } else {
                Diagnostics.log("arbiter", "HyperOS rate restore pending")
            }
            return
        }
        val ceiling = DisplayRates.main(context).maxOrNull() ?: 120
        ShizukuGateway.writeSecureSetting(key, ceiling.toString())
        Diagnostics.log("arbiter", "HyperOS rate returned to panel ceiling $ceiling Hz")
    }

    private fun writeEcoKeysOnce(context: Context) {
        if (ecoKeysWritten) return
        if (OneHzPreferences.ecoGlobalBackup(context).isEmpty()) {
            val backup = mutableMapOf<String, String>()
            for (key in SAMSUNG_ECO_KEYS) {
                val current = ShizukuGateway.readGlobalString(key)
                if (current.isNotEmpty() && current != "0") backup[key] = current
            }
            // Persist the snapshot before changing anything so a crash cannot lose it.
            OneHzPreferences.setEcoGlobalBackup(context, backup)
            for (key in backup.keys) {
                ShizukuGateway.writeGlobalString(key, "0")
            }
        }
        ecoKeysWritten = true
    }

    /**
     * Restores the Samsung keys changed by [writeEcoKeysOnce]. Keep the backup when any write
     * fails so the next connection can retry instead of leaving the system modified.
     */
    private fun restoreEcoKeys(context: Context) {
        val backup = OneHzPreferences.ecoGlobalBackup(context)
        if (backup.isEmpty()) return
        var restored = true
        for ((key, value) in backup) {
            if (!ShizukuGateway.writeGlobalString(key, value)) restored = false
        }
        if (restored) {
            OneHzPreferences.setEcoGlobalBackup(context, emptyMap())
            Diagnostics.log("arbiter", "Samsung ECO settings restored")
        } else {
            Diagnostics.log("arbiter", "Samsung ECO settings restore pending")
        }
    }

    private fun observedHz(context: Context): Int = try {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        dm.getDisplay(Display.DEFAULT_DISPLAY).refreshRate.roundToInt()
    } catch (t: Throwable) {
        -1
    }
}
