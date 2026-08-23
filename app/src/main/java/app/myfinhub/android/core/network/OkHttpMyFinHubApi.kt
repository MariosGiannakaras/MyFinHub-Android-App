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
import kotlinx.serialization.json.JsonPrimitive
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
        val gate = sessionGate(session)
        if (gate != null) return gate
        if (!configuration.isConfigured) return ApiResult.Failure(ApiFailureKind.BUILD_NOT_CONFIGURED)

        val request = Request.Builder()
            .url("${configuration.myFinHubApiBaseUrl}/api/data")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
            .get()
            .build()

        return execute(request) { body ->
            runCatching {
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
        }
    }

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> {
        val gate = sessionGate(session)
        if (gate != null) return gate
        if (!configuration.isConfigured) return ApiResult.Failure(ApiFailureKind.BUILD_NOT_CONFIGURED)
        if (!expectedRevision.matches(REVISION_REGEX)) {
            return ApiResult.Failure(ApiFailureKind.PRECONDITION_REQUIRED)
        }

        val body = buildJsonObject {
            put("state", document.state)
            put("updatedAt", JsonPrimitive(document.updatedAt))
        }.toString()
        val request = Request.Builder()
            .url("${configuration.myFinHubApiBaseUrl}/api/data")
            .header("Authorization", "Bearer ${session.accessToken}")
            .header("Accept", "application/json")
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

    private fun sessionGate(session: AuthSession): ApiResult.Failure? = when {
        session.accessToken.isBlank() -> ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED)
        session.assuranceLevel != AssuranceLevel.AAL2 -> ApiResult.Failure(ApiFailureKind.MFA_REQUIRED)
        else -> null
    }

    private suspend fun <T> execute(
        request: Request,
        parse: (String) -> ApiResult<T>,
    ): ApiResult<T> = withContext(Dispatchers.IO) {
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.isSuccessful -> parse(body)
                    response.code == 401 -> ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED)
                    response.code == 403 -> ApiResult.Failure(ApiFailureKind.MFA_REQUIRED)
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
        val REVISION_REGEX = Regex("^(0|[1-9]\\d*)$")
    }
}
