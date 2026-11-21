package io.github.nevesz.onehz.engine

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import kotlin.math.roundToInt

/**
 * Samsung display control: write the logical display mode and the System peak/min values.
 * Writes are deduplicated per epoch, so reapplying the same values costs zero privileged
 * calls.
 */
object SessionArbiter {

    private const val KEY_MODE = "refresh_rate_mode"
    private const val KEY_PEAK = "peak_refresh_rate"
    private const val KEY_MIN = "min_refresh_rate"
    private const val MODE_ADAPTIVE = 2

    private var sessionOpen = false
    private var ecoKeysWritten = false

    private var appliedMode: Int? = null
    private var appliedPeak: Float? = null
    private var appliedMin: Float? = null

    /** Drops the cache so the next apply rewrites everything after a real state change. */
    @Synchronized
    fun bumpEpoch() {
        appliedMode = null
        appliedPeak = null
        appliedMin = null
    }

    @Synchronized
    fun apply(context: Context, request: TierRequest, screenOff: Boolean, ecoActive: Boolean) {
        if (ShizukuGateway.service == null) {
            Diagnostics.lastWrite.value = "failed: UserService unavailable"
            return
        }
        if (!sessionOpen) openSession(context)

        // Battery saver turned off: return Samsung's own rate-cap keys immediately.
        if (!ecoActive && ecoKeysWritten) {
            restoreEcoKeys(context)
            ecoKeysWritten = false
        }

        // AOD pins peak/min to the AOD rate. Interactive sessions pin the current tier for the
        // same reason: the tier is the value the user asked for.
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

        Diagnostics.appliedHz.value = request.hz
        val observed = observedHz(context)
        if (observed > 0) Diagnostics.observedHz.value = observed
        Diagnostics.lastWrite.value = if (wrote) {
            "mode 2 · peak ${request.hz} · min ${request.hz} · observed ${if (observed > 0) observed else "?"} Hz · ${request.source}"
        } else {
            "no change (dedupe) · ${request.hz} Hz · ${request.source}"
        }
        if (wrote) {
            Diagnostics.log(
                "arbiter",
                "${request.source} -> ${request.hz} Hz · screen ${if (screenOff) "off/AOD" else "on"} · obs $observed"
            )
        }
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
        bumpEpoch()
    }

    @Synchronized
    fun release(context: Context?) {
        if (!sessionOpen) return
        if (ShizukuGateway.service == null) {
            // Cannot restore now; keep the pending values for the next connection.
            sessionOpen = false
            ecoKeysWritten = false
            bumpEpoch()
            return
        }
        resetToSystemDefault()
        context?.let { restoreEcoKeys(it) }
        sessionOpen = false
        ecoKeysWritten = false
        context?.let { OneHzPreferences.clearSessionPending(it) }
        bumpEpoch()
        Diagnostics.log("arbiter", "session closed; adaptive default restored")
    }

    /**
     * Restores values left behind by a previous session that ended without a clean release
     * (process death, app update). Called on every fresh service connection.
     */
    @Synchronized
    fun restorePending(context: Context) {
        val hasPending = OneHzPreferences.hasPendingSession(context)
        val hasEcoBackup = OneHzPreferences.ecoGlobalBackup(context).isNotEmpty()
        if (!hasPending && !hasEcoBackup) return
        if (ShizukuGateway.service == null) return
        resetToSystemDefault()
        restoreEcoKeys(context)
        OneHzPreferences.clearSessionPending(context)
        bumpEpoch()
        Diagnostics.log("arbiter", "adaptive default restored (previous session)")
    }

    /**
     * Cheap self-healing check: if something else changed mode/peak/min, drop the cache so the
     * next apply rewrites them. Costs three reads and only runs on resume.
     */
    @Synchronized
    fun audit() {
        if (!sessionOpen || ShizukuGateway.service == null) return
        val mode = ShizukuGateway.readMode(KEY_MODE)
        val peak = ShizukuGateway.readSystemRate(KEY_PEAK)?.toFloatOrNull()
        val min = ShizukuGateway.readSystemRate(KEY_MIN)?.toFloatOrNull()
        val mismatch = (appliedMode != null && mode != appliedMode) ||
            (appliedPeak != null && peak != appliedPeak) ||
            (appliedMin != null && min != appliedMin)
        if (mismatch) {
            Diagnostics.log("arbiter", "external settings detected; reapplying")
            bumpEpoch()
        }
    }

    private fun openSession(context: Context) {
        sessionOpen = true
        bumpEpoch()
        OneHzPreferences.markSessionPending(context)
        Diagnostics.log("arbiter", "session opened")
    }

    /**
     * Returns the display to the stock adaptive range (10-120) instead of replaying captured
     * values, which could be our own AOD state (10/10) and would pin the screen at 10 Hz.
     */
    private fun resetToSystemDefault() {
        ShizukuGateway.writeMode(KEY_MODE, MODE_ADAPTIVE)
        ShizukuGateway.deleteSystemRate(KEY_PEAK)
        ShizukuGateway.deleteSystemRate(KEY_MIN)
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
