package app.myfinhub.android.app

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BootstrapViewModel : ViewModel() {
    private val _state = MutableStateFlow(BootstrapUiState())
    val state = _state.asStateFlow()

    fun onAction(action: BootstrapAction) {
        _state.update { current -> reduceBootstrapState(current, action) }
    }
}
