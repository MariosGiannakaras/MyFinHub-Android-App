package app.myfinhub.android.core.auth

import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Parses non-authoritative JWT hints needed for client flow decisions.
 *
 * This does not verify a JWT signature and must never be used as an authorization boundary. The
 * MyFinHub backend remains authoritative for token validity, owner UID and AAL2 enforcement.
 */
class JwtClaimsParser(
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    fun assuranceLevel(token: String): AssuranceLevel = runCatching {
        val payload = token.split('.').getOrNull(1) ?: return AssuranceLevel.UNKNOWN
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        when (json.parseToJsonElement(decoded.decodeToString()).jsonObject["aal"]?.jsonPrimitive?.content) {
            "aal1" -> AssuranceLevel.AAL1
            "aal2" -> AssuranceLevel.AAL2
            else -> AssuranceLevel.UNKNOWN
        }
    }.getOrDefault(AssuranceLevel.UNKNOWN)
}
