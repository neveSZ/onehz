package io.github.nevesz.onehz.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.SleepPreferences
import io.github.nevesz.onehz.shizuku.ShizukuGateway

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return
        // The platform starts from a clean idle state after a reboot.
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            SleepPreferences.setForced(context, false)
        }
        if (!OneHzPreferences.isEnabled(context)) return
        if (!DisplayGate.evaluate(context).eligible) return

        ShizukuGateway.init(
            context = context,
            onReady = {
                SessionArbiter.invalidate()
                SessionArbiter.restorePending(context)
                OneHzEngine.start(context)
                AodBrightnessController.start(context)
                OneHzKeepAliveService.start(context)
                OneHzEngine.refreshSystemState()
            },
            onUnavailable = { AodBrightnessController.onServiceUnavailable() }
        )
        ShizukuGateway.refresh()
        OneHzKeepAliveService.start(context)
    }
}
