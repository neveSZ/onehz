package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.diagnostic.Diagnostics

class AodViewModel : ViewModel() {
    val aodInfo = Diagnostics.aodBrightness
}
