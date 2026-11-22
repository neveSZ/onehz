package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.core.update.UpdateCenter
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway

/** UI state for the home screen. */
class HomeViewModel : ViewModel() {
    val shizuku = ShizukuGateway.state
    val update = UpdateCenter.state
    val activeTierKey = Diagnostics.activeTierKey
    val activeStep = Diagnostics.activeStep
    val activeStepCount = Diagnostics.activeStepCount
    val appliedHz = Diagnostics.appliedHz
    val idleReason = Diagnostics.idleReason
    val videoSource = Diagnostics.videoSource
    val activeLayer = Diagnostics.activeLayer
    val activeProfileId = Diagnostics.activeProfileId
    val activeAppPackage = Diagnostics.activeAppPackage
}
