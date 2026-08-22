package app.myfinhub.android.app

data class BootstrapUiState(
    val title: String = "MyFinHub",
    val subtitle: String = "Native Android client",
    val phase: String = "Phase 1 · Bootstrap",
    val architectureNote: String = "Kotlin + Jetpack Compose · no WebView",
    val acknowledged: Boolean = false,
)

sealed interface BootstrapAction {
    data object AcknowledgeNativeBaseline : BootstrapAction
}

fun reduceBootstrapState(state: BootstrapUiState, action: BootstrapAction): BootstrapUiState = when (action) {
    BootstrapAction.AcknowledgeNativeBaseline -> state.copy(acknowledged = true)
}
