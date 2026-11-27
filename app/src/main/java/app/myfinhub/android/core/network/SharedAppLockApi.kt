package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.config.AppConfiguration
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

internal data class SharedAppLockStatus(val enabled: Boolean, val idleMinutes: Int, val retryAfterMs: Long)

/** A server-issued ticket is never a serializable credential or a replacement for AAL2. */
internal class SharedAppUnlockTicket private constructor(
    private val value: String,
    private val ownerId: String,
    private val accessToken: String,
) {
    // Conservative: any bearer rotation requires fresh verification, even for the same owner.
    internal fun valueFor(session: AuthSession): String? = value.takeIf {
        session.assuranceLevel == AssuranceLevel.AAL2 && session.userId == ownerId &&
            session.accessToken == accessToken
    }
    override fun toString() = "SharedAppUnlockTicket(<redacted>)"

    companion object {
        internal fun fromVerifiedResponse(value: String, session: AuthSession): SharedAppUnlockTicket? =
            if (Regex("^[A-Za-z0-9_-]{43}$").matches(value) &&
                session.assuranceLevel == AssuranceLevel.AAL2 && session.accessToken.isNotBlank()) {
                SharedAppUnlockTicket(value, session.userId, session.accessToken)
            } else null
    }
}

internal sealed interface SharedAppLockVerification {
    data class Verified(val status: SharedAppLockStatus, val ticket: SharedAppUnlockTicket) : SharedAppLockVerification
    data class Rejected(val status: SharedAppLockStatus, val reason: Rejection) : SharedAppLockVerification
    enum class Rejection { WRONG_PIN, RATE_LIMITED, NOT_CONFIGURED }
}

internal sealed interface SharedAppLockResult<out T> {
    data class Success<T>(val value: T) : SharedAppLockResult<T>
    data class Failure(val kind: SharedAppLockFailure, val httpStatus: Int? = null) : SharedAppLockResult<Nothing>
}

internal enum class SharedAppLockFailure {
    BUILD_NOT_CONFIGURED, AUTH_REQUIRED, MFA_REQUIRED, INVALID_INPUT, WRONG_SESSION,
    FORBIDDEN, LOCK_REQUIRED, RATE_LIMITED, UNAVAILABLE, NETWORK, MALFORMED_RESPONSE,
}

/**
 * Native bearer-only transport for the canonical Web app-lock endpoint.
 * This deliberately has no storage, UI, background retries or configuration/activation action.
 * The auth shell owns lifecycle transitions; the protected API uses a separate memory-only grant.
 */
internal class SharedAppLockApi(
    private val configuration: AppConfiguration,
    client: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client = client.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    /**
     * An idle pooled connection can close during MFA. Status is a foreground
     * read, so retry a transport failure once with the same authenticated session.
     * Verification, touch, lock and recovery never retry implicitly.
     */
    suspend fun status(session: AuthSession): SharedAppLockResult<SharedAppLockStatus> {
        val first = readStatus(session)
        return if (first is SharedAppLockResult.Failure && first.kind == SharedAppLockFailure.NETWORK) {
            readStatus(session)
        } else first
    }

    private suspend fun readStatus(session: AuthSession): SharedAppLockResult<SharedAppLockStatus> =
        execute(session, null) { row ->
            require(row.strictBoolean("ok") == true && row["unlockToken"] == null && row["error"] == null)
            parseStatus(row)
        }

    suspend fun verify(session: AuthSession, pin: CharArray): SharedAppLockResult<SharedAppLockVerification> {
        if (!validPin(pin)) return failure(SharedAppLockFailure.INVALID_INPUT)
        return execute(session, buildJsonObject {
            put("action", "verify")
            put("pin", pin.concatToString())
        }) { row ->
            val status = parseStatus(row)
            if (row.strictBoolean("ok") == true) {
                require(status.enabled && status.retryAfterMs == 0L && row["error"] == null)
                val token = requireNotNull(row.strictString("unlockToken"))
                val ticket = requireNotNull(SharedAppUnlockTicket.fromVerifiedResponse(token, session))
                SharedAppLockVerification.Verified(status, ticket)
            } else {
                require(row.strictBoolean("ok") == false && row["unlockToken"] == null)
                val reason = when (row.strictString("error")) {
                    "APP_LOCK_WRONG_PIN" -> SharedAppLockVerification.Rejection.WRONG_PIN
                    "APP_LOCK_RATE_LIMITED" -> SharedAppLockVerification.Rejection.RATE_LIMITED
                    "APP_LOCK_NOT_CONFIGURED" -> SharedAppLockVerification.Rejection.NOT_CONFIGURED
                    else -> error("Unrecognized rejection")
                }
                require(status.enabled == (reason != SharedAppLockVerification.Rejection.NOT_CONFIGURED))
                if (reason == SharedAppLockVerification.Rejection.RATE_LIMITED) require(status.retryAfterMs > 0L)
                SharedAppLockVerification.Rejected(status, reason)
            }
        }
    }

    suspend fun touch(session: AuthSession, ticket: SharedAppUnlockTicket): SharedAppLockResult<Unit> =
        updateTicket(session, ticket, "touch")

    suspend fun lock(session: AuthSession, ticket: SharedAppUnlockTicket): SharedAppLockResult<Unit> =
        updateTicket(session, ticket, "lock")

    private suspend fun updateTicket(
        session: AuthSession, ticket: SharedAppUnlockTicket, action: String,
    ): SharedAppLockResult<Unit> {
        val value = ticket.valueFor(session) ?: return failure(SharedAppLockFailure.WRONG_SESSION)
        return execute(session, buildJsonObject {
            put("action", action)
            put("unlockToken", value)
        }) { row ->
            require(row.strictBoolean("ok") == true && row["unlockToken"] == null && row["error"] == null)
        }
    }

    /** Recovery proves fresh password + TOTP on the server; success does NOT yield an unlock ticket. */
    suspend fun recover(
        session: AuthSession, email: String, password: CharArray, code: CharArray, newPin: CharArray,
    ): SharedAppLockResult<SharedAppLockStatus> {
        val normalized = email.trim().lowercase()
        if (normalized.length !in 5..254 || '@' !in normalized || password.size !in 8..512 ||
            code.size != 6 || code.any { it !in '0'..'9' } || !validPin(newPin)) {
            return failure(SharedAppLockFailure.INVALID_INPUT)
        }
        return execute(session, buildJsonObject {
            put("action", "recover")
            put("email", normalized)
            put("password", password.concatToString())
            put("code", code.concatToString())
            put("newPin", newPin.concatToString())
        }) { row ->
            require(row.strictBoolean("ok") == true && row["unlockToken"] == null && row["error"] == null)
            parseStatus(row).also { require(it.enabled && it.retryAfterMs == 0L) }
        }
    }

    private suspend fun <T> execute(
        session: AuthSession, payload: JsonObject?, parse: (JsonObject) -> T,
    ): SharedAppLockResult<T> = withContext(Dispatchers.IO) {
        when {
            session.accessToken.isBlank() -> return@withContext failure(SharedAppLockFailure.AUTH_REQUIRED)
            session.assuranceLevel != AssuranceLevel.AAL2 -> return@withContext failure(SharedAppLockFailure.MFA_REQUIRED)
            !configuration.isConfigured -> return@withContext failure(SharedAppLockFailure.BUILD_NOT_CONFIGURED)
        }
        try {
            val builder = Request.Builder()
                .url("${configuration.myFinHubApiBaseUrl}/api/auth/app-lock")
                .header("Authorization", "Bearer ${session.accessToken}")
                .header("Accept", "application/json")
            val request = if (payload == null) builder.get().build()
                else builder.post(payload.toString().toRequestBody(JSON_MEDIA_TYPE)).build()
            client.newCall(request).execute().use { response ->
                if (response.code != 200) {
                    val kind = when (response.code) {
                        401 -> SharedAppLockFailure.AUTH_REQUIRED
                        403 -> SharedAppLockFailure.FORBIDDEN
                        423 -> SharedAppLockFailure.LOCK_REQUIRED
                        429 -> SharedAppLockFailure.RATE_LIMITED
                        else -> SharedAppLockFailure.UNAVAILABLE
                    }
                    return@withContext failure(kind, response.code)
                }
                val bytes = response.peekBody(MAX_RESPONSE_BYTES + 1).bytes()
                if (bytes.size.toLong() > MAX_RESPONSE_BYTES) return@withContext failure(SharedAppLockFailure.MALFORMED_RESPONSE, 200)
                try {
                    val row = json.parseToJsonElement(bytes.decodeToString(throwOnInvalidSequence = true)) as? JsonObject ?: error("Invalid object")
                    SharedAppLockResult.Success(parse(row))
                } catch (_: Exception) {
                    failure(SharedAppLockFailure.MALFORMED_RESPONSE, 200)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            failure(SharedAppLockFailure.NETWORK)
        } catch (_: Exception) {
            failure(SharedAppLockFailure.UNAVAILABLE)
        }
    }

    private fun parseStatus(row: JsonObject): SharedAppLockStatus {
        val enabled = requireNotNull(row.strictBoolean("enabled"))
        val idle = requireNotNull(row.strictLong("idleMinutes"))
        val retry = requireNotNull(row.strictLong("retryAfterMs"))
        require(idle in setOf(1L, 5L, 15L, 30L, 60L) && retry in 0L..30_000L && (enabled || retry == 0L))
        return SharedAppLockStatus(enabled, idle.toInt(), retry)
    }

    private fun JsonObject.strictBoolean(key: String) = (this[key] as? JsonPrimitive)
        ?.takeUnless { it.isString }?.booleanOrNull
    private fun JsonObject.strictLong(key: String) = (this[key] as? JsonPrimitive)
        ?.takeUnless { it.isString }?.longOrNull
    private fun JsonObject.strictString(key: String) = (this[key] as? JsonPrimitive)
        ?.takeIf { it.isString }?.content
    private fun validPin(pin: CharArray) = pin.size == 4 && pin.all { it in '0'..'9' }
    private fun failure(kind: SharedAppLockFailure, status: Int? = null) = SharedAppLockResult.Failure(kind, status)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val MAX_RESPONSE_BYTES = 8192L
    }
}
