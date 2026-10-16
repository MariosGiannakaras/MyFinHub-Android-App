package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceEnvelope
import app.myfinhub.android.core.data.CanonicalWriteReceipt

interface MyFinHubApi {
    suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope>

    /** Owner-scoped read-only account metadata: IBAN is never part of FinanceData or on-disk cache. */
    suspend fun loadAccountMetadata(session: AuthSession): ApiResult<Map<String, AccountMetadataRecord>> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** Owner+AAL2 Web IBAN metadata write, conditional on the exact server record revision.
     * Separate from finance JSON, never queued offline or logged as an ordinary mutation.
     */
    suspend fun updateAccountIban(
        session: AuthSession,
        accountId: String,
        iban: String?,
        expectedRevision: Long,
    ): ApiResult<AccountMetadataRecord> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** Only GET /api/history. No snapshot download, POST undo/redo or offline history cache. */
    suspend fun loadServerFinanceHistory(
        session: AuthSession,
    ): ApiResult<ServerFinanceHistorySummary> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** Authenticated owner+AAL2 read of the Web dynamic provider catalog, no asset fetches. */
    suspend fun loadFinancialProviders(session: AuthSession): ApiResult<List<FinancialProviderRecord>> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** Owner+AAL2 read-only Web GET /api/auth/devices, no secret caching. */
    suspend fun loadDeviceSessions(session: AuthSession): ApiResult<List<DeviceSessionRecord>> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** One explicitly confirmed remote device; neither current-session nor revoke-all. */
    suspend fun revokeOtherDeviceSession(
        session: AuthSession, targetSessionId: String,
    ): ApiResult<List<DeviceSessionRecord>> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt>

    suspend fun loadCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecrets> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    suspend fun saveCardSecrets(
        session: AuthSession,
        cardId: String,
        update: CardSecretUpdate,
    ): ApiResult<CardSecretWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    suspend fun deleteCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    /** Vault deletion conditioned on a server-confirmed finance profile deletion. */
    suspend fun deleteCommittedCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)
}

/** Sensitive PAN/expiry/CVV response. Intentionally redacts values from toString(). */
class CardSecrets(
    val pan: String?,
    val expiry: String?,
    val cvv: String?,
) {
    override fun toString(): String = "CardSecrets(pan=<redacted>, expiry=<redacted>, cvv=<redacted>)"
}

/** Shared encrypted server-vault update. Sensitive values are always redacted from diagnostics. */
class CardSecretUpdate(
    val pan: String? = null,
    val expiry: String? = null,
    val cvv: String? = null,
) {
    override fun toString(): String = "CardSecretUpdate(pan=<redacted>, expiry=<redacted>, cvv=<redacted>)"
}

data class CardSecretWriteReceipt(
    val saved: Boolean,
    val last4: String?,
)

data class CardSecretDeleteReceipt(
    val deleted: Boolean,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>

    data class Failure(
        val kind: ApiFailureKind,
        val retryable: Boolean = false,
        val statusCode: Int? = null,
    ) : ApiResult<Nothing>
}

enum class ApiFailureKind {
    BUILD_NOT_CONFIGURED,
    AUTH_REQUIRED,
    MFA_REQUIRED,
    REVISION_CONFLICT,
    PRECONDITION_REQUIRED,
    INVALID_DATA,
    RATE_LIMITED,
    NETWORK,
    SERVER,
    MALFORMED_RESPONSE,
    UNSUPPORTED_IN_SYNTHETIC_MODE,
}

class SyntheticMyFinHubApi : MyFinHubApi {
    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)
}
