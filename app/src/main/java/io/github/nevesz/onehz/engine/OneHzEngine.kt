package io.github.nevesz.onehz.engine

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.TierStep
import io.github.nevesz.onehz.core.VideoSignals
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway

/**
 * Generic idle staircase: the first step applies on touch, then every hold elapses and the
 * next step is applied until the last one (the resting rate). A profile with a single step
 * behaves as a fixed rate and does not need raw touch monitoring at all.
 */
object OneHzEngine {

    private const val RECHECK_MS = 100L
    private const val MAX_RECHECKS = 20
    private const val WAKE_DEBOUNCE_MS = 1_200L
    private const val VIDEO_RECHECK_MS = 12_000L
    private const val VIDEO_PROBE_MS = 1_800L
    private const val VIDEO_UNKNOWN_LIMIT = 2

    private val thread = HandlerThread("OneHzEngine").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var started = false
    @Volatile
    private var appContext: Context? = null
    @Volatile
    private var isDown = false
    @Volatile
    private var lowRechecks = 0
    @Volatile
    private var lastUpAt = 0L
    @Volatile
    private var screenOff = false
    @Volatile
    private var ecoActive = false
    @Volatile
    private var foregroundPackage: String? = null
    @Volatile
    private var index = 0
    @Volatile
    private var steps: List<TierStep> = emptyList()
    @Volatile
    private var nextIndex = 0
    @Volatile
    private var timersPending = false
    @Volatile
    private var monitoringNeeded = false
    @Volatile
    private var lastWakeAt = 0L

    @Volatile
    private var videoActive = false
    @Volatile
    private var videoPackage: String? = null
    @Volatile
    private var videoSource = VideoSignals.Source.NONE
    @Volatile
    private var videoFailures = 0
    private var videoCheckInFlight = false

    private val stepRunnable: Runnable = Runnable {
        if (!started) {
            timersPending = false
            return@Runnable
        }
        if (isDown) {
            if (lowRechecks < MAX_RECHECKS) {
                lowRechecks++
                Diagnostics.log("timer", "next tier deferred (finger on screen) $lowRechecks/$MAX_RECHECKS")
                handler.postDelayed(stepRunnable, RECHECK_MS)
                return@Runnable
            }
            Diagnostics.log("timer", "forced step down after $MAX_RECHECKS rechecks")
        }
        lowRechecks = 0
        if (nextIndex > steps.lastIndex) {
            timersPending = false
            return@Runnable
        }
        index = nextIndex
        Diagnostics.log("timer", "tier ${index + 1}/${steps.size}")
        applyCurrent("timer")
        if (index >= steps.lastIndex) {
            timersPending = false
        } else {
            scheduleNext(null)
        }
    }

    /**
     * One check shortly after the finger lifts. It catches the common "tap play" flow even
     * when the app profile is fixed (no staircase timer would fire later) and also detects a
     * pause without waiting for the 12 s recheck.
     */
    private val videoProbe: Runnable = Runnable {
        if (!started || screenOff) return@Runnable
        val ctx = appContext ?: return@Runnable
        if (!videoEligible(ctx)) return@Runnable
        val changed = updateVideoState(ctx, "probe")
        if (changed) {
            SessionArbiter.bumpEpoch()
            applyCurrent("video-probe")
        }
    }

    /** Slow recheck only while a video is actually active. */
    private val videoRecheck: Runnable = Runnable {
        if (!started || screenOff || !videoActive) return@Runnable
        val ctx = appContext ?: return@Runnable
        if (isDown || timersPending) {
            scheduleVideoRecheck()
            return@Runnable
        }
        val changed = updateVideoState(ctx, "recheck")
        if (changed) {
            SessionArbiter.bumpEpoch()
            applyCurrent("video-recheck")
        }
        if (videoActive) scheduleVideoRecheck()
    }

    fun start(context: Context) {
        appContext = context.applicationContext
        if (started) return
        started = true
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        ecoActive = pm?.isPowerSaveMode == true
        screenOff = pm?.isInteractive == false
        handler.post { applyCurrent("start") }
    }

    fun stop() {
        started = false
        handler.removeCallbacksAndMessages(null)
        isDown = false
        lowRechecks = 0
        timersPending = false
        nextIndex = 0
        index = 0
        steps = emptyList()
        monitoringNeeded = false
        lastWakeAt = 0L
        videoActive = false
        videoPackage = null
        videoSource = VideoSignals.Source.NONE
        videoFailures = 0
        videoCheckInFlight = false
        SessionArbiter.release(appContext)
        ShizukuGateway.stopTouchMonitoring()
        Diagnostics.videoSource.value = VideoSignals.Source.NONE
        Diagnostics.videoDetail.value = "—"
        Diagnostics.idleReason.value = null
        Diagnostics.activeTierKey.value = "OFF"
        Diagnostics.activeStep.value = 0
        Diagnostics.activeStepCount.value = 0
        Diagnostics.appliedHz.value = 0
        Diagnostics.observedHz.value = 0
    }

    /**
     * Aggregated touch state across every monitored touchscreen. The gateway already
     * de-duplicates per-device DOWN/UP edges, so this only fires when the whole set goes from
     * "no finger" to "touching" and back.
     */
    fun onTouchState(touching: Boolean) = handler.post {
        if (!started) return@post
        if (touching) handleTouchDown() else handleTouchUp()
    }

    private fun handleTouchDown() {
        if (screenOff) {
            // The broadcast can arrive after the first touch; trust the live state.
            val interactive =
                (appContext?.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false
            if (!interactive) return
            screenOff = false
            Diagnostics.log("touch", "screen flag corrected on DOWN")
        }
        // While a video is active, a touch is also the moment the app may have changed.
        val videoCtx = appContext
        if (videoCtx != null && videoActive) {
            val changed = updateVideoState(videoCtx, "touch-down")
            if (changed) SessionArbiter.bumpEpoch()
        }
        isDown = true
        lowRechecks = 0
        handler.removeCallbacks(stepRunnable)
        timersPending = false
        index = 0
        Diagnostics.log("touch", "touch -> tier 1")
        applyCurrent("touch-down", resetTo = 0)
        // The rate is already applied; the slower foreground lookup only matters with
        // app profiles configured.
        val ctx = appContext
        if (ctx != null && OneHzPreferences.hasAppProfiles(ctx)) {
            val pkg = ShizukuGateway.foregroundPackage()
            if (!pkg.isNullOrBlank() && pkg != foregroundPackage) {
                foregroundPackage = pkg
                SessionArbiter.bumpEpoch()
                applyCurrent("foreground")
            }
        }
    }

    private fun handleTouchUp() {
        if (!started || screenOff) return
        if (!isDown) {
            Diagnostics.log("touch", "UP without DOWN (ignored)")
            return
        }
        lastUpAt = SystemClock.elapsedRealtime()
        isDown = false
        lowRechecks = 0
        handler.removeCallbacks(stepRunnable)
        timersPending = false
        index = 0
        scheduleNext(null)
        steps.getOrNull(0)?.holdMs?.let { Diagnostics.log("timer", "next tier in $it ms") }
        val ctx = appContext
        if (ctx != null && videoEligible(ctx)) {
            handler.removeCallbacks(videoProbe)
            handler.postDelayed(videoProbe, VIDEO_PROBE_MS)
        }
    }

    /** Refreshes the foreground package without polling; called on resume. */
    fun refreshForegroundPackage() = handler.post {
        val ctx = appContext ?: return@post
        if (started && !screenOff && videoEligible(ctx)) {
            val changed = updateVideoState(ctx, "resume")
            if (changed) {
                SessionArbiter.bumpEpoch()
                applyCurrent("video-resume")
            }
        }
        if (!OneHzPreferences.hasAppProfiles(ctx)) return@post
        val pkg = ShizukuGateway.foregroundPackage()
        if (!pkg.isNullOrBlank() && pkg != foregroundPackage) {
            foregroundPackage = pkg
            if (started && !screenOff) {
                SessionArbiter.bumpEpoch()
                applyCurrent("foreground")
            }
        }
    }

    /** Re-anchors the pending staircase when the profile changes while idle. */
    fun onTimingsChanged() = handler.post {
        if (!started || screenOff || isDown) return@post
        if (!timersPending || lastUpAt == 0L) return@post
        val elapsed = (SystemClock.elapsedRealtime() - lastUpAt).coerceAtLeast(0L)
        val decision = PolicyResolver.resolve(appContext ?: return@post, screenOff, ecoActive, foregroundPackage, index)
        val profile = (decision as? PolicyDecision.Apply)?.policy?.profile ?: run {
            applyCurrent("timings")
            return@post
        }
        steps = profile.steps
        var accumulated = 0L
        var target = 0
        while (target < steps.lastIndex) {
            val hold = steps[target].holdMs ?: break
            if (elapsed < accumulated + hold) break
            accumulated += hold
            target++
        }
        index = target
        applyCurrent("timings")
        if (index >= steps.lastIndex) {
            timersPending = false
            return@post
        }
        val remaining = (accumulated + (steps[index].holdMs ?: 0L) - elapsed).coerceAtLeast(0L)
        scheduleNext(remaining)
        Diagnostics.log("timer", "rescheduled: tier ${index + 1} for another $remaining ms")
    }

    /**
     * SCREEN_OFF means "screen off / AOD"; SCREEN_ON and USER_PRESENT resume normal control.
     * Refresh-rate changes never reach this method.
     */
    fun setScreenOff(value: Boolean) = handler.post { applyScreenState(value, fromBroadcast = true) }

    private fun applyScreenState(value: Boolean, fromBroadcast: Boolean) {
        val changed = value != screenOff
        screenOff = value
        if (!started) return
        if (changed) SessionArbiter.bumpEpoch()

        if (value) {
            if (changed) {
                Diagnostics.log("display", "SCREEN_OFF → AOD")
                clearVideoState("screen-off")
                handler.removeCallbacks(stepRunnable)
                timersPending = false
                isDown = false
                lowRechecks = 0
                index = 0
                ShizukuGateway.stopTouchMonitoring()
            } else if (fromBroadcast) {
                Diagnostics.log("display", "repeated SCREEN_OFF; reapplying AOD")
            }
            applyCurrent("screen-off")
        } else {
            if (changed) {
                Diagnostics.log("display", "screen on")
                handler.removeCallbacks(stepRunnable)
                timersPending = false
                isDown = false
                lowRechecks = 0
                index = 0
            }
            wake(if (fromBroadcast) "screen-on" else "state")
        }
    }

    /**
     * Wake/unlock pulse: start the staircase at the first step so the lock screen and the
     * unlock animation are not rendered at the resting rate. Debounced because SCREEN_ON and
     * USER_PRESENT usually arrive together.
     */
    private fun wake(source: String) {
        if (screenOff || isDown) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeAt < WAKE_DEBOUNCE_MS) return
        lastWakeAt = now
        lastUpAt = now
        lowRechecks = 0
        handler.removeCallbacks(stepRunnable)
        timersPending = false
        index = 0
        Diagnostics.log("wake", "$source -> tier 1")
        applyCurrent("wake:$source", resetTo = 0)
        if (ShizukuGateway.service != null && steps.size > 1) scheduleNext(null)
    }

    fun setForegroundPackage(packageName: String?) = handler.post {
        val changed = packageName != foregroundPackage
        foregroundPackage = packageName
        if (changed && videoActive && packageName != videoPackage) {
            clearVideoState("foreground-event")
        }
        if (changed) SessionArbiter.bumpEpoch()
        if (started && !screenOff) applyCurrent("foreground-event")
    }

    fun setPowerSaveMode(active: Boolean) = handler.post {
        val changed = active != ecoActive
        ecoActive = active
        if (started && !screenOff) {
            if (changed) SessionArbiter.bumpEpoch()
            handler.removeCallbacks(stepRunnable)
            timersPending = false
            index = 0
            applyCurrent("eco")
        }
    }

    fun reapply() = handler.post { if (started) applyCurrent("manual") }

    /** The privileged service was recreated: force a monitor check and a fresh apply. */
    fun onServiceAvailable() = handler.post {
        monitoringNeeded = false
        videoFailures = 0
        if (started) applyCurrent("service-ready")
    }

    /** Re-reads screen/power-save state after a broadcast may have been missed. */
    fun refreshSystemState() = handler.post {
        val ctx = appContext ?: return@post
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val newOff = pm?.isInteractive == false
        ecoActive = pm?.isPowerSaveMode == true
        if (!started) return@post
        if (newOff != screenOff) {
            applyScreenState(newOff, fromBroadcast = false)
        } else if (timersPending) {
            SessionArbiter.audit()
        } else {
            wake("resume")
        }
    }

    /** Forces the first step for two seconds. */
    fun testMax() = handler.post {
        if (!started) return@post
        applyCurrent("test-first", resetTo = 0)
        handler.postDelayed({ reapply() }, 2_000L)
    }

    /** Forces the resting step (last one) for three seconds. */
    fun testLow() = handler.post {
        if (!started) return@post
        applyCurrent("test-last", resetTo = steps.lastIndex.coerceAtLeast(0))
        handler.postDelayed({ reapply() }, 3_000L)
    }

    /** Walks the whole staircase, independent of touch detection. */
    fun testCycle() = handler.post {
        if (!started) return@post
        val count = steps.size.coerceAtLeast(1)
        timersPending = false
        applyCurrent("test-cycle-first", resetTo = 0)
        if (count > 1) {
            handler.postDelayed({
                if (!started) return@postDelayed
                applyCurrent("test-cycle-middle", resetTo = 1)
            }, 1_000L)
        }
        if (count > 2) {
            handler.postDelayed({
                if (!started) return@postDelayed
                applyCurrent("test-cycle-last", resetTo = count - 1)
            }, 6_000L)
        }
    }

    private fun scheduleNext(delayOverride: Long?) {
        val hold = steps.getOrNull(index)?.holdMs
        if (hold == null) {
            timersPending = false
            return
        }
        nextIndex = index + 1
        timersPending = true
        handler.removeCallbacks(stepRunnable)
        handler.postDelayed(stepRunnable, delayOverride ?: hold)
    }

    private fun applyCurrent(reason: String, resetTo: Int? = null) {
        val ctx = appContext ?: return
        if (!OneHzPreferences.isEnabled(ctx)) {
            goIdle(null)
            return
        }
        Diagnostics.log("engine", "apply reason=$reason")
        // Always trust the live system state over a cached broadcast.
        ecoActive = (ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
        if (videoActive && !videoEligible(ctx)) clearVideoState("feature-off")

        when (val decision = PolicyResolver.resolve(ctx, screenOff, ecoActive, foregroundPackage, index)) {
            is PolicyDecision.Idle -> goIdle(decision.reason)
            is PolicyDecision.Apply -> {
                val policy = decision.policy
                steps = policy.profile.steps
                // A new situation while idle starts at the resting step. During a touch
                // sequence the current position is kept; a touch always starts at the first step.
                val target = when {
                    screenOff -> 0
                    resetTo != null -> resetTo
                    isDown || timersPending -> index
                    else -> Int.MAX_VALUE
                }
                index = policy.profile.clampedIndex(target)

                // Video is a floor on top of the app/normal/ECO profile. Check it right
                // before the resting step so a video is never left at the minimum.
                if (!screenOff && !videoActive && index >= policy.profile.lastIndex && videoEligible(ctx)) {
                    updateVideoState(ctx, "rest")
                }

                val baseHz = policy.profile.hzAt(index) ?: run {
                    goIdle(IdleReason.EMPTY_PROFILE)
                    return
                }
                val videoMin = OneHzPreferences.videoMinHz(ctx)
                val videoApplied = videoActive && !screenOff
                val hz = if (videoApplied) VideoSignals.floorHz(baseHz, videoMin) else baseHz
                if (screenOff) timersPending = false

                Diagnostics.idleReason.value = null
                Diagnostics.activeStep.value = index + 1
                Diagnostics.activeStepCount.value = policy.profile.steps.size
                Diagnostics.activeTierKey.value = when {
                    screenOff -> "AOD"
                    index == 0 -> "MAX"
                    index == policy.profile.steps.lastIndex -> "LOW"
                    else -> "MID"
                }
                val layer = if (videoApplied) "${policy.layer}·video" else policy.layer
                Diagnostics.activeTier.value = if (screenOff) {
                    "AOD (${hz} Hz)"
                } else {
                    "tier ${index + 1}/${policy.profile.steps.size} · $layer · ${hz} Hz"
                }
                syncTouchMonitoring(
                    !screenOff && (policy.profile.steps.size > 1 || videoEligible(ctx))
                )
                SessionArbiter.apply(
                    ctx,
                    TierRequest(hz, index, policy.profile.steps.size, layer),
                    screenOff,
                    ecoActive
                )
                if (videoApplied) scheduleVideoRecheck() else handler.removeCallbacks(videoRecheck)
            }
        }
    }

    private fun goIdle(reason: IdleReason?) {
        steps = emptyList()
        index = 0
        nextIndex = 0
        timersPending = false
        handler.removeCallbacks(stepRunnable)
        clearVideoState("idle")
        SessionArbiter.release(appContext)
        Diagnostics.idleReason.value = reason
        Diagnostics.activeStep.value = 0
        Diagnostics.activeStepCount.value = 0
        Diagnostics.appliedHz.value = 0
        Diagnostics.observedHz.value = 0
        Diagnostics.activeTierKey.value = "OFF"
        Diagnostics.activeTier.value = when (reason) {
            IdleReason.AOD_DISABLED -> "no control: AOD disabled"
            IdleReason.ECO_PROFILE_DISABLED -> "no control: ECO profile disabled"
            IdleReason.NORMAL_PROFILE_DISABLED -> "no control: normal profile disabled"
            IdleReason.EMPTY_PROFILE -> "no control: no tiers configured"
            null -> "OneHZ disabled"
        }
        syncTouchMonitoring(false)
    }

    private fun syncTouchMonitoring(needed: Boolean) {
        if (needed == monitoringNeeded) return
        monitoringNeeded = needed
        if (needed) {
            ShizukuGateway.ensureInputMonitoring()
        } else {
            ShizukuGateway.stopTouchMonitoring()
        }
        Diagnostics.log("touch", if (needed) "monitor required" else "monitor released")
    }

    // --------------------------------------------------------------------- video

    private fun videoEligible(ctx: Context): Boolean =
        OneHzPreferences.videoEnabled(ctx)

    private fun scheduleVideoRecheck() {
        handler.removeCallbacks(videoRecheck)
        if (started && !screenOff && videoActive) {
            handler.postDelayed(videoRecheck, VIDEO_RECHECK_MS)
        }
    }

    private fun clearVideoState(reason: String) {
        val was = videoActive
        videoActive = false
        videoPackage = null
        videoSource = VideoSignals.Source.NONE
        videoFailures = 0
        Diagnostics.videoSource.value = VideoSignals.Source.NONE
        Diagnostics.videoDetail.value = reason
        handler.removeCallbacks(videoRecheck)
        handler.removeCallbacks(videoProbe)
        if (was) Diagnostics.log("video", "stopped ($reason)")
    }

    /**
     * Updates the video state and returns true when it changed.
     *
     * The caller decides whether to reapply, so [applyCurrent] can consume the new state
     * without re-entering itself. Only one privileged check runs at a time.
     */
    private fun updateVideoState(ctx: Context, reason: String): Boolean {
        if (videoCheckInFlight) return false
        if (!videoEligible(ctx)) {
            val was = videoActive
            if (was) clearVideoState("feature-off")
            return was
        }
        videoCheckInFlight = true
        val verdict = try {
            ShizukuGateway.videoPlayback()
        } finally {
            videoCheckInFlight = false
        }

        if (verdict.source == VideoSignals.Source.UNKNOWN) {
            videoFailures++
            Diagnostics.log("video", "check unavailable ($reason) $videoFailures/$VIDEO_UNKNOWN_LIMIT")
            if (videoActive && videoFailures >= VIDEO_UNKNOWN_LIMIT) {
                clearVideoState("unknown")
                return true
            }
            return false
        }
        videoFailures = 0

        val pkg = verdict.packageName
        if (!pkg.isNullOrBlank()) foregroundPackage = pkg
        val active = verdict.source == VideoSignals.Source.PLAYING
        val wasActive = videoActive
        val changed = active != wasActive ||
            (active && (pkg != videoPackage || verdict.source != videoSource))

        videoActive = active
        videoPackage = if (active) pkg else null
        videoSource = if (active) verdict.source else VideoSignals.Source.NONE
        Diagnostics.videoSource.value = videoSource
        Diagnostics.videoDetail.value = buildString {
            if (!pkg.isNullOrBlank()) append(pkg)
            verdict.detail?.let {
                if (isNotEmpty()) append(" · ")
                append(it)
            }
        }.ifBlank { "—" }
        if (active) {
            if (changed) {
                Diagnostics.log("video", "active (${verdict.source.name.lowercase()}) in $pkg ($reason)")
            }
        } else if (wasActive) {
            Diagnostics.log("video", "inactive ($reason) in ${pkg ?: "?"}")
        }
        return changed
    }
}
