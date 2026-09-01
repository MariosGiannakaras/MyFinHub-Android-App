package app.myfinhub.android.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Synthetic state holder used only by the standalone test/demo app shell. */
class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(syntheticHomeUiState())
    val state = _state.asStateFlow()

    fun onAction(action: HomeAction) {
        _state.update { current -> reduceHomeState(current, action) }
    }
}
