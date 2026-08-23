package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceEnvelope
import app.myfinhub.android.core.data.CanonicalWriteReceipt
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class OkHttpMyFinHubApi(
    private val configuration: AppConfiguration,
    private val client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : MyFinHubApi {
    override suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary> {
        if (!configuration.isConfigured) return ApiResult.Failure(ApiFailureKind.BUILD_NOT_CONFIGURED)
        return ApiResult.Success(BootstrapSummary(DataSource.CANONICAL_API, revision = null))
    }

    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> {
        val gate = requestGate(session)
        if (gate != null) return gate

        val request = authenticatedRequest(session, "/api/data")
            .get()
            .build()

        return execute(request) { body -> parseEnvelope(body) }
    }

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> {
        val gate = requestGate(session)
        if (gate != null) return gate
        if (!expectedRevision.matches(REVISION_REGEX)) {
            return ApiResult.Failure(ApiFailureKind.PRECONDITION_REQUIRED)
        }

        val body = buildJsonObject {
            put("state", document.state)
            put("updatedAt", JsonPrimitive(document.updatedAt))
        }.toString()
        val request = authenticatedRequest(session, "/api/data")
            .header("If-Match", expectedRevision)
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return execute(request) { responseBody ->
            runCatching {
                val receipt = json.parseToJsonElement(responseBody).jsonObject
                ApiResult.Success(
                    CanonicalWriteReceipt(
                        revision = receipt["revision"]?.jsonPrimitive?.contentOrNull ?: error("Missing revision"),
                        lastSavedAt = receipt["lastSavedAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    ),
                )
            }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }
        }
    }

    override suspend fun createBackup(session: AuthSession): ApiResult<BackupReceipt> {
        val gate = requestGate(session)
        if (gate != null) return gate

        val request = authenticatedRequest(session, "/api/backup")
            .post(EMPTY_JSON_BODY)
            .build()
        return execute(request) { body ->
            runCatching {
                val payload = json.parseToJsonElement(body).jsonObject
                val path = payload["path"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
                    ?: error("Missing backup path")
                ApiResult.Success(BackupReceipt(path))
            }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }
        }
    }

    override suspend fun importFinanceData(
        session: AuthSession,
        document: CanonicalFinanceDocument,
    ): ApiResult<CanonicalFinanceEnvelope> {
        val gate = requestGate(session)
        if (gate != null) return gate

        val request = authenticatedRequest(session, "/api/import")
            .header("X-RheomIQ-Confirm-Import", "replace")
            .post(document.raw.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request) { body -> parseEnvelope(body) }
    }

    override suspend fun loadCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecrets> {
        val gate = requestGate(session)
        if (gate != null) return gate
        val normalizedCardId = cardId.trim()
        if (!CARD_ID_REGEX.matches(normalizedCardId)) return ApiResult.Failure(ApiFailureKind.INVALID_DATA)

        val body = buildJsonObject { put("cardId", JsonPrimitive(normalizedCardId)) }.toString()
        val request = authenticatedRequest(session, "/api/card-secrets")
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request, notFoundKind = ApiFailureKind.INVALID_DATA) { responseBody ->
            runCatching {
                val payload = json.parseToJsonElement(responseBody).jsonObject
                val pan = payload.nullableString("pan")
                val expiry = payload.nullableString("expiry")
                if (pan == null && expiry == null) error("Empty card secret")
                ApiResult.Success(CardSecrets(pan = pan, expiry = expiry))
            }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }
        }
    }

    override suspend fun saveCardSecrets(
        session: AuthSession,
        cardId: String,
        update: CardSecretUpdate,
    ): ApiResult<CardSecretWriteReceipt> {
        val gate = requestGate(session)
        if (gate != null) return gate
        val normalizedCardId = cardId.trim()
        if (!CARD_ID_REGEX.matches(normalizedCardId)) return ApiResult.Failure(ApiFailureKind.INVALID_DATA)
        if (update.pan.isNullOrBlank() && update.expiry.isNullOrBlank()) {
            return ApiResult.Failure(ApiFailureKind.INVALID_DATA)
        }

        val body = buildJsonObject {
            put("cardId", JsonPrimitive(normalizedCardId))
            update.pan?.let { put("pan", JsonPrimitive(it)) }
            update.expiry?.let { put("expiry", JsonPrimitive(it)) }
        }.toString()
        val request = authenticatedRequest(session, "/api/card-secrets")
            .put(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request) { responseBody ->
            runCatching {
                val payload = json.parseToJsonElement(responseBody).jsonObject
                val saved = payload["saved"]?.jsonPrimitive?.booleanOrNull ?: error("Missing saved flag")
                if (!saved) error("Card secret was not saved")
                ApiResult.Success(
                    CardSecretWriteReceipt(
                        saved = true,
                        last4 = payload.nullableString("last4"),
                    ),
                )
            }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }
        }
    }

    override suspend fun deleteCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> {
        val gate = requestGate(session)
        if (gate != null) return gate
        val normalizedCardId = cardId.trim()
        if (!CARD_ID_REGEX.matches(normalizedCardId)) return ApiResult.Failure(ApiFailureKind.INVALID_DATA)

        val body = buildJsonObject { put("cardId", JsonPrimitive(normalizedCardId)) }.toString()
        val request = authenticatedRequest(session, "/api/card-secrets")
            .delete(body.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request) { responseBody ->
            runCatching {
                val payload = json.parseToJsonElement(responseBody).jsonObject
                val deleted = payload["deleted"]?.jsonPrimitive?.booleanOrNull ?: error("Missing deleted flag")
                if (!deleted) error("Card secret was not deleted")
                ApiResult.Success(CardSecretDeleteReceipt(deleted = true))
            }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }
        }
    }

    private fun requestGate(session: AuthSession): ApiResult.Failure? = when {
        session.accessToken.isBlank() -> ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED)
        session.assuranceLevel != AssuranceLevel.AAL2 -> ApiResult.Failure(ApiFailureKind.MFA_REQUIRED)
        !configuration.isConfigured -> ApiResult.Failure(ApiFailureKind.BUILD_NOT_CONFIGURED)
        else -> null
    }

    private fun authenticatedRequest(session: AuthSession, path: String): Request.Builder = Request.Builder()
        .url("${configuration.myFinHubApiBaseUrl}$path")
        .header("Authorization", "Bearer ${session.accessToken}")
        .header("Accept", "application/json")

    private fun parseEnvelope(body: String): ApiResult<CanonicalFinanceEnvelope> = runCatching {
        val envelope = json.parseToJsonElement(body).jsonObject
        val data = envelope["data"]?.jsonObject ?: error("Missing data")
        val revision = envelope["revision"]?.jsonPrimitive?.contentOrNull ?: error("Missing revision")
        val lastSavedAt = envelope["lastSavedAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        ApiResult.Success(
            CanonicalFinanceEnvelope(
                document = CanonicalFinanceDocument(data),
                revision = revision,
                lastSavedAt = lastSavedAt,
            ),
        )
    }.getOrElse { ApiResult.Failure(ApiFailureKind.MALFORMED_RESPONSE) }

    private fun JsonObject.nullableString(name: String): String? =
        this[name]?.jsonPrimitive?.contentOrNull

    private suspend fun <T> execute(
        request: Request,
        notFoundKind: ApiFailureKind? = null,
        parse: (String) -> ApiResult<T>,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> parse(body)
                    response.code == 401 -> ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED)
                    response.code == 403 -> ApiResult.Failure(ApiFailureKind.MFA_REQUIRED)
                    response.code == 404 && notFoundKind != null -> ApiResult.Failure(notFoundKind)
                    response.code == 409 -> ApiResult.Failure(ApiFailureKind.REVISION_CONFLICT)
                    response.code == 428 -> ApiResult.Failure(ApiFailureKind.PRECONDITION_REQUIRED)
                    response.code == 400 -> ApiResult.Failure(ApiFailureKind.INVALID_DATA)
                    response.code == 429 -> ApiResult.Failure(ApiFailureKind.RATE_LIMITED, retryable = true)
                    response.code in 500..599 -> ApiResult.Failure(ApiFailureKind.SERVER, retryable = true)
                    else -> ApiResult.Failure(ApiFailureKind.SERVER)
                }
            }
        } catch (_: IOException) {
            ApiResult.Failure(ApiFailureKind.NETWORK, retryable = true)
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val EMPTY_JSON_BODY = "{}".toRequestBody(JSON_MEDIA_TYPE)
        val REVISION_REGEX = Regex("^(0|[1-9]\\d*)$")
        val CARD_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
    }
}
