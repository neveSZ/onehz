package io.github.nevesz.onehz.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import io.github.nevesz.onehz.diagnostic.Diagnostics

class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Diagnostics.log("screen", "SCREEN_OFF → AOD")
                AodBrightnessController.onScreenOff()
                OneHzEngine.setScreenOff(true)
            }
            Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                Diagnostics.log("screen", "${intent.action} -> resume")
                AodBrightnessController.onScreenOn()
                OneHzEngine.setScreenOff(false)
            }
            PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                val active = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
                Diagnostics.log("eco", "battery saver ${if (active) "enabled" else "disabled"}")
                OneHzEngine.setPowerSaveMode(active)
            }
        }
    }
}
