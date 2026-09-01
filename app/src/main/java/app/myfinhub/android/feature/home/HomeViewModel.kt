package app.myfinhub.android.feature.home

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Synthetic state holder for the standalone UI test/demo shell; production state comes from the canonical product controller. */
class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(syntheticHomeUiState())
    val state = _state.asStateFlow()

    fun onAction(action: HomeAction) {
        _state.update { current -> reduceHomeState(current, action) }
    }
}
