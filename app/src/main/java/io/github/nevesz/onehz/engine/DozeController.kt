package io.github.nevesz.onehz.engine

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
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
 * on. Every guard is evaluated once at screen-off; nothing is polled while the device sleeps.
 *
 * All work runs on its own thread. The wake path is never blocked by a shell call: leaving
 * idle is dispatched here while the refresh-rate engine applies its wake pulse in parallel.
 */
object DozeController {

    private const val TAG = "sleep"
    private const val FORCE_DELAY_MS = 800L
    private const val VERIFY_DELAY_MS = 1_200L
    private const val TEST_DURATION_MS = 10_000L

    private val thread = HandlerThread("OneHzDoze").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var started = false

    @Volatile
    private var appContext: Context? = null

    private var open: SleepRecord? = null
    private var pendingDeep: Boolean? = null
    private var testing = false

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
                val ok = ShizukuGateway.forceIdle(again.deep)
                Diagnostics.log(TAG, "force deep=${again.deep} command=${if (ok) "ok" else "failed"}")
                handler.postDelayed({ verify(ctx) }, VERIFY_DELAY_MS)
            }
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        if (started) return
        started = true
        handler.post {
            open = SleepPreferences.openRecord(ctx)
            if (open != null && isInteractive(ctx)) {
                closeSession(ctx, System.currentTimeMillis(), "restart while awake")
            }
            // A forced state that cannot be confirmed is cleared instead of trusted.
            if (SleepPreferences.forced(ctx)) {
                val stillIdle = !isInteractive(ctx) &&
                    ShizukuGateway.service != null &&
                    DozeState.isIdleEncoded(ShizukuGateway.idleState())
                if (!stillIdle) {
                    if (ShizukuGateway.service != null) ShizukuGateway.unforceIdle()
                    SleepPreferences.setForced(ctx, false)
                }
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
            pendingDeep = null
            val ctx = appContext
            if (ctx != null) {
                if (SleepPreferences.forced(ctx) || testing) {
                    ShizukuGateway.unforceIdle()
                    SleepPreferences.setForced(ctx, false)
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
        // A missed screen-on must not leave two sessions open.
        if (open != null) closeSession(ctx, now, "stale session")
        open = SleepLedger.start(now, batteryLevel(ctx), SleepPreferences.deep(ctx))
        saveOpen(ctx)

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
        handler.removeCallbacks(forceRunnable)
        closeSession(ctx, System.currentTimeMillis(), "screen on")
        if (SleepPreferences.forced(ctx) || testing) {
            val ok = ShizukuGateway.unforceIdle()
            SleepPreferences.setForced(ctx, false)
            Diagnostics.log(TAG, "unforce ${if (ok) "ok" else "failed"}")
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

    fun onPowerChanged() = handler.post {
        val ctx = appContext ?: return@post
        if (!isCharging(ctx)) return@post
        if (!SleepPreferences.skipCharging(ctx)) return@post
        if (!SleepPreferences.forced(ctx)) return@post
        ShizukuGateway.unforceIdle()
        SleepPreferences.setForced(ctx, false)
        Diagnostics.dozeActiveSince.value = 0L
        Diagnostics.dozePhase.value = DozePhase.CHARGING
        Diagnostics.log(TAG, "charger connected: left Doze")
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
        if (!isActive(ctx)) {
            Diagnostics.dozePhase.value = DozePhase.OFF
            return@post
        }
        // The service returned while the screen is on: clear a state we cannot confirm.
        if (SleepPreferences.forced(ctx) && isInteractive(ctx)) {
            ShizukuGateway.unforceIdle()
            SleepPreferences.setForced(ctx, false)
        }
        val decision = decide(ctx)
        if (!isInteractive(ctx) && decision is DozeDecision.Force) {
            pendingDeep = decision.deep
            handler.removeCallbacks(forceRunnable)
            handler.postDelayed(forceRunnable, FORCE_DELAY_MS)
        }
        refreshPhase(ctx, "service available")
    }

    fun onPrefsChanged() = handler.post {
        val ctx = appContext ?: return@post
        if (!isActive(ctx)) {
            pendingDeep = null
            handler.removeCallbacks(forceRunnable)
            if (SleepPreferences.forced(ctx) || testing) {
                ShizukuGateway.unforceIdle()
                SleepPreferences.setForced(ctx, false)
            }
            testing = false
            closeSession(ctx, System.currentTimeMillis(), "feature disabled")
            Diagnostics.dozeActiveSince.value = 0L
            Diagnostics.dozePhase.value = DozePhase.OFF
            Diagnostics.log(TAG, "feature off")
            return@post
        }
        refreshPhase(ctx, "preferences")
    }

    fun pauseFor(durationMs: Long) = handler.post {
        val ctx = appContext ?: return@post
        val until = System.currentTimeMillis() + durationMs.coerceAtLeast(0L)
        SleepPreferences.setPausedUntil(ctx, until)
        pendingDeep = null
        handler.removeCallbacks(forceRunnable)
        if (SleepPreferences.forced(ctx)) {
            ShizukuGateway.unforceIdle()
            SleepPreferences.setForced(ctx, false)
        }
        Diagnostics.dozeActiveSince.value = 0L
        Diagnostics.dozePhase.value = DozePhase.PAUSED
        Diagnostics.log(TAG, "paused until $until")
    }

    fun resume() = handler.post {
        val ctx = appContext ?: return@post
        SleepPreferences.setPausedUntil(ctx, 0L)
        refreshPhase(ctx, "resumed")
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
        }, VERIFY_DELAY_MS)
        handler.postDelayed({
            if (!isInteractive(ctx)) return@postDelayed
            ShizukuGateway.unforceIdle()
            testing = false
            Diagnostics.log(TAG, "test finished")
            refreshPhase(ctx, "test")
        }, TEST_DURATION_MS)
    }

    private fun verify(ctx: Context) {
        if (!started) return
        val quick = isIdle(ctx)
        val raw = ShizukuGateway.idleState()
        if (quick || DozeState.isIdleEncoded(raw)) {
            SleepPreferences.setForced(ctx, true, System.currentTimeMillis())
            Diagnostics.dozePhase.value = DozePhase.ACTIVE
            Diagnostics.dozeActiveSince.value = System.currentTimeMillis()
            Diagnostics.dozeDetail.value = raw.ifBlank { "power manager" }
            Diagnostics.log(TAG, "idle confirmed (${raw.ifBlank { "power manager" }})")
        } else {
            Diagnostics.dozePhase.value = DozePhase.FAILED
            Diagnostics.dozeDetail.value = raw.ifBlank { "no state" }
            Diagnostics.log(TAG, "not idle after force: ${raw.ifBlank { "no state" }}")
        }
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
                charging = isCharging(ctx),
                skipMusic = SleepPreferences.skipMusic(ctx),
                musicActive = isMusicActive(ctx),
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
