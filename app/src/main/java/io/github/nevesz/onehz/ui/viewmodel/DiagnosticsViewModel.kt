package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.diagnostic.Diagnostics

/** UI state for the diagnostics screen. */
class DiagnosticsViewModel : ViewModel() {
    val entries = Diagnostics.entries
    val paused = Diagnostics.paused
    val shizukuState = Diagnostics.shizukuState
    val displayState = Diagnostics.displayState
    val displayEligible = Diagnostics.displayEligible
    val controlMethod = Diagnostics.controlMethod
    val activeTierKey = Diagnostics.activeTierKey
    val observedHz = Diagnostics.observedHz
    val activeStep = Diagnostics.activeStep
    val activeStepCount = Diagnostics.activeStepCount
    val lastWrite = Diagnostics.lastWrite
    val screenBrightness = Diagnostics.screenBrightness
    val batterySaver = Diagnostics.batterySaver
    val displayRoute = Diagnostics.displayRoute
    val dozeDetail = Diagnostics.dozeDetail
    val inputState = Diagnostics.inputState
    val inputDevice = Diagnostics.inputDevice
    val inputCandidates = Diagnostics.inputCandidates
    val lastTouchAt = Diagnostics.lastTouchAt
    val aodBrightness = Diagnostics.aodBrightness
    val videoSource = Diagnostics.videoSource
    val videoDetail = Diagnostics.videoDetail
    val activeLayer = Diagnostics.activeLayer
    val activeProfileId = Diagnostics.activeProfileId
    val activeAppPackage = Diagnostics.activeAppPackage
    val idleReason = Diagnostics.idleReason
    val appliedHz = Diagnostics.appliedHz
    val dozePhase = Diagnostics.dozePhase
    val dozeActiveSince = Diagnostics.dozeActiveSince
}
