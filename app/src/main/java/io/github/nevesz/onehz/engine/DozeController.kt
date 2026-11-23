package io.github.nevesz.onehz.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import io.github.nevesz.onehz.core.DozeDecision
import io.github.nevesz.onehz.core.DozeDecisionResolver
import io.github.nevesz.onehz.core.DozeInput
import io.github.nevesz.onehz.core.DozePhase
import io.github.nevesz.onehz.core.DozeSkip
import io.github.nevesz.onehz.core.DozeState
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SleepLedger
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.core.SleepRecord
import io.github.nevesz.onehz.core.SleepWindow
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import java.util.Calendar

/**
 * Forces deep or light idle when the screen turns off, and leaves it when the screen turns
 * on. Screen-off is the anchor, but charging, audio playback and preference changes are
 * event-driven and re-evaluated while the screen is off; nothing is polled.
 *
 * All work runs on its own thread. The wake path is never blocked by a shell call: leaving
 * idle is dispatched here while the refresh-rate engine applies its wake pulse in parallel.
 */
object DozeController {

    private const val TAG = "sleep"
    private const val FORCE_DELAY_MS = 800L
    private const val VERIFY_RETRY_MS = 400L
    private const val VERIFY_FINAL_MS = 1_200L
    private const val TEST_DURATION_MS = 10_000L
    private const val MUSIC_CHECK_MS = 200L

    private val thread = HandlerThread("OneHzDoze").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var started = false

    @Volatile
    private var appContext: Context? = null

    private var open: SleepRecord? = null
    private var pendingDeep: Boolean? = null
    private var testing = false
    private var verifyGeneration = 0
    private var screenOffAt = 0L

    /** Guard values cached from their events; read on the controller thread only. */
    private var charging = false
    private var musicActive = false

    private var audioManager: AudioManager? = null

    private val musicCheck = Runnable {
        val ctx = appContext ?: return@Runnable
        if (!started) return@Runnable
        val active = isMusicActive(ctx)
        if (active == musicActive) return@Runnable
        musicActive = active
        onGuardChanged(ctx, "music")
    }

    private val audioCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: MutableList<AudioPlaybackConfiguration>) {
            // Playback callbacks arrive in bursts when a track changes. Coalesce them and
            // compare the final value before touching Doze.
            handler.removeCallbacks(musicCheck)
            handler.postDelayed(musicCheck, MUSIC_CHECK_MS)
        }
    }

    private val forceRunnable = Runnable {
        val ctx = appContext ?: return@Runnable
        if (pendingDeep == null) return@Runnable
        pendingDeep = null
        if (!started || isInteractive(ctx)) return@Runnable
        // Charging, music or a pause may have changed during the short delay.
        when (val again = decide(ctx)) {
            is DozeDecision.Skip -> {
                Diagnostics.dozePhase.value = phaseOf(again.reason)
                Diagnostics.log(TAG, "skip after delay: ${again.reason}")
            }
            is DozeDecision.Force -> {
                val commandStart = SystemClock.elapsedRealtime()
                val ok = ShizukuGateway.forceIdle(again.deep)
                val elapsed = SystemClock.elapsedRealtime() - commandStart
                Diagnostics.log(
                    TAG,
                    "force deep=${again.deep} command=${if (ok) "ok" else "failed"} in ${elapsed}ms",
                )
                scheduleVerify(ctx, again.deep)
            }
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (started) return
        started = true
        handler.post {
            charging = isCharging(ctx)
            musicActive = isMusicActive(ctx)
            (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.let { manager ->
                manager.registerAudioPlaybackCallback(audioCallback, handler)
                audioManager = manager
            }
            open = SleepPreferences.openRecord(ctx)
            if (open != null && isInteractive(ctx)) {
                closeSession(ctx, System.currentTimeMillis(), "restart while awake")
            }
            // A forced state that cannot be confirmed is cleared instead of trusted.
            if (SleepPreferences.forced(ctx)) {
                val stillIdle = !isInteractive(ctx) &&
                    ShizukuGateway.service != null &&
                    DozeState.isIdleEncoded(ShizukuGateway.idleState())
                if (!stillIdle) unforce(ctx, "start")
            }
            Diagnostics.dozeActiveSince.value =
                if (SleepPreferences.forced(ctx)) SleepPreferences.forcedAt(ctx) else 0L
            refreshPhase(ctx, "start")
        }
    }

    /** Fail-safe: never leave the device forced after the engine is disabled. */
    fun stop() {
        started = false
        handler.post {
            handler.removeCallbacksAndMessages(null)
            audioManager?.let { manager ->
                runCatching { manager.unregisterAudioPlaybackCallback(audioCallback) }
            }
            audioManager = null
            pendingDeep = null
            val ctx = appContext
            if (ctx != null) {
                if (SleepPreferences.forced(ctx) || testing) {
                    unforce(ctx, "stop")
                }
                closeSession(ctx, System.currentTimeMillis(), "stop")
            }
            testing = false
            open = null
            Diagnostics.dozeActiveSince.value = 0L
            Diagnostics.dozePhase.value = DozePhase.OFF
        }
    }

    fun onScreenOff() = handler.post {
        if (!started) return@post
        val ctx = appContext ?: return@post
        if (!isActive(ctx)) {
            Diagnostics.dozePhase.value = DozePhase.OFF
            return@post
        }
        val now = System.currentTimeMillis()
        screenOffAt = SystemClock.elapsedRealtime()
        verifyGeneration++
        // A missed screen-on must not leave two sessions open.
        if (open != null) closeSession(ctx, now, "stale session")
        open = SleepLedger.start(now, batteryLevel(ctx), SleepPreferences.deep(ctx))
        saveOpen(ctx)
        charging = isCharging(ctx)
        musicActive = isMusicActive(ctx)

        when (val decision = decide(ctx)) {
            is DozeDecision.Skip -> {
                pendingDeep = null
                Diagnostics.dozePhase.value = phaseOf(decision.reason)
                Diagnostics.dozeDetail.value = decision.reason.name
                Diagnostics.log(TAG, "skip: ${decision.reason}")
            }
            is DozeDecision.Force -> {
                pendingDeep = decision.deep
                Diagnostics.dozePhase.value = DozePhase.READY
                handler.removeCallbacks(forceRunnable)
                handler.postDelayed(forceRunnable, FORCE_DELAY_MS)
                Diagnostics.log(TAG, "force scheduled (deep=${decision.deep})")
            }
        }
    }

    fun onScreenOn() = handler.post {
        val ctx = appContext ?: return@post
        pendingDeep = null
        verifyGeneration++
        handler.removeCallbacks(forceRunnable)
        charging = isCharging(ctx)
        musicActive = isMusicActive(ctx)
        closeSession(ctx, System.currentTimeMillis(), "screen on")
        if (SleepPreferences.forced(ctx) || testing) {
            unforce(ctx, "screen on")
        }
        testing = false
        Diagnostics.dozeActiveSince.value = 0L
        refreshPhase(ctx, "screen on")
    }

    fun onIdleChanged() = handler.post {
        val ctx = appContext ?: return@post
        if (!started || !isActive(ctx) || isInteractive(ctx)) return@post
        val idle = isIdle(ctx)
        val now = System.currentTimeMillis()
        open = if (idle) SleepLedger.enterIdle(open, now) else SleepLedger.leaveIdle(open, now)
        saveOpen(ctx)
        Diagnostics.log(TAG, if (idle) "idle entered" else "idle left (maintenance)")
        refreshPhase(ctx, "idle")
    }

    fun onPowerChanged(pluggedIn: Boolean) = handler.post {
        val ctx = appContext ?: return@post
        if (!started) return@post
        charging = pluggedIn
        Diagnostics.log(TAG, "power ${if (pluggedIn) "connected" else "disconnected"}")
        onGuardChanged(ctx, "power")
    }

    fun onServiceUnavailable() = handler.post {
        val ctx = appContext ?: return@post
        if (!started || !isActive(ctx)) return@post
        Diagnostics.dozePhase.value = DozePhase.WAITING_SERVICE
        Diagnostics.log(TAG, "privileged service unavailable")
    }

    fun onServiceAvailable() = handler.post {
        val ctx = appContext ?: return@post
        if (!started) return@post
        // The service returned while the screen is on: clear a state we cannot confirm,
        // even if the feature was disabled while the service was away.
        if (SleepPreferences.forced(ctx) && isInteractive(ctx)) {
            unforce(ctx, "service available")
        }
        if (!isActive(ctx)) {
            Diagnostics.dozePhase.value = DozePhase.OFF
            return@post
        }
        // Events may have been missed while the service was away.
        charging = isCharging(ctx)
        musicActive = isMusicActive(ctx)
        onGuardChanged(ctx, "service available")
    }

    fun onPrefsChanged() = handler.post {
        val ctx = appContext ?: return@post
        if (!isActive(ctx)) {
            pendingDeep = null
            handler.removeCallbacks(forceRunnable)
            if (SleepPreferences.forced(ctx) || testing) {
                unforce(ctx, "feature disabled")
            }
            testing = false
            closeSession(ctx, System.currentTimeMillis(), "feature disabled")
            Diagnostics.dozeActiveSince.value = 0L
            Diagnostics.dozePhase.value = DozePhase.OFF
            Diagnostics.log(TAG, "feature off")
            return@post
        }
        onGuardChanged(ctx, "preferences")
    }

    /**
     * Applies a guard change to the current screen-off state without waiting for the next
     * screen cycle. Runs on the controller thread.
     */
    private fun onGuardChanged(ctx: Context, source: String) {
        if (!started) return
        if (isInteractive(ctx)) {
            refreshPhase(ctx, source)
            return
        }
        if (!isActive(ctx)) {
            Diagnostics.dozePhase.value = DozePhase.OFF
            return
        }
        if (testing) return
        when (val decision = decide(ctx)) {
            is DozeDecision.Skip -> {
                pendingDeep = null
                verifyGeneration++
                handler.removeCallbacks(forceRunnable)
                if (SleepPreferences.forced(ctx)) {
                    unforce(ctx, source)
                }
                Diagnostics.dozePhase.value = phaseOf(decision.reason)
                Diagnostics.dozeDetail.value = decision.reason.name
                Diagnostics.log(TAG, "$source -> skip: ${decision.reason}")
            }
            is DozeDecision.Force -> {
                if (!SleepPreferences.forced(ctx)) {
                    pendingDeep = decision.deep
                    handler.removeCallbacks(forceRunnable)
                    // The AOD has already settled; this change does not need the screen-off delay.
                    handler.post(forceRunnable)
                    Diagnostics.log(TAG, "$source -> force now (deep=${decision.deep})")
                } else {
                    Diagnostics.dozePhase.value = DozePhase.ACTIVE
                }
            }
        }
        refreshPhase(ctx, source)
    }

    fun pauseFor(durationMs: Long) = handler.post {
        val ctx = appContext ?: return@post
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(0L)
        SleepPreferences.setPausedUntil(ctx, until)
        pendingDeep = null
        handler.removeCallbacks(forceRunnable)
        if (SleepPreferences.forced(ctx)) {
            unforce(ctx, "pause")
        }
        Diagnostics.dozeActiveSince.value = 0L
        Diagnostics.dozePhase.value = DozePhase.PAUSED
        Diagnostics.log(TAG, "paused until $until")
    }

    fun resume() = handler.post {
        val ctx = appContext ?: return@post
        SleepPreferences.setPausedUntil(ctx, 0L)
        onGuardChanged(ctx, "resumed")
    }

    /** Forces idle for a few seconds and leaves again; used by the details sheet. */
    fun testNow() = handler.post {
        val ctx = appContext ?: return@post
        if (!isActive(ctx)) {
            Diagnostics.dozePhase.value = DozePhase.OFF
            return@post
        }
        if (ShizukuGateway.service == null) {
            Diagnostics.dozePhase.value = DozePhase.WAITING_SERVICE
            return@post
        }
        testing = true
        val deep = SleepPreferences.deep(ctx)
        ShizukuGateway.forceIdle(deep)
        handler.postDelayed({
            val raw = ShizukuGateway.idleState()
            Diagnostics.dozeDetail.value = raw.ifBlank { "no state" }
            Diagnostics.log(TAG, "test force deep=$deep -> ${raw.ifBlank { "no state" }}")
        }, VERIFY_FINAL_MS)
        handler.postDelayed({
            unforce(ctx, "test finished")
            testing = false
            refreshPhase(ctx, "test")
        }, TEST_DURATION_MS)
    }

    private fun scheduleVerify(ctx: Context, deep: Boolean) {
        val generation = ++verifyGeneration
        handler.post { verify(ctx, deep, 0, generation) }
    }

    /**
     * Confirms idle without paying for a full `dumpsys` when the public deep-idle check is
     * already true. Retries at 0/400/1200 ms instead of waiting a fixed 1.2 s.
     */
    private fun verify(ctx: Context, deep: Boolean, attempt: Int, generation: Int) {
        if (generation != verifyGeneration || !started || isInteractive(ctx)) return
        val quick = deep && isIdle(ctx)
        val raw = if (quick) "" else ShizukuGateway.idleState()
        if (quick || DozeState.isIdleEncoded(raw)) {
            val now = System.currentTimeMillis()
            SleepPreferences.setForced(ctx, true, now)
            Diagnostics.dozePhase.value = DozePhase.ACTIVE
            Diagnostics.dozeActiveSince.value = now
            Diagnostics.dozeDetail.value = raw.ifBlank { "power manager" }
            val elapsed = SystemClock.elapsedRealtime() - screenOffAt
            Diagnostics.log(TAG, "idle confirmed after ${elapsed}ms (${raw.ifBlank { "power manager" }})")
            return
        }
        if (attempt < 2) {
            val delay = if (attempt == 0) VERIFY_RETRY_MS else VERIFY_FINAL_MS - VERIFY_RETRY_MS
            handler.postDelayed({ verify(ctx, deep, attempt + 1, generation) }, delay)
            return
        }
        Diagnostics.dozePhase.value = DozePhase.FAILED
        Diagnostics.dozeDetail.value = raw.ifBlank { "no state" }
        Diagnostics.log(TAG, "not idle after force: ${raw.ifBlank { "no state" }}")
    }

    private fun decide(ctx: Context): DozeDecision {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }
        val window = SleepPreferences.window(ctx)
        return DozeDecisionResolver.resolve(
            DozeInput(
                masterEnabled = OneHzPreferences.isEnabled(ctx),
                featureEnabled = SleepPreferences.enabled(ctx),
                now = now,
                pausedUntil = SleepPreferences.pausedUntil(ctx),
                windowEnabled = SleepPreferences.windowEnabled(ctx),
                window = window,
                minuteOfDay = SleepWindow.minuteOfDay(
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                ),
                skipCharging = SleepPreferences.skipCharging(ctx),
                charging = charging,
                dozeWithMusic = SleepPreferences.dozeWithMusic(ctx),
                musicActive = musicActive,
                serviceReady = ShizukuGateway.service != null,
                deep = SleepPreferences.deep(ctx),
            ),
        )
    }

    private fun refreshPhase(ctx: Context, source: String) {
        Diagnostics.dozePhase.value = when {
            !isActive(ctx) -> DozePhase.OFF
            SleepPreferences.forced(ctx) -> DozePhase.ACTIVE
            SleepPreferences.pausedUntil(ctx) > System.currentTimeMillis() -> DozePhase.PAUSED
            ShizukuGateway.service == null -> DozePhase.WAITING_SERVICE
            else -> when (val decision = decide(ctx)) {
                is DozeDecision.Skip -> phaseOf(decision.reason)
                is DozeDecision.Force -> DozePhase.READY
            }
        }
        Diagnostics.log(TAG, "phase=${Diagnostics.dozePhase.value} ($source)")
    }

    /**
     * Leaves idle and clears the persisted flag only when the privileged service confirms the
     * command. If the service is unavailable, the flag is kept so the next reconnect or
     * screen-on retries instead of leaving the device forced with a false "off" state.
     */
    private fun unforce(ctx: Context, reason: String): Boolean {
        verifyGeneration++
        if (ShizukuGateway.service == null) {
            Diagnostics.log(TAG, "unforce pending ($reason): service unavailable")
            return false
        }
        val ok = ShizukuGateway.unforceIdle()
        if (ok) {
            SleepPreferences.setForced(ctx, false)
            Diagnostics.dozeActiveSince.value = 0L
            Diagnostics.log(TAG, "unforce ok ($reason)")
        } else {
            Diagnostics.log(TAG, "unforce failed ($reason); keeping the flag for retry")
        }
        return ok
    }

    private fun closeSession(ctx: Context, now: Long, reason: String) {
        val record = open ?: return
        val closed = SleepLedger.close(record, now, batteryLevel(ctx)) ?: return
        SleepPreferences.setRecords(
            ctx,
            (SleepPreferences.records(ctx) + closed).takeLast(SleepLedger.MAX_RECORDS),
        )
        SleepPreferences.setOpenRecord(ctx, null)
        open = null
        val minutes = (closed.endAt - closed.startAt).coerceAtLeast(0L) / 60_000L
        Diagnostics.log(TAG, "session closed ($reason, $minutes min)")
    }

    private fun saveOpen(ctx: Context) {
        SleepPreferences.setOpenRecord(ctx, open)
    }

    private fun isActive(ctx: Context): Boolean =
        OneHzPreferences.isEnabled(ctx) && SleepPreferences.enabled(ctx)

    private fun phaseOf(reason: DozeSkip): DozePhase = when (reason) {
        DozeSkip.MASTER_OFF, DozeSkip.DISABLED -> DozePhase.OFF
        DozeSkip.PAUSED -> DozePhase.PAUSED
        DozeSkip.OUTSIDE_WINDOW -> DozePhase.OUTSIDE_WINDOW
        DozeSkip.CHARGING -> DozePhase.CHARGING
        DozeSkip.MUSIC -> DozePhase.MUSIC
        DozeSkip.SERVICE -> DozePhase.WAITING_SERVICE
    }

    private fun powerManager(ctx: Context): PowerManager? =
        ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager

    private fun isInteractive(ctx: Context): Boolean = powerManager(ctx)?.isInteractive != false

    private fun isIdle(ctx: Context): Boolean = powerManager(ctx)?.isDeviceIdleMode == true

    private fun isCharging(ctx: Context): Boolean {
        // BatteryManager.isCharging() reads a property that some vendor builds do not
        // update. The sticky battery broadcast carries the same status shown by dumpsys.
        val manager = ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val intent = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        if (manager?.isCharging == true) return true
        return plugged != 0 || status == BatteryManager.BATTERY_STATUS_CHARGING
    }

    private fun isMusicActive(ctx: Context): Boolean =
        (ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager)?.isMusicActive == true

    private fun batteryLevel(ctx: Context): Int =
        (ctx.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?: -1
}
