package app.myfinhub.android.feature.money

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.MyFinHubApi
import app.myfinhub.android.core.network.NetworkClientFactory
import app.myfinhub.android.core.network.OkHttpMyFinHubApi
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.CardDetailsVault
import app.myfinhub.android.core.security.DataStoreEncryptedCardDetailsVault
import app.myfinhub.android.core.security.CvvVault
import app.myfinhub.android.core.security.DataStoreEncryptedCvvVault
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.core.ui.apiFailureMessage
import app.myfinhub.android.core.ui.toUserNotice
import app.myfinhub.android.core.ui.unexpectedUserNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CardSecretUiState {
    data class Hidden(val cardId: String? = null) : CardSecretUiState
    open class Loading(val cardId: String) : CardSecretUiState
    class Saving(cardId: String) : Loading(cardId)

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

sealed interface CardSecretCleanupUiState {
    data object Idle : CardSecretCleanupUiState
    data class Cleaning(val cardId: String) : CardSecretCleanupUiState
    data class Complete(val cardId: String) : CardSecretCleanupUiState
    data class Failure(
        val cardId: String,
        val serverCleanupPending: Boolean,
        val localCleanupPending: Boolean,
    ) : CardSecretCleanupUiState
}

private data class PendingCardSecretCleanup(
    val cardId: String,
    val serverCleanupPending: Boolean,
    val localCleanupPending: Boolean,
)

/**
 * Production Android card-details controller.
 *
 * PAN/expiry and CVV are encrypted at rest with separate non-exportable Android Keystore keys.
 * The canonical finance document stores only safe profile metadata (including last4). Full values
 * never enter finance JSON, logs, diagnostics or backups. Presentation may show the local values
 * directly while the authenticated app session is active.
 */
class CardSecretViewModel internal constructor(
    application: Application,
    private val api: MyFinHubApi,
    private val cvvVault: CvvVault,
    private val cardDetailsVault: CardDetailsVault,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        api = OkHttpMyFinHubApi(
            configuration = AppConfiguration.fromBuildConfig(),
            client = NetworkClientFactory.create(),
        ),
        cvvVault = DataStoreEncryptedCvvVault(
            context = application,
            cipher = AndroidKeystoreCipher(CVV_KEY_ALIAS),
        ),
        cardDetailsVault = DataStoreEncryptedCardDetailsVault(
            context = application,
            cipher = AndroidKeystoreCipher(CARD_DETAILS_KEY_ALIAS),
        ),
    )

    private val mutableState = MutableStateFlow<CardSecretUiState>(CardSecretUiState.Hidden())
    val state: StateFlow<CardSecretUiState> = mutableState.asStateFlow()

    private val mutableCleanupState = MutableStateFlow<CardSecretCleanupUiState>(CardSecretCleanupUiState.Idle)
    val cleanupState: StateFlow<CardSecretCleanupUiState> = mutableCleanupState.asStateFlow()

    private val mutableNotices = MutableSharedFlow<UserNotice>(extraBufferCapacity = 8)
    val notices: SharedFlow<UserNotice> = mutableNotices.asSharedFlow()

    private var currentSession: AuthSession? = null
    private var currentCardId: String? = null
    private var pendingCleanup: PendingCardSecretCleanup? = null
    private var cleanupGeneration = 0L

    fun attachSession(session: AuthSession) {
        val previousUserId = currentSession?.userId
        currentSession = session
        if (previousUserId != null && previousUserId != session.userId) {
            currentCardId = null
            cleanupGeneration += 1
            pendingCleanup = null
            mutableState.value = CardSecretUiState.Hidden()
            mutableCleanupState.value = CardSecretCleanupUiState.Idle
        }
    }

    fun clear() {
        currentSession = null
        currentCardId = null
        cleanupGeneration += 1
        pendingCleanup = null
        mutableState.value = CardSecretUiState.Hidden()
        mutableCleanupState.value = CardSecretCleanupUiState.Idle
    }

    fun openCard(cardId: String) {
        val normalized = cardId.trim()
        if (!CARD_ID_REGEX.matches(normalized)) {
            emitInvalidCardNotice("Άνοιγμα στοιχείων κάρτας")
            return
        }
        if (currentCardId == normalized && mutableState.value is CardSecretUiState.Revealed) return
        currentCardId = normalized
        loadLocalDetails(normalized)
    }

    fun closeCard(cardId: String) {
        if (currentCardId == cardId.trim()) currentCardId = null
    }

    /**
     * Internal reset hook for session/navigation transitions. Normal card presentation no longer
     * hides values behind a reveal interaction.
     */
    fun hideSecrets() {
        mutableState.value = CardSecretUiState.Hidden(currentCardId)
    }

    /**
     * Runs only after the canonical card deactivation has committed. At that point the card is no
     * longer active in finance state, so cleanup removes both server PAN/expiry and the device-local
     * encrypted CVV. A partial cleanup never restores the canonical card and is reported once.
     */
    fun purgeCard(cardId: String) {
        val normalized = cardId.trim()
        if (!CARD_ID_REGEX.matches(normalized)) {
            emitInvalidCardNotice("Καθαρισμός ασφαλών στοιχείων κάρτας")
            return
        }
        if (currentCardId == normalized) {
            currentCardId = null
            mutableState.value = CardSecretUiState.Hidden()
        }
        val request = PendingCardSecretCleanup(
            cardId = normalized,
            serverCleanupPending = true,
            localCleanupPending = true,
        )
        pendingCleanup = request
        cleanupGeneration += 1
        launchCleanup(request, cleanupGeneration)
    }

    /** Retries only the secret stores that failed after canonical card deactivation. */
    fun retryPurgeCard(cardId: String) {
        val normalized = cardId.trim()
        val request = pendingCleanup?.takeIf { it.cardId == normalized } ?: return
        if (mutableCleanupState.value is CardSecretCleanupUiState.Cleaning) return
        cleanupGeneration += 1
        launchCleanup(request, cleanupGeneration)
    }

    private fun launchCleanup(request: PendingCardSecretCleanup, generation: Long) {
        mutableCleanupState.value = CardSecretCleanupUiState.Cleaning(request.cardId)
        viewModelScope.launch {
            var serverFailure: ApiResult.Failure? = null
            if (request.serverCleanupPending) {
                val session = currentSession
                if (session == null) {
                    serverFailure = ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED)
                } else {
                    when (val result = safeApiCall { api.deleteCardSecrets(session, request.cardId) }) {
                        is ApiResult.Success -> Unit
                        is ApiResult.Failure -> serverFailure = result
                    }
                }
            }

            var localFailure: Exception? = null
            if (request.localCleanupPending) {
                try {
                    cardDetailsVault.delete(request.cardId)
                    cvvVault.delete(request.cardId)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    localFailure = error
                }
            }

            val nextRequest = PendingCardSecretCleanup(
                cardId = request.cardId,
                serverCleanupPending = serverFailure != null,
                localCleanupPending = localFailure != null,
            )
            if (generation != cleanupGeneration) return@launch
            if (!nextRequest.serverCleanupPending && !nextRequest.localCleanupPending) {
                pendingCleanup = null
                mutableCleanupState.value = CardSecretCleanupUiState.Complete(request.cardId)
            } else {
                pendingCleanup = nextRequest
                mutableCleanupState.value = CardSecretCleanupUiState.Failure(
                    cardId = request.cardId,
                    serverCleanupPending = nextRequest.serverCleanupPending,
                    localCleanupPending = nextRequest.localCleanupPending,
                )
                val failedParts = buildList {
                    if (serverFailure != null) add("server PAN/λήξη")
                    if (localFailure != null) add("τοπικά στοιχεία κάρτας")
                }.joinToString(" και ")
                mutableNotices.emit(
                    UserNotice(
                        message = "Η κάρτα αφαιρέθηκε, αλλά ο καθαρισμός ασφαλών στοιχείων δεν ολοκληρώθηκε.",
                        details = buildString {
                            append("Ενέργεια: Καθαρισμός ασφαλών στοιχείων κάρτας\n")
                            append("Δεν καθαρίστηκε: $failedParts")
                            serverFailure?.let { failure ->
                                append("\nΚατηγορία server: ${failure.kind}")
                                failure.statusCode?.let { append("\nHTTP: $it") }
                            }
                            if (localFailure != null) append("\nΚατηγορία συσκευής: LOCAL_CVV_CLEANUP_FAILED")
                            append("\nΔεν εμφανίζονται ευαίσθητα δεδομένα.")
                        },
                        diagnosticCode = when {
                            serverFailure != null -> "MFH-CARD-SECRET-CLEANUP-${serverFailure.kind}"
                            else -> "MFH-CARD-LOCAL-DETAILS-CLEANUP"
                        },
                    ),
                )
            }
        }
    }

    fun reveal() {
        val cardId = currentCardId ?: run {
            emitInvalidCardNotice("Προβολή στοιχείων κάρτας")
            return
        }
        loadLocalDetails(cardId)
    }

    fun saveServerSecrets(pan: CharArray, expiry: CharArray) {
        val cardId = currentCardId
        val panCopy = pan.copyOf()
        val expiryCopy = expiry.copyOf()
        pan.fill('\u0000')
        expiry.fill('\u0000')

        val normalizedPan = try {
            panCopy.concatToString().filter(Char::isDigit)
        } finally {
            panCopy.fill('\u0000')
        }
        val normalizedExpiry = try {
            normalizeServerExpiry(expiryCopy.concatToString())
        } finally {
            expiryCopy.fill('\u0000')
        }

        if (cardId == null) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Τα στοιχεία κάρτας δεν αποθηκεύτηκαν επειδή η κάρτα δεν είναι πλέον ανοιχτή.",
                    details = "Ενέργεια: Αποθήκευση στοιχείων κάρτας\nΚατηγορία: STALE_CARD_STATE",
                    diagnosticCode = "MFH-APP-STALE_CARD_STATE",
                ),
            )
            return
        }
        if (normalizedPan.length !in 12..19 || normalizedExpiry == null) {
            mutableState.value = CardSecretUiState.Failure(
                cardId = cardId,
                message = "Έλεγξε τον αριθμό κάρτας και τη λήξη (MM/YY ή MM/YYYY).",
                retryable = false,
            )
            return
        }

        mutableState.value = CardSecretUiState.Saving(cardId)
        viewModelScope.launch {
            val panChars = normalizedPan.toCharArray()
            val expiryChars = normalizedExpiry.toCharArray()
            try {
                cardDetailsVault.save(cardId, panChars, expiryChars)
                if (currentCardId == cardId) {
                    loadLocalDetails(cardId, "Ο αριθμός και η λήξη αποθηκεύτηκαν κρυπτογραφημένα σε αυτή τη συσκευή.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (currentCardId == cardId) {
                    mutableState.value = CardSecretUiState.Failure(
                        cardId = cardId,
                        message = "Τα στοιχεία κάρτας δεν μπόρεσαν να αποθηκευτούν στη συσκευή.",
                        retryable = true,
                    )
                }
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Αποθήκευση τοπικών στοιχείων κάρτας",
                        throwable = error,
                        message = "Τα στοιχεία κάρτας δεν αποθηκεύτηκαν στη συσκευή.",
                    ),
                )
            } finally {
                panChars.fill('\u0000')
                expiryChars.fill('\u0000')
            }
        }
    }

    fun saveCardDetailsForCard(cardId: String, pan: CharArray, expiry: CharArray, cvv: CharArray) {
        val normalizedCardId = cardId.trim()
        val panCopy = pan.copyOf()
        val expiryCopy = expiry.copyOf()
        val cvvCopy = cvv.copyOf()
        pan.fill('\u0000')
        expiry.fill('\u0000')
        cvv.fill('\u0000')

        val normalizedPan = try {
            panCopy.concatToString().filter(Char::isDigit)
        } finally {
            panCopy.fill('\u0000')
        }
        val normalizedExpiry = try {
            normalizeServerExpiry(expiryCopy.concatToString())
        } finally {
            expiryCopy.fill('\u0000')
        }
        val normalizedCvv = try {
            cvvCopy.concatToString().filter(Char::isDigit)
        } finally {
            cvvCopy.fill('\u0000')
        }

        if (
            !CARD_ID_REGEX.matches(normalizedCardId) ||
            normalizedPan.length !in 12..19 ||
            normalizedExpiry == null ||
            normalizedCvv.length !in 3..4
        ) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Έλεγξε τα στοιχεία της κάρτας.",
                    details = "Ενέργεια: Αποθήκευση νέας κάρτας\nΚατηγορία: INVALID_LOCAL_CARD_DETAILS",
                    diagnosticCode = "MFH-APP-INVALID-CARD-DETAILS",
                ),
            )
            return
        }

        viewModelScope.launch {
            val panChars = normalizedPan.toCharArray()
            val expiryChars = normalizedExpiry.toCharArray()
            val cvvChars = normalizedCvv.toCharArray()
            try {
                cardDetailsVault.save(normalizedCardId, panChars, expiryChars)
                cvvVault.save(normalizedCardId, cvvChars)
                if (currentCardId == normalizedCardId) {
                    loadLocalDetails(normalizedCardId, "Τα στοιχεία της κάρτας αποθηκεύτηκαν στη συσκευή.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Αποθήκευση νέας κάρτας",
                        throwable = error,
                        message = "Η κάρτα δημιουργήθηκε, αλλά τα πλήρη στοιχεία δεν αποθηκεύτηκαν στη συσκευή.",
                    ),
                )
            } finally {
                panChars.fill('\u0000')
                expiryChars.fill('\u0000')
                cvvChars.fill('\u0000')
            }
        }
    }

    fun saveCvv(cvv: CharArray) {
        val session = currentSession
        val cardId = currentCardId
        val current = mutableState.value as? CardSecretUiState.Revealed
        if (current?.cvvSaving == true) {
            cvv.fill('\u0000')
            return
        }
        if (session == null || cardId == null || current?.cardId != cardId) {
            cvv.fill('\u0000')
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Το CVV δεν αποθηκεύτηκε επειδή η κάρτα δεν είναι πλέον ανοιχτή.",
                    details = "Ενέργεια: Αποθήκευση τοπικού CVV\nΚατηγορία: STALE_CARD_STATE",
                    diagnosticCode = "MFH-APP-STALE_CARD_STATE",
                ),
            )
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IllegalArgumentException) {
                val latest = mutableState.value as? CardSecretUiState.Revealed
                if (latest != null) {
                    mutableState.value = latest.copy(
                        cvvSaving = false,
                        message = "Το CVV πρέπει να έχει 3 ή 4 αριθμητικά ψηφία.",
                    )
                }
            } catch (error: Exception) {
                val latest = mutableState.value as? CardSecretUiState.Revealed
                if (latest != null) {
                    mutableState.value = latest.copy(
                        cvvSaving = false,
                        message = "Το τοπικό CVV vault δεν είναι διαθέσιμο.",
                    )
                }
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Αποθήκευση τοπικού CVV",
                        throwable = error,
                        message = "Το CVV δεν αποθηκεύτηκε στη συσκευή.",
                    ),
                )
            } finally {
                copy.fill('\u0000')
            }
        }
    }

    fun deleteCvv() {
        val session = currentSession ?: return
        val cardId = currentCardId ?: return
        val current = mutableState.value as? CardSecretUiState.Revealed ?: return
        if (current.cvvSaving) return
        mutableState.value = current.copy(cvvSaving = true, message = null)

        viewModelScope.launch {
            try {
                cvvVault.delete(cardId)
                if (!stillCurrent(session, cardId)) return@launch
                val latest = mutableState.value as? CardSecretUiState.Revealed ?: return@launch
                mutableState.value = latest.copy(
                    cvv = null,
                    cvvSaving = false,
                    message = "Το CVV αφαιρέθηκε από αυτή τη συσκευή.",
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!stillCurrent(session, cardId)) return@launch
                val latest = mutableState.value as? CardSecretUiState.Revealed ?: return@launch
                mutableState.value = latest.copy(
                    cvvSaving = false,
                    message = "Η διαγραφή από το τοπικό CVV vault δεν ολοκληρώθηκε.",
                )
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Διαγραφή τοπικού CVV",
                        throwable = error,
                        message = "Το CVV δεν μπόρεσε να διαγραφεί από τη συσκευή.",
                    ),
                )
            }
        }
    }

    private fun loadLocalDetails(cardId: String, message: String? = null) {
        mutableState.value = CardSecretUiState.Loading(cardId)
        viewModelScope.launch {
            var localMessage = message
            val details = try {
                cardDetailsVault.load(cardId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                localMessage = listOfNotNull(localMessage, "Ο αριθμός/λήξη δεν μπόρεσαν να διαβαστούν από τη συσκευή.").joinToString(" ")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Ανάγνωση τοπικών στοιχείων κάρτας",
                        throwable = error,
                        message = "Τα στοιχεία κάρτας δεν είναι διαθέσιμα στη συσκευή.",
                    ),
                )
                null
            }
            val cvvChars = try {
                cvvVault.load(cardId)
            } catch (cancelled: CancellationException) {
                details?.clear()
                throw cancelled
            } catch (error: Exception) {
                localMessage = listOfNotNull(localMessage, "Το CVV δεν μπόρεσε να διαβαστεί από τη συσκευή.").joinToString(" ")
                mutableNotices.emit(
                    unexpectedUserNotice(
                        operation = "Ανάγνωση τοπικού CVV",
                        throwable = error,
                        message = "Ο αριθμός/λήξη φορτώθηκαν, αλλά το CVV δεν είναι διαθέσιμο.",
                    ),
                )
                null
            }

            val pan = try { details?.pan?.concatToString() } finally { details?.pan?.fill('\u0000') }
            val expiry = try { details?.expiry?.concatToString() } finally { details?.expiry?.fill('\u0000') }
            val cvv = try { cvvChars?.concatToString() } finally { cvvChars?.fill('\u0000') }
            if (currentCardId != cardId) return@launch
            mutableState.value = CardSecretUiState.Revealed(
                cardId = cardId,
                pan = pan,
                expiry = expiry,
                cvv = cvv,
                message = localMessage,
            )
        }
    }

    private suspend fun <T> safeApiCall    private suspend fun <T> safeApiCall(block: suspend () -> ApiResult<T>): ApiResult<T> = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        ApiResult.Failure(ApiFailureKind.SERVER, retryable = true)
    }

    private fun emitInvalidCardNotice(operation: String) {
        mutableNotices.tryEmit(
            UserNotice(
                message = "Η κάρτα δεν είναι διαθέσιμη.",
                details = "Ενέργεια: $operation\nΚατηγορία: INVALID_CARD_ID",
                diagnosticCode = "MFH-APP-INVALID_CARD_ID",
            ),
        )
    }

    private fun stillCurrent(session: AuthSession, cardId: String): Boolean =
        currentSession?.userId == session.userId && currentCardId == cardId

    private fun normalizeServerExpiry(raw: String): String? {
        val compact = raw.trim().replace(" ", "")
        val match = Regex("^(0[1-9]|1[0-2])/(\\d{2}|\\d{4})$").matchEntire(compact) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private companion object {
        const val CVV_KEY_ALIAS = "myfinhub_cvv_v1"
        const val CARD_DETAILS_KEY_ALIAS = "myfinhub_card_details_v1"
        val CARD_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
    }
}
