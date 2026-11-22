package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.diagnostic.Diagnostics
import io.github.nevesz.onehz.shizuku.ShizukuGateway

class SleepViewModel : ViewModel() {
    val phase = Diagnostics.dozePhase
    val activeSince = Diagnostics.dozeActiveSince
    val shizuku = ShizukuGateway.state
}
