package app.myfinhub.android.feature.auth

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.security.PinAttemptStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundLockPolicyTest {
    private val session = AuthSession(
        accessToken = "fixture", refreshToken = "fixture",
        expiresAtEpochSeconds = Long.MAX_VALUE, userId = "fixture-owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )
    private val throttle = PinAttemptStatus(false, 0, 60_000)

    @Test
    fun leavingForegroundImmediatelyReplacesReadyWithSameOwnerLock() {
        for (offline in listOf(false, true)) {
            val next = backgroundLockedState(AuthShellUiState.Ready(session, offline), throttle)
            assertTrue(next is AuthShellUiState.Locked)
            val locked = next as AuthShellUiState.Locked
            assertEquals(session, locked.session)
            assertEquals(throttle, locked.pinStatus)
            assertFalse(locked.showPin)
        }
    }

    @Test
    fun backgroundNeverUnlocksOrCreatesSessionFromOtherStates() {
        val login = AuthShellUiState.Login()
        assertSame(login, backgroundLockedState(login, throttle))
        assertSame(AuthShellUiState.Loading, backgroundLockedState(AuthShellUiState.Loading, throttle))
        val already = AuthShellUiState.Locked(session, showPin = true, pinStatus = throttle)
        assertSame(already, backgroundLockedState(already, throttle))
    }
}
