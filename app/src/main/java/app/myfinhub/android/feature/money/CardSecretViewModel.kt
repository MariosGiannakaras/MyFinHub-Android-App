package app.myfinhub.android.feature.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.MyFinHubApi
import app.myfinhub.android.core.network.OkHttpMyFinHubApi
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.CvvVault
import app.myfinhub.android.core.security.DataStoreEncryptedCvvVault
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

sealed interface CardSecretUiState {
    data class Hidden(val cardId: String? = null) : CardSecretUiState
    data class Loading(val cardId: String) : CardSecretUiState

    data class Revealed(
        val cardId: String,
        val pan: String?,
        val expiry: String?,
        val cvv: String?,
        val cvvSaving: Boolean = false,
        val message: String? = null,
    ) : CardSecretUiState {
        override fun toString(): String =
            "Revealed(cardId=$cardId, pan=<redacted>, expiry=<redacted>, cvv=<redacted>, cvvSaving=$cvvSaving, message=$message)"
    }

    data class Failure(
        val cardId: String,
        val message: String,
        val retryable: Boolean,
    ) : CardSecretUiState

    data object AuthRejected : CardSecretUiState
}

/**
 * Production card-secret controller. PAN/expiry come only from the owner+AAL2 server vault; CVV is
 * read/written only through the device-local encrypted vault. Sensitive values are held only while
 * the card detail explicitly reveals them and are dropped when that surface closes or auth changes.
 */
class CardSecretViewModel internal constructor(
    application: Application,
    private val api: MyFinHubApi,
    private val cvvVault: CvvVault,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        api = OkHttpMyFinHubApi(
            configuration = AppConfiguration.fromBuildConfig(),
            client = OkHttpClient.Builder().build(),
        ),
        cvvVault = DataStoreEncryptedCvvVault(
            context = application,
            cipher = AndroidKeystoreCipher(CVV_KEY_ALIAS),
        ),
    )

    private val mutableState = MutableStateFlow<CardSecretUiState>(CardSecretUiState.Hidden())
    val state: StateFlow<CardSecretUiState> = mutableState.asStateFlow()

    private var currentSession: AuthSession? = null
    private var currentCardId: String? = null

    fun attachSession(session: AuthSession) {
        val previousUserId = currentSession?.userId
        currentSession = session
        if (previousUserId != null && previousUserId != session.userId) {
            currentCardId = null
            mutableState.value = CardSecretUiState.Hidden()
        }
    }

    fun clear() {
        currentSession = null
        currentCardId = null
        mutableState.value = CardSecretUiState.Hidden()
    }

    fun openCard(cardId: String) {
        if (!CARD_ID_REGEX.matches(cardId)) return
        if (currentCardId == cardId) return
        currentCardId = cardId
        mutableState.value = CardSecretUiState.Hidden(cardId)
    }

    fun closeCard(cardId: String) {
        if (currentCardId != cardId) return
        currentCardId = null
        mutableState.value = CardSecretUiState.Hidden()
    }

    fun hideSecrets() {
        mutableState.value = CardSecretUiState.Hidden(currentCardId)
    }

    /**
     * Removes device-local secret material when a canonical card is deleted/deactivated.
     * The canonical mutation is owned by FinanceProductViewModel; this method only clears the
     * Android-only CVV boundary and any currently revealed in-memory state for the same stable ID.
     */
    fun purgeCard(cardId: String) {
        if (!CARD_ID_REGEX.matches(cardId)) return
        if (currentCardId == cardId) {
            currentCardId = null
            mutableState.value = CardSecretUiState.Hidden()
        }
        viewModelScope.launch {
            runCatching { cvvVault.delete(cardId) }
        }
    }

    fun reveal() {
        val session = currentSession ?: return
        val cardId = currentCardId ?: return
        mutableState.value = CardSecretUiState.Loading(cardId)

        viewModelScope.launch {
            val serverResult = api.loadCardSecrets(session, cardId)
            if (!stillCurrent(session, cardId)) return@launch

            when (serverResult) {
                is ApiResult.Success -> revealWithLocalCvv(
                    session = session,
                    cardId = cardId,
                    pan = serverResult.value.pan,
                    expiry = serverResult.value.expiry,
                )

                is ApiResult.Failure -> when {
                    serverResult.kind == ApiFailureKind.AUTH_REQUIRED ||
                        serverResult.kind == ApiFailureKind.MFA_REQUIRED -> {
                        mutableState.value = CardSecretUiState.AuthRejected
                    }

                    serverResult.kind == ApiFailureKind.INVALID_DATA -> revealWithLocalCvv(
                        session = session,
                        cardId = cardId,
                        pan = null,
                        expiry = null,
                        message = "Δεν έχουν αποθηκευτεί PAN/λήξη στο server vault για αυτή την κάρτα.",
                    )

                    else -> mutableState.value = CardSecretUiState.Failure(
                        cardId = cardId,
                        message = failureMessage(serverResult.kind),
                        retryable = serverResult.retryable,
                    )
                }
            }
        }
    }

    fun saveCvv(cvv: CharArray) {
        val session = currentSession
        val cardId = currentCardId
        val current = mutableState.value as? CardSecretUiState.Revealed
        if (session == null || cardId == null || current?.cardId != cardId) {
            cvv.fill('\u0000')
            return
        }

        val copy = cvv.copyOf()
        cvv.fill('\u0000')
        if (copy.size !in 3..4 || copy.any { it !in '0'..'9' }) {
            copy.fill('\u0000')
            mutableState.value = current.copy(message = "Το CVV πρέπει να έχει 3 ή 4 αριθμητικά ψηφία.")
            return
        }

        mutableState.value = current.copy(cvvSaving = true, message = null)
        viewModelScope.launch {
            try {
                cvvVault.save(cardId, copy)
                if (!stillCurrent(session, cardId)) return@launch
                val nextCvv = copy.concatToString()
                val latest = mutableState.value as? CardSecretUiState.Revealed ?: return@launch
                mutableState.value = latest.copy(
                    cvv = nextCvv,
                    cvvSaving = false,
                    message = "Το CVV αποθηκεύτηκε μόνο σε αυτή τη συσκευή.",
                )
            } catch (_: IllegalArgumentException) {
                val latest = mutableState.value as? CardSecretUiState.Revealed
                if (latest != null) {
                    mutableState.value = latest.copy(
                        cvvSaving = false,
                        message = "Το CVV πρέπει να έχει 3 ή 4 αριθμητικά ψηφία.",
                    )
                }
            } catch (_: Exception) {
                val latest = mutableState.value as? CardSecretUiState.Revealed
                if (latest != null) {
                    mutableState.value = latest.copy(
                        cvvSaving = false,
                        message = "Το τοπικό CVV vault δεν είναι διαθέσιμο.",
                    )
                }
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    fun deleteCvv() {
        val session = currentSession ?: return
        val cardId = currentCardId ?: return
        val current = mutableState.value as? CardSecretUiState.Revealed ?: return
        mutableState.value = current.copy(cvvSaving = true, message = null)

        viewModelScope.launch {
            val deletion = runCatching { cvvVault.delete(cardId) }
            if (!stillCurrent(session, cardId)) return@launch
            val latest = mutableState.value as? CardSecretUiState.Revealed ?: return@launch
            mutableState.value = if (deletion.isSuccess) {
                latest.copy(
                    cvv = null,
                    cvvSaving = false,
                    message = "Το CVV αφαιρέθηκε από αυτή τη συσκευή.",
                )
            } else {
                latest.copy(
                    cvvSaving = false,
                    message = "Η διαγραφή από το τοπικό CVV vault δεν ολοκληρώθηκε.",
                )
            }
        }
    }

    private suspend fun revealWithLocalCvv(
        session: AuthSession,
        cardId: String,
        pan: String?,
        expiry: String?,
        message: String? = null,
    ) {
        val localChars = runCatching { cvvVault.load(cardId) }.getOrNull()
        val localCvv = try {
            localChars?.concatToString()
        } finally {
            localChars?.fill('\u0000')
        }
        if (!stillCurrent(session, cardId)) return
        mutableState.value = CardSecretUiState.Revealed(
            cardId = cardId,
            pan = pan,
            expiry = expiry,
            cvv = localCvv,
            message = message,
        )
    }

    private fun stillCurrent(session: AuthSession, cardId: String): Boolean =
        currentSession?.userId == session.userId && currentCardId == cardId

    private fun failureMessage(kind: ApiFailureKind): String = when (kind) {
        ApiFailureKind.BUILD_NOT_CONFIGURED -> "Η έκδοση της εφαρμογής δεν έχει έγκυρη public client configuration."
        ApiFailureKind.AUTH_REQUIRED -> "Η συνεδρία δεν είναι πλέον έγκυρη."
        ApiFailureKind.MFA_REQUIRED -> "Απαιτείται ξανά AAL2 επαλήθευση για τα ασφαλή στοιχεία κάρτας."
        ApiFailureKind.INVALID_DATA -> "Τα ασφαλή στοιχεία της κάρτας δεν είναι διαθέσιμα."
        ApiFailureKind.RATE_LIMITED -> "Έγιναν πολλές προσπάθειες. Δοκίμασε ξανά αργότερα."
        ApiFailureKind.NETWORK -> "Δεν υπάρχει σύνδεση με το ασφαλές card vault."
        ApiFailureKind.SERVER -> "Το ασφαλές card vault δεν είναι προσωρινά διαθέσιμο."
        ApiFailureKind.MALFORMED_RESPONSE -> "Το card vault επέστρεψε μη αναμενόμενη απάντηση."
        ApiFailureKind.REVISION_CONFLICT,
        ApiFailureKind.PRECONDITION_REQUIRED,
        ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE -> "Η λειτουργία ασφαλών στοιχείων δεν είναι διαθέσιμη."
    }

    private companion object {
        const val CVV_KEY_ALIAS = "myfinhub_cvv_v1"
        val CARD_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
    }
}