package app.myfinhub.android.feature.auth

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRevalidationPublicationTest {
    private val session = AuthSession(
        userId = "synthetic-owner",
        accessToken = "synthetic-access",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 9_999,
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test fun currentOfflineOwnerMayPublishValidatedSession() {
        assertTrue(mayPublishOfflineRevalidation(
            session, AuthShellUiState.Ready(session, offline = true), 7L, 7L,
        ))
    }

    @Test fun logoutOrNewGenerationCannotRestoreReady() {
        assertFalse(mayPublishOfflineRevalidation(
            session, AuthShellUiState.Loading, 7L, 8L,
        ))
        assertFalse(mayPublishOfflineRevalidation(
            session, AuthShellUiState.Login(), 7L, 7L,
        ))
        assertFalse(mayPublishOfflineRevalidation(
            session, AuthShellUiState.Ready(session, offline = true), 7L, 8L,
        ))
    }

    @Test fun freshAuthenticatedSessionCannotBeOverwrittenByStaleReconnect() {
        assertFalse(mayPublishOfflineRevalidation(
            session, AuthShellUiState.Ready(session, offline = false), 3L, 3L,
        ))
        assertFalse(mayPublishOfflineRevalidation(
            session,
            AuthShellUiState.Ready(
                session.copy(accessToken = "newly-refreshed-bearer"), offline = true,
            ),
            3L, 3L,
        ))
        assertFalse(mayPublishOfflineRevalidation(
            session,
            AuthShellUiState.Ready(
                session.copy(userId = "other-account"), offline = true,
            ),
            3L, 3L,
        ))
    }
}
