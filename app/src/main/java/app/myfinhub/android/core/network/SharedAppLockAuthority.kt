package app.myfinhub.android.core.network

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.matchesSensitiveUiSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Process-memory authority. No PIN, ticket, proof or status may be persisted. */
internal class SharedAppLockAuthority(
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    // Stable identity of a single publication generation. Never include a bearer or ticket in logs.
    internal class Grant(
        private val session: AuthSession,
        val ticket: SharedAppUnlockTicket?,
        val idleMillis: Long?,
        val online: Boolean,
        var expiresAt: Long?,
        var lastTouchStartedAt: Long,
    ) {
        fun matches(candidate: AuthSession) = matchesSensitiveUiSession(session, candidate)
        fun headerValue(): String? = ticket?.valueFor(session)
        override fun toString() = "SharedAppLockGrant(<redacted>)"
    }

    private val mutableRevision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    private var active: Grant? = null
    // A disabled proof is deliberately process-local and bound to the exact bearer.
    // It permits local-only offline operation after an earlier authoritative disabled
    // response; a cold start/changed bearer/lock rejection never assumes disabled.
    private var disabledProof: AuthSession? = null

    @Synchronized
    fun publishDisabled(session: AuthSession) {
        clear()
        require(session.assuranceLevel == AssuranceLevel.AAL2 && session.accessToken.isNotBlank())
        disabledProof = session
        active = Grant(session, null, null, true, null, nowMillis())
        changed()
    }

    @Synchronized
    fun publishOfflineDisabled(session: AuthSession): Boolean {
        if (!matchesSensitiveUiSession(disabledProof, session)) return false
        active = Grant(session, null, null, false, null, nowMillis())
        changed()
        return true
    }

    @Synchronized
    fun publishVerified(
        session: AuthSession,
        verified: SharedAppLockVerification.Verified,
        verificationStartedAt: Long,
    ): Boolean {
        clear()
        val idle = verified.status.idleMinutes.toLong() * 60_000L
        val deadline = verificationStartedAt + idle
        if (!verified.status.enabled || verified.status.retryAfterMs != 0L ||
            verified.status.idleMinutes !in setOf(1, 5, 15, 30, 60) ||
            verified.ticket.valueFor(session) == null || nowMillis() >= deadline) return false
        active = Grant(session, verified.ticket, idle, true, deadline, verificationStartedAt)
        changed()
        return true
    }

    @Synchronized
    fun canPresent(session: AuthSession): Boolean = currentGrant(session) != null

    @Synchronized
    fun requestGrant(session: AuthSession): Grant? = currentGrant(session)?.takeIf { it.online }

    @Synchronized
    fun isCurrent(grant: Grant): Boolean = active === grant && !expired(grant)

    /** Only an exact in-flight grant may invalidate the present session. */
    @Synchronized
    fun reject(grant: Grant) {
        if (active === grant) clear()
    }

    /** Use only on actual foreground interaction, never periodic/background activity. */
    @Synchronized
    fun beginTouch(session: AuthSession): Grant? {
        val grant = currentGrant(session) ?: return null
        val idle = grant.idleMillis ?: return null
        val now = nowMillis()
        if (now - grant.lastTouchStartedAt < minOf(15_000L, idle / 3)) return null
        grant.lastTouchStartedAt = now
        return grant
    }

    @Synchronized
    fun renew(grant: Grant, touchStartedAt: Long): Boolean {
        if (!isCurrent(grant)) return false
        val idle = grant.idleMillis ?: return false
        val deadline = touchStartedAt + idle
        if (nowMillis() >= deadline) return false
        grant.expiresAt = deadline
        changed()
        return true
    }

    @Synchronized
    fun expireIfNeeded(session: AuthSession): Boolean {
        val grant = active ?: return false
        if (!grant.matches(session) || !expired(grant)) return false
        clear()
        return true
    }

    @Synchronized
    fun clear(rememberDisabled: Boolean = false): SharedAppUnlockTicket? {
        val ticket = active?.ticket
        active = null
        if (!rememberDisabled) disabledProof = null
        changed()
        return ticket
    }

    private fun currentGrant(session: AuthSession): Grant? =
        active?.takeIf { it.matches(session) && !expired(it) }

    private fun expired(grant: Grant): Boolean = grant.expiresAt?.let { nowMillis() >= it } == true
    private fun changed() { mutableRevision.value += 1 }
}

/** All production MyFinHub finance/device/account/vault clients use this same authority. */
internal val productionSharedAppLockAuthority = SharedAppLockAuthority()
