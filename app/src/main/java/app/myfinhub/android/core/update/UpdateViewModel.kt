package app.myfinhub.android.core.update

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.NetworkClientFactory
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val client = PrivateUpdateClient(AppConfiguration.fromBuildConfig(), NetworkClientFactory.create())
    private val verifier = ApkVerifier(application)
    private val installer = PackageInstallerUpdateInstaller(application)
    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var session: AuthSession? = null
    private var activeJob: Job? = null
    private var autoCheckedUserId: String? = null

    fun attachSession(value: AuthSession) {
        session = value
        if (autoCheckedUserId != value.userId) {
            autoCheckedUserId = value.userId
            checkForUpdates(manual = false)
        }
    }

    fun clearSession() {
        session = null
        activeJob?.cancel()
        activeJob = null
        _state.value = UpdateUiState.Idle
    }

    fun checkForUpdates(manual: Boolean = true) {
        val currentSession = session ?: run {
            if (manual) _state.value = UpdateUiState.Failure(UpdateFailureKind.AUTH_REQUIRED, retryable = false)
            return
        }
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _state.value = UpdateUiState.Checking
            _state.value = when (val result = client.check(currentSession)) {
                UpdateCheckResult.UpToDate -> UpdateUiState.UpToDate
                is UpdateCheckResult.Available -> UpdateUiState.Available(result.release)
                is UpdateCheckResult.Failure -> UpdateUiState.Failure(result.kind, result.retryable)
            }
        }
    }

    fun downloadAvailableUpdate() {
        val currentSession = session ?: return
        val release = when (val current = _state.value) {
            is UpdateUiState.Available -> current.release
            is UpdateUiState.Failure -> current.release
            else -> null
        } ?: return
        activeJob?.cancel()
        activeJob = viewModelScope.launch {
            _state.value = UpdateUiState.Downloading(release, 0f)
            val directory = File(getApplication<Application>().cacheDir, "private-updates")
            when (val result = client.download(currentSession, release, directory) { progress ->
                _state.value = UpdateUiState.Downloading(release, progress)
            }) {
                is UpdateDownloadResult.Failure ->
                    _state.value = UpdateUiState.Failure(result.kind, result.retryable, release)
                is UpdateDownloadResult.Success -> {
                    val verificationFailure = verifier.verify(result.file, release)
                    _state.value = if (verificationFailure == null) {
                        UpdateUiState.ReadyToInstall(release, result.file)
                    } else {
                        result.file.delete()
                        UpdateUiState.Failure(verificationFailure, retryable = false, release = release)
                    }
                }
            }
        }
    }

    fun installReadyUpdate() {
        val pair = when (val current = _state.value) {
            is UpdateUiState.ReadyToInstall -> current.release to current.file
            is UpdateUiState.PermissionRequired -> current.release to current.file
            else -> return
        }
        val release = pair.first
        val file = pair.second
        if (!installer.canRequestInstalls()) {
            _state.value = UpdateUiState.PermissionRequired(release, file)
            return
        }
        val failure = installer.install(file, release)
        _state.value = if (failure == null) {
            UpdateUiState.Installing(release)
        } else {
            UpdateUiState.Failure(failure, retryable = true, release = release)
        }
    }

    fun installPermissionIntent(): Intent? =
        if (_state.value is UpdateUiState.PermissionRequired) installer.permissionIntent() else null

    fun refreshInstallStatus() {
        val (status, versionCode) = UpdateInstallStatusStore.read(getApplication())
        if (status == InstallStatus.NONE) return
        val currentRelease = when (val current = _state.value) {
            is UpdateUiState.Installing -> current.release
            is UpdateUiState.PermissionRequired -> current.release
            is UpdateUiState.ReadyToInstall -> current.release
            else -> null
        }
        if (currentRelease != null && versionCode == currentRelease.versionCode && status == InstallStatus.FAILURE) {
            _state.value = UpdateUiState.Failure(UpdateFailureKind.INSTALL_FAILED, retryable = true, currentRelease)
        }
        if (status == InstallStatus.FAILURE || status == InstallStatus.SUCCESS) {
            UpdateInstallStatusStore.clear(getApplication())
        }
    }

    override fun onCleared() {
        activeJob?.cancel()
        session = null
        super.onCleared()
    }
}
