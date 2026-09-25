package app.myfinhub.android.core.update

import java.io.File
import kotlinx.serialization.Serializable

@Serializable
data class UpdateRelease(
    val versionCode: Long,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val mandatory: Boolean,
    val notes: String,
    val publishedAt: String,
)

@Serializable
internal data class UpdateEnvelope(
    val available: Boolean,
    val release: UpdateRelease? = null,
)

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val release: UpdateRelease) : UpdateCheckResult
    data class Failure(val kind: UpdateFailureKind, val retryable: Boolean = false) : UpdateCheckResult
}

sealed interface UpdateDownloadResult {
    data class Success(val file: File) : UpdateDownloadResult
    data class Failure(val kind: UpdateFailureKind, val retryable: Boolean = false) : UpdateDownloadResult
}

enum class UpdateFailureKind {
    BUILD_NOT_CONFIGURED,
    AUTH_REQUIRED,
    MFA_REQUIRED,
    NETWORK,
    SERVER,
    MALFORMED_METADATA,
    INSECURE_DOWNLOAD,
    DOWNLOAD_SIZE_MISMATCH,
    DOWNLOAD_DIGEST_MISMATCH,
    WRONG_PACKAGE,
    WRONG_VERSION,
    WRONG_SIGNER,
    PACKAGE_UNREADABLE,
    INSTALL_PERMISSION_REQUIRED,
    INSTALL_BLOCKED,
    INSTALL_FAILED,
}

sealed interface UpdateUiState {
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    data class Available(val release: UpdateRelease) : UpdateUiState
    data class Downloading(val release: UpdateRelease, val progress: Float) : UpdateUiState
    data class ReadyToInstall(val release: UpdateRelease, val file: File) : UpdateUiState
    data class PermissionRequired(val release: UpdateRelease, val file: File) : UpdateUiState
    data class Installing(val release: UpdateRelease) : UpdateUiState
    data class Failure(
        val kind: UpdateFailureKind,
        val retryable: Boolean,
        val release: UpdateRelease? = null,
    ) : UpdateUiState
}
