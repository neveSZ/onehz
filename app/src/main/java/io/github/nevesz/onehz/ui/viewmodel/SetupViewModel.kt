package io.github.nevesz.onehz.ui.viewmodel

import androidx.lifecycle.ViewModel
import io.github.nevesz.onehz.shizuku.ShizukuGateway

class SetupViewModel : ViewModel() {
    val shizuku = ShizukuGateway.state
}
