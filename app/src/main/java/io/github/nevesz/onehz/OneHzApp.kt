package io.github.nevesz.onehz

import android.app.Application
import io.github.nevesz.onehz.core.DisplayGate
import io.github.nevesz.onehz.core.OneHzPreferences
import io.github.nevesz.onehz.core.prefs.PreferencesStore
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.engine.AodBrightnessController
import io.github.nevesz.onehz.engine.DozeController
import io.github.nevesz.onehz.engine.OneHzEngine
import io.github.nevesz.onehz.engine.OneHzKeepAliveService
import io.github.nevesz.onehz.engine.SessionArbiter
import io.github.nevesz.onehz.shizuku.ShizukuGateway

class OneHzApp : Application() {
    override fun onCreate() {
        super.onCreate()

        PreferencesStore.initialize(this)
        val gate = DisplayGate.evaluate(this)
        Diagnostics.displayState.value = gate.summary
        Diagnostics.displayEligible.value = gate.eligible
        Diagnostics.setPaused(OneHzPreferences.logsPaused(this))
        Diagnostics.log("display", gate.summary)
        UpdateCenter.init(this)

        ShizukuGateway.init(
            context = this,
            onReady = {
                val fresh = DisplayGate.evaluate(this)
                Diagnostics.displayState.value = fresh.summary
                Diagnostics.displayEligible.value = fresh.eligible
                if (fresh.eligible) {
                    SessionArbiter.invalidate()
                    SessionArbiter.restorePending(this)
                    OneHzEngine.start(this)
                    AodBrightnessController.start(this)
                    DozeController.start(this)
                    DozeController.onServiceAvailable()
                    if (OneHzPreferences.isEnabled(this)) {
                        OneHzKeepAliveService.start(this)
                    }
                    OneHzEngine.refreshSystemState()
                }
            },
            onUnavailable = {
                AodBrightnessController.onServiceUnavailable()
                DozeController.onServiceUnavailable()
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
