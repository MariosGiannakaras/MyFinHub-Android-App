package app.myfinhub.android.feature.auth

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.security.PinAttemptStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InitialUnlockGenerationSafetyTest {
    private val session = AuthSession(
        userId = "owner-synthetic",
        accessToken = "access-synthetic",
        refreshToken = "refresh-synthetic",
        expiresAtEpochSeconds = 10_000,
        assuranceLevel = AssuranceLevel.AAL2,
    )
    private val locked = AuthShellUiState.Locked(
        session = session, showPin = true,
        pinStatus = PinAttemptStatus(allowed = true, attemptsRemaining = 5),
    )

    @Test fun onlyCurrentLockedSessionMayFinishAnInFlightPinCheck() {
        assertTrue(mayResumeLockedAuth(session, locked, 7L, 7L))
        assertFalse(mayResumeLockedAuth(session, locked, 7L, 8L))
        assertFalse(mayResumeLockedAuth(session, locked.copy(session = session.copy(
            userId = "another-owner",
        )), 7L, 7L))
        assertFalse(mayResumeLockedAuth(session, locked.copy(session = session.copy(
            accessToken = "refreshed-bearer",
        )), 7L, 7L))
        assertFalse(mayResumeLockedAuth(session, AuthShellUiState.Loading, 7L, 7L))
        assertFalse(mayResumeLockedAuth(session, AuthShellUiState.Login(), 7L, 7L))
    }

    @Test fun delayedInitialUnlockCannotPublishAfterLogoutOrNewAuthGeneration() {
        assertTrue(mayPublishInitialUnlock(AuthShellUiState.Loading, 2L, 2L))
        assertFalse(mayPublishInitialUnlock(AuthShellUiState.Loading, 2L, 3L))
        assertFalse(mayPublishInitialUnlock(locked, 2L, 2L))
        assertFalse(mayPublishInitialUnlock(AuthShellUiState.Login(), 2L, 2L))
        assertFalse(mayPublishInitialUnlock(AuthShellUiState.Ready(session), 2L, 2L))
    }

    @Test fun pinEnrollmentCannotReopenFinanceAfterSessionTransition() {
        val enrolling = AuthShellUiState.PinEnrollment(session = session)
        assertTrue(mayFinishPinEnrollment(session, enrolling, 4L, 4L))
        assertFalse(mayFinishPinEnrollment(session, enrolling, 4L, 5L))
        assertFalse(mayFinishPinEnrollment(session, AuthShellUiState.Loading, 4L, 4L))
        assertFalse(mayFinishPinEnrollment(session, AuthShellUiState.Login(), 4L, 4L))
        assertFalse(mayFinishPinEnrollment(session, AuthShellUiState.PinEnrollment(
            session = session.copy(accessToken = "new-bearer"),
        ), 4L, 4L))
    }
}
