package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthResult
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.SupabaseAuthGateway
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.data.withMutableState
import java.io.File
import java.net.Proxy
import java.nio.ByteBuffer
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in proof against the pinned, disposable Web + real local GoTrue/PostgREST stack.
 * No live URL, admin key, production credential, cookie fallback or mock server is accepted.
 * The runner creates synthetic users and a canonical synthetic finance fixture.
 */
class SharedAppLockRealStackTest {
    private val web = "http://127.0.0.1:4317"
    private val supabase = "http://127.0.0.1:54321"
    private val jsonType = "application/json".toMediaType()
    private lateinit var key: String
    private lateinit var client: OkHttpClient
    private lateinit var gateway: SupabaseAuthGateway
    private lateinit var lock: SharedAppLockApi

    @Test
    fun nativeBearerLockAndRecoveryAgainstRealLocalStack() {
        assumeTrue("Opt-in isolated stack only", System.getenv("MYFINHUB_RUN_ISOLATED_LOCK_TEST") == "1")
        runBlocking {
            key = input("MYFINHUB_ISOLATED_PUBLISHABLE_KEY")
            val ownerEmail = input("MYFINHUB_ISOLATED_OWNER_EMAIL")
            val outsiderEmail = input("MYFINHUB_ISOLATED_OUTSIDER_EMAIL")
            val password = input("MYFINHUB_ISOLATED_PASSWORD")
            check(ownerEmail.endsWith("@example.test") && outsiderEmail.endsWith("@example.test"))
            val fixture = File(input("MYFINHUB_ISOLATED_FIXTURE")).readText()
            client = NetworkClientFactory.create().newBuilder()
                .proxy(Proxy.NO_PROXY).cookieJar(CookieJar.NO_COOKIES)
                .followRedirects(false).followSslRedirects(false).build()
            val configuration = AppConfiguration(web, supabase, key)
            gateway = SupabaseAuthGateway(configuration, client)
            lock = SharedAppLockApi(configuration, client)
            // Deliberately freeze the *test* client clock so the actual database expiry
            // and 423 response, not only local expiry, are proven below.
            val authority = SharedAppLockAuthority(nowMillis = { 0L })
            val finance = OkHttpMyFinHubApi(configuration, client, authority)

            expectHttp("no credentials", raw(web, "/api/auth/app-lock"), 401)
            val aal1 = authSuccess("owner password", gateway.signInWithPassword(ownerEmail, password.toCharArray()))
            assertEquals(AssuranceLevel.AAL1, aal1.assuranceLevel)
            assertTrue("Native AAL1 status must fail", lock.status(aal1) is SharedAppLockResult.Failure)
            expectHttp("server AAL1", raw(web, "/api/auth/app-lock", aal1.accessToken), 403)
            val (owner, totpSecret, enrollmentCode) = enroll(aal1)
            assertEquals(AssuranceLevel.AAL2, owner.assuranceLevel)
            val outsiderAal1 = authSuccess("outsider password", gateway.signInWithPassword(outsiderEmail, password.toCharArray()))
            val outsider = enroll(outsiderAal1).first
            assertEquals(AssuranceLevel.AAL2, outsider.assuranceLevel)
            assertTrue("Nonowner AAL2 must fail", lock.status(outsider) is SharedAppLockResult.Failure)

            val disabled = lockSuccess("initial disabled", lock.status(owner))
            assertFalse(disabled.enabled)
            authority.publishDisabled(owner)
            expectHttp("import synthetic fixture", raw(web, "/api/import", owner.accessToken, "POST", fixture,
                mapOf("x-rheomiq-confirm-import" to "replace")), 200)
            val initial = apiSuccess("disabled finance", finance.loadFinanceData(owner))
            assertTrue("Synthetic state must have accounts", initial.document.accounts().isNotEmpty())
            assertTrue("Direct JWT baseline", directRows(owner).size == 1)
            assertTrue("Nonowner direct JWT denial", directRows(outsider).isEmpty())

            // Configure only in the disposable test stack. Android has no configure API.
            expectHttp("configure fixture PIN", raw(web, "/api/auth/app-lock", owner.accessToken, "POST",
                """{"action":"configure","newPin":"1234","idleMinutes":1}"""), 200)
            expectGate("known disabled grant rejected by real server", finance.loadFinanceData(owner), true)
            assertFalse("423 revokes local grant", authority.canPresent(owner))
            assertTrue(lockSuccess("enabled status", lock.status(owner)).enabled)
            assertTrue("Direct JWT is denied with no ticket", directRows(owner).isEmpty())
            expectHttp("missing ticket", raw(web, "/api/data", owner.accessToken), 423)
            expectHttp("forged ticket", raw(web, "/api/data", owner.accessToken,
                headers = mapOf("X-MyFinHub-App-Unlock" to "A".repeat(43))), 423)
            expectGate("local presentation gate", finance.loadFinanceData(owner), false)

            var verified = verify(owner, "1234")
            assertTrue(authority.publishVerified(owner, verified, 0))
            apiSuccess("unlocked finance", finance.loadFinanceData(owner))
            apiSuccess("unlocked history", finance.loadServerFinanceHistory(owner))
            apiSuccess("unlocked account metadata", finance.loadAccountMetadata(owner))
            assertTrue("Direct JWT unlocked baseline", directRows(owner).size == 1)
            // Mutate only synthetic account display metadata in the disposable
            // canonical fixture through Android's real history/finance preconditions.
            val settings = initial.document.state["settings"]!!.jsonObject
            val names = settings["accountNames"]!!.jsonObject
            val label = "Isolated Android QA"
            val updatedNames = JsonObject(names + ("qa-cash" to JsonPrimitive(label)))
            val updatedSettings = JsonObject(settings + ("accountNames" to updatedNames))
            val updated = initial.document.withMutableState(
                JsonObject(initial.document.state + ("settings" to updatedSettings)), Instant.now().toString(),
            )
            val receipt = apiSuccess("unlocked synthetic write", finance.saveMutableState(owner, updated, initial.revision))
            assertTrue("Write advanced the revision", receipt.revision != initial.revision)
            val readback = apiSuccess("synthetic write readback", finance.loadFinanceData(owner))
            val readbackNames = readback.document.state["settings"]!!.jsonObject["accountNames"]!!.jsonObject
            assertTrue("Synthetic account label persisted", readbackNames["qa-cash"]!!.jsonPrimitive.content == label)
            lockSuccess("real touch", lock.touch(owner, verified.ticket))
            lockSuccess("real lock", lock.lock(owner, verified.ticket))
            expectGate("server-revoked ticket", finance.loadFinanceData(owner), true)
            assertFalse(authority.canPresent(owner))
            assertTrue("Direct JWT locked again", directRows(owner).isEmpty())

            verified = verify(owner, "1234")
            assertTrue(authority.publishVerified(owner, verified, 0))
            delay(63_000)
            val expiredTouch = lock.touch(owner, verified.ticket)
            assertTrue("Server expired ticket rejects touch", expiredTouch is SharedAppLockResult.Failure &&
                expiredTouch.kind == SharedAppLockFailure.LOCK_REQUIRED)
            expectGate("actual server expiration", finance.loadFinanceData(owner), true)
            assertTrue("Direct JWT denied after expiration", directRows(owner).isEmpty())

            verified = verify(owner, "1234")
            assertTrue(authority.publishVerified(owner, verified, 0))
            repeat(5) {
                val wrong = lockSuccess("wrong PIN", lock.verify(owner, "0000".toCharArray()))
                assertTrue("Wrong PIN never unlocks", wrong is SharedAppLockVerification.Rejected &&
                    wrong.reason == SharedAppLockVerification.Rejection.WRONG_PIN)
            }
            val limited = lockSuccess("rate limit", lock.verify(owner, "1234".toCharArray()))
            assertTrue("Correct PIN blocked by rate limit", limited is SharedAppLockVerification.Rejected &&
                limited.reason == SharedAppLockVerification.Rejection.RATE_LIMITED && limited.status.retryAfterMs > 0)
            val freshCode = nextCode(totpSecret, enrollmentCode)
            assertTrue("Wrong recovery credentials fail", lock.recover(owner, ownerEmail,
                "InvalidSyntheticPassword".toCharArray(), freshCode.toCharArray(), "5678".toCharArray()) is SharedAppLockResult.Failure)
            val recovered = lockSuccess("fresh password and TOTP", lock.recover(owner, ownerEmail,
                password.toCharArray(), freshCode.toCharArray(), "5678".toCharArray()))
            assertTrue(recovered.enabled)
            expectGate("recovery revoked prior ticket", finance.loadFinanceData(owner), true)
            assertTrue("Recovery does not unlock direct JWT", directRows(owner).isEmpty())
            val old = lockSuccess("old PIN after recovery", lock.verify(owner, "1234".toCharArray()))
            assertTrue("Old PIN rejected", old is SharedAppLockVerification.Rejected)
            verified = verify(owner, "5678")
            assertTrue(authority.publishVerified(owner, verified, 0))
            apiSuccess("new PIN unlocks original session", finance.loadFinanceData(owner))

            // Exercise the actual active-device RLS boundary separately from the
            // currently cookie-only Web /api/auth/devices handler.
            val sid = Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(owner.accessToken.split('.')[1])))
                .jsonObject["session_id"]?.jsonPrimitive?.content ?: error("Missing session identity")
            check(Regex("^[0-9a-fA-F-]{36}$").matches(sid))
            val revoke = raw(supabase, "/rest/v1/myfinhub_device_sessions?session_id=eq.$sid",
                owner.accessToken, "PATCH", buildJsonObject { put("revoked_at", Instant.now().toString()) }.toString(),
                mapOf("Prefer" to "return=minimal"))
            assertTrue("Synthetic device revocation must succeed", revoke.status in 200..299)
            val revoked = finance.loadFinanceData(owner)
            assertTrue("Revoked device must not load finance", revoked is ApiResult.Failure &&
                revoked.kind == ApiFailureKind.AUTH_REQUIRED)
            assertTrue("Revoked device status rejected", lock.status(owner) is SharedAppLockResult.Failure)
            assertTrue("Revoked device direct JWT denied", directRows(owner).isEmpty())
        }
    }

    private fun input(name: String): String = requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) {
        "Missing isolated fixture input: $name"
    }

    private data class Reply(val status: Int, val body: JsonElement?)
    private fun raw(base: String, path: String, bearer: String? = null, method: String = "GET",
        body: String? = null, headers: Map<String, String> = emptyMap()): Reply {
        require(base == web || base == supabase)
        require(path.startsWith("/") && !path.startsWith("//"))
        val builder = Request.Builder().url(base + path).header("Accept", "application/json")
        if (base == supabase) builder.header("apikey", key)
        bearer?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (name, value) -> builder.header(name, value) }
        builder.method(method, body?.toRequestBody(jsonType))
        return client.newCall(builder.build()).execute().use { response ->
            Reply(response.code, runCatching { Json.parseToJsonElement(response.body.string()) }.getOrNull())
        }
    }

    private fun expectHttp(stage: String, reply: Reply, expected: Int) {
        assertEquals(stage, expected, reply.status)
    }

    private fun directRows(session: AuthSession): JsonArray {
        val reply = raw(supabase, "/rest/v1/rheomiq_app_state?select=id", session.accessToken)
        expectHttp("direct RLS read", reply, 200)
        return reply.body as? JsonArray ?: error("Direct RLS returned malformed rows")
    }

    private fun <T> authSuccess(stage: String, result: AuthResult<T>): T =
        (result as? AuthResult.Success<T>)?.value ?: error("$stage: auth failed (${(result as? AuthResult.Failure)?.kind})")
    private fun <T> lockSuccess(stage: String, result: SharedAppLockResult<T>): T =
        (result as? SharedAppLockResult.Success<T>)?.value ?: error("$stage: lock request failed (${(result as? SharedAppLockResult.Failure)?.kind}, HTTP ${(result as? SharedAppLockResult.Failure)?.httpStatus})")
    private fun <T> apiSuccess(stage: String, result: ApiResult<T>): T =
        (result as? ApiResult.Success<T>)?.value ?: error("$stage: finance request failed (${(result as? ApiResult.Failure)?.kind}, HTTP ${(result as? ApiResult.Failure)?.statusCode}, ${(result as? ApiResult.Failure)?.serverCode})")
    private fun expectGate(stage: String, result: ApiResult<*>, server: Boolean) {
        assertTrue(stage, result is ApiResult.Failure && result.kind == ApiFailureKind.APP_LOCK_REQUIRED &&
            if (server) result.statusCode == 423 else result.statusCode == null)
    }

    private suspend fun verify(owner: AuthSession, pin: String): SharedAppLockVerification.Verified =
        lockSuccess("correct PIN", lock.verify(owner, pin.toCharArray())) as? SharedAppLockVerification.Verified
            ?: error("Correct PIN did not return verified ticket")

    private suspend fun enroll(aal1: AuthSession): Triple<AuthSession, String, String> {
        val response = raw(supabase, "/auth/v1/factors", aal1.accessToken, "POST",
            """{"factor_type":"totp","friendly_name":"Isolated native QA"}""")
        assertTrue("TOTP enrollment must succeed", response.status in 200..299)
        val data = response.body as? JsonObject ?: error("Missing enrollment data")
        val factor = data["id"]?.jsonPrimitive?.content ?: error("Missing factor ID")
        val secret = data["totp"]?.jsonObject?.get("secret")?.jsonPrimitive?.content ?: error("Missing factor secret")
        val challenge = authSuccess("native challenge", gateway.challengeTotp(aal1.accessToken, factor))
        val code = nextCode(secret, "")
        val owner = authSuccess("native AAL2", gateway.verifyTotp(aal1.accessToken, factor, challenge.id, code.toCharArray()))
        return Triple(owner, secret, code)
    }

    private suspend fun nextCode(secret: String, previous: String): String {
        repeat(35) {
            val code = totp(secret)
            // Avoid expiration at the end of a TOTP time window.
            if (code != previous && (System.currentTimeMillis() / 1000) % 30 < 23) return code
            delay(1_000)
        }
        error("Fresh TOTP window unavailable")
    }

    private fun totp(secret: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        val bits = secret.uppercase().trimEnd('=').map { char ->
            val digit = alphabet.indexOf(char)
            require(digit >= 0) { "Invalid test TOTP secret" }
            digit.toString(2).padStart(5, '0')
        }.joinToString("")
        val bytes = bits.chunked(8).filter { it.length == 8 }.map { it.toInt(2).toByte() }.toByteArray()
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(bytes, "HmacSHA1"))
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(System.currentTimeMillis() / 30_000L).array())
        val offset = digest.last().toInt() and 15
        val value = ByteBuffer.wrap(digest, offset, 4).int and 0x7fffffff
        return (value % 1_000_000).toString().padStart(6, '0')
    }
}
