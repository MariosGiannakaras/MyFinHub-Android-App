package app.myfinhub.android.app

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.feature.auth.AuthShellUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** D6 terminal auth/Settings acceptance: delayed IBAN and history reads stay owner-scoped. */
class D6RootServerSnapshotAuthorizationTest {
    private val owner = AuthSession(
        userId = "owner-synthetic",
        accessToken = "bearer-synthetic",
        refreshToken = "refresh-synthetic",
        expiresAtEpochSeconds = 60_000,
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test fun currentOnlineOwnerBearerMayPublishServerData() {
        assertTrue(mayPublishRootServerSnapshot(owner, AuthShellUiState.Ready(owner)))
    }

    @Test fun delayedResponseAfterLogoutOrLockMayNotRestoreOwnerMetadata() {
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Loading))
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Login()))
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Ready(owner, offline = true)))
    }

    @Test fun priorOwnersIbanHistoryMayNotPublishForNextAccount() {
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Ready(
            owner.copy(userId = "other-owner"),
        )))
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Ready(
            owner.copy(accessToken = "next-session-bearer"),
        )))
        assertFalse(mayPublishRootServerSnapshot(owner, AuthShellUiState.Ready(
            owner.copy(userId = "other-owner", accessToken = "next-session-bearer"),
        )))
    }
}
