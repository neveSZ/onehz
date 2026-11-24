package io.github.nevesz.onehz.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Single source of truth for the battery optimization exemption.
 *
 * The setup flow and the settings warning must agree on both the state and the request, so
 * both read this object. The exemption is optional: OneHZ works without it, but on some ROMs
 * the notification is throttled while the app is in the background.
 */
object BatteryOptimizationAccess {

    fun isExempt(context: Context): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return power?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /**
     * Opens the system dialog that asks for the exemption. Sideload-only app: the request is
     * always explicit, never a background optimization toggle.
     */
    @SuppressLint("BatteryLife")
    fun request(context: Context) {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                )
            )
        }
    }
}
