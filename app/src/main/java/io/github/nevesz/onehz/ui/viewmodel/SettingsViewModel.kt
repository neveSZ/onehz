package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.core.update.UpdateCenter

class SettingsViewModel : ViewModel() {
    val updateState = UpdateCenter.state
}
