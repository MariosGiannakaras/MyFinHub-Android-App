package app.myfinhub.android.core.update

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class UpdateController(
    val state: UpdateUiState = UpdateUiState.Idle,
    val check: () -> Unit = {},
    val download: () -> Unit = {},
    val install: () -> Unit = {},
    val openInstallPermission: () -> Unit = {},
)

val LocalUpdateController = staticCompositionLocalOf { UpdateController() }
