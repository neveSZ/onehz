package io.github.nevesz.onehz.engine

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.provider.Settings
import io.github.nevesz.onehz.core.ActivationRulesLogic
import io.github.nevesz.onehz.core.AodBrightnessMath
import io.github.nevesz.onehz.core.DisplayProfileResolver
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.diagnostic.AodBrightnessInfo
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway
import java.util.Calendar

/**
 * Drives Samsung's `debug_aod_brightness` (Settings.System, 0..255) from the interactive
 * screen brightness.
 *
 * The AOD service reads that key once, when the AOD starts. Waiting for SCREEN_OFF can lose
 * that race (the AOD sometimes initializes ~45 ms after the screen turns off), so the value is
 * pre-written whenever the interactive brightness changes and only re-checked on SCREEN_OFF.
 * Everything is deduplicated by target, so the steady state costs zero writes.
 */
object AodBrightnessController {
    private const val TAG = "aod"
    private const val DEBOUNCE_MS = 400L

    private val thread = HandlerThread("OneHzAodBrightness").apply { start() }
    private val handler = Handler(thread.looper)

    @Volatile
    private var running = false
    @Volatile
    private var appContext: Context? = null
    private var observer: ContentObserver? = null

    private var lastScreenRaw = -1
    private var lastTarget = -1
    private var pendingSync = false

    private val debounceRunnable = Runnable {
        if (!pendingSync) return@Runnable
        pendingSync = false
        syncNow("brightness")
    }

    private val observerCallback = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (!running) return
            val context = appContext ?: return
            // During the AOD the setting may still change; the final value was already written
            // on SCREEN_OFF and the AOD cached it until the next lock.
            if (!isInteractive(context)) return
            pendingSync = true
            handler.removeCallbacks(debounceRunnable)
            handler.postDelayed(debounceRunnable, DEBOUNCE_MS)
        }
    }

    fun start(context: Context) {
        val ctx = context.applicationContext
        handler.post {
            appContext = ctx
            if (!DisplayProfileResolver.detect(ctx).supportsAodBrightness) {
                running = false
                unregisterObserver()
                pendingSync = false
                handler.removeCallbacks(debounceRunnable)
                Diagnostics.log(TAG, "AOD brightness is Samsung-only; skipped")
                return@post
            }
            if (!OneHzPreferences.isEnabled(ctx)) {
                running = false
                unregisterObserver()
                pendingSync = false
                handler.removeCallbacks(debounceRunnable)
                clearKey(ctx, "master-off")
                return@post
            }
            if (running) {
                syncNow("restart")
                return@post
            }
            running = true
            registerObserver(ctx)
            if (OneHzPreferences.aodBrightnessEnabled(ctx)) {
                syncNow("start")
            } else {
                clearKey(ctx, "start")
            }
        }
    }

    /** Clears the key when the service/master goes away. */
    fun stop() {
        val ctx = appContext
        handler.post {
            running = false
            unregisterObserver()
            pendingSync = false
            handler.removeCallbacks(debounceRunnable)
            if (ctx != null) clearKey(ctx, "stop")
        }
    }

    /** Shizuku went away: keep the key (the AOD keeps working) and wait for reconnection. */
    fun onServiceUnavailable() = handler.post {
        running = false
        unregisterObserver()
        pendingSync = false
        handler.removeCallbacks(debounceRunnable)
        Diagnostics.log(TAG, "Shizuku unavailable; AOD brightness control paused")
    }

    fun onScreenOff() = handler.post {
        if (!running) return@post
        pendingSync = false
        handler.removeCallbacks(debounceRunnable)
        syncNow("screen-off")
    }

    fun onScreenOn() = handler.post {
        if (!running) return@post
        syncNow("screen-on")
    }

    fun onPrefsChanged() = handler.post {
        val ctx = appContext ?: return@post
        if (!running) {
            start(ctx)
            return@post
        }
        if (isEnabled(ctx)) syncNow("prefs") else clearKey(ctx, "prefs")
    }

    /** Diagnostics safety net: disables the feature and removes the key. */
    fun restoreSystem(context: Context) {
        val ctx = context.applicationContext
        handler.post {
            appContext = ctx
            OneHzPreferences.setAodBrightnessEnabled(ctx, false)
            clearKey(ctx, "restore", force = true)
        }
    }

    private fun isEnabled(context: Context) =
        OneHzPreferences.isEnabled(context) &&
            OneHzPreferences.aodMasterEnabled(context) &&
            OneHzPreferences.aodBrightnessEnabled(context)

    private fun syncNow(reason: String) {
        val ctx = appContext ?: return
        if (!isEnabled(ctx)) {
            clearKey(ctx, reason)
            return
        }

        val raw = readScreenRaw(ctx)
        if (raw <= 0) {
            Diagnostics.log(TAG, "$reason: screen brightness unavailable ($raw); keeping current value")
            return
        }
        lastScreenRaw = raw

        val basePct = OneHzPreferences.aodBrightnessPct(ctx)
        val scheduled = OneHzPreferences.aodScheduleEnabled(ctx) &&
            ActivationRulesLogic.inWindow(
                currentMinute(),
                OneHzPreferences.aodScheduleStart(ctx),
                OneHzPreferences.aodScheduleEnd(ctx)
            )
        val pct = if (scheduled) OneHzPreferences.aodSchedulePct(ctx) else basePct
        val min = OneHzPreferences.aodBrightnessMinPct(ctx)
        val max = OneHzPreferences.aodBrightnessMaxPct(ctx)
        val effectivePct = pct.coerceIn(min, max)
        val target = AodBrightnessMath.compute(raw, effectivePct, min, max)

        if (target == lastTarget && OneHzPreferences.aodBrightnessWritten(ctx)) {
            publish(raw, target, pct, effectivePct, min, max, applied = true)
            return
        }

        if (ShizukuGateway.service == null) {
            Diagnostics.log(TAG, "$reason: target $target/255 waiting for Shizuku")
            return
        }

        val ok = ShizukuGateway.writeAodBrightness(target)
        if (ok) {
            lastTarget = target
            OneHzPreferences.setAodBrightnessWritten(ctx, true)
            val effective = if (effectivePct != pct) " (efetivo $effectivePct%)" else ""
            Diagnostics.log(
                TAG,
                "$reason: screen $raw -> $target/255 (chosen $pct%$effective · band $min-$max%)"
            )
        } else {
            Diagnostics.log(TAG, "$reason: failed to write $target/255")
        }
        publish(raw, target, pct, effectivePct, min, max, applied = ok)
    }

    private fun publish(
        raw: Int,
        target: Int,
        pct: Int,
        effectivePct: Int,
        min: Int,
        max: Int,
        applied: Boolean
    ) {
        Diagnostics.aodBrightness.value =
            AodBrightnessInfo(raw, target, pct, effectivePct, min, max, applied)
    }

    private fun readScreenRaw(context: Context): Int = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(-1).takeIf { it > 0 } ?: lastScreenRaw

    private fun isInteractive(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isInteractive != false

    private fun currentMinute(): Int = Calendar.getInstance().let {
        it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE)
    }

    private fun clearKey(context: Context, reason: String, force: Boolean = false) {
        if (!force && !OneHzPreferences.aodBrightnessWritten(context)) {
            Diagnostics.aodBrightness.value = null
            return
        }
        if (ShizukuGateway.service == null) {
            Diagnostics.log(TAG, "$reason: cleanup pending (service unavailable)")
            return
        }
        if (ShizukuGateway.clearAodBrightness()) {
            OneHzPreferences.setAodBrightnessWritten(context, false)
            lastTarget = -1
            Diagnostics.aodBrightness.value = null
            Diagnostics.log(TAG, "$reason: key removed (AOD back to system control)")
        } else {
            Diagnostics.log(TAG, "$reason: failed to remove the key")
        }
    }

    private fun registerObserver(context: Context) {
        if (observer != null) return
        val callback = observerCallback
        runCatching {
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, callback
            )
            context.contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, callback
            )
        }.onFailure { Diagnostics.log(TAG, "observer failed: ${it.message}") }
        observer = callback
    }

    private fun unregisterObserver() {
        val callback = observer ?: return
        runCatching { appContext?.contentResolver?.unregisterContentObserver(callback) }
        observer = null
    }
}
