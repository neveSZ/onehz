package io.github.nevesz.onehz

import android.app.Application
import io.github.nevesz.onehz.core.LtpoGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.engine.SessionArbiter
import io.github.nevesz.onehz.shizuku.ShizukuGateway

class OneHzApp : Application() {
    override fun onCreate() {
        super.onCreate()

        val gate = LtpoGate.evaluate(this)
        Diagnostics.ltpoState.value = gate.summary
        Diagnostics.ltpoEligible.value = gate.eligible
        Diagnostics.setPaused(OneHzPreferences.logsPaused(this))
        Diagnostics.log("LTPO", gate.summary)
        UpdateCenter.init(this)

        ShizukuGateway.init(
            context = this,
            onReady = {
                val fresh = LtpoGate.evaluate(this)
                Diagnostics.ltpoState.value = fresh.summary
                Diagnostics.ltpoEligible.value = fresh.eligible
                if (fresh.eligible) {
                    SessionArbiter.invalidate()
                    SessionArbiter.restorePending(this)
                    OneHzEngine.start(this)
                    AodBrightnessController.start(this)
                    if (OneHzPreferences.isEnabled(this)) {
                        OneHzKeepAliveService.start(this)
                    }
                    OneHzEngine.refreshSystemState()
                }
            },
            onUnavailable = {
                AodBrightnessController.onServiceUnavailable()
                OneHzEngine.stop()
            }
        )

        if (gate.eligible && OneHzPreferences.isEnabled(this)) {
            ShizukuGateway.refresh()
            OneHzKeepAliveService.start(this)
        }
    }

    override fun onTerminate() {
        AodBrightnessController.stop()
        OneHzEngine.stop()
        ShizukuGateway.destroy()
        super.onTerminate()
    }
}
