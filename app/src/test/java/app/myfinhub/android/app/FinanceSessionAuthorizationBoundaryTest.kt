package app.myfinhub.android.app

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.money.CardSecretCleanupUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Session-bound mutation and read jobs cannot cross owner/bearer boundaries. */
class FinanceSessionAuthorizationBoundaryTest {
    private val first=AuthSession(
        accessToken="synthetic-old-access",refreshToken="synthetic-refresh-1",
        expiresAtEpochSeconds=1000,userId="owner-1",assuranceLevel=AssuranceLevel.AAL2,
    )
    @Test fun initialSessionDoesNotCancelNonexistentOperations() {
        assertFalse(financeSessionAuthorizationChanged(null,first))
    }
    @Test fun sameOwnerSameBearerRefreshOnlyDoesNotRestartFinance() {
        assertFalse(financeSessionAuthorizationChanged(first,first.copy(
            refreshToken="synthetic-refresh-2",expiresAtEpochSeconds=5000,
        )))
    }
    @Test fun sameOwnerRotatedBearerMustCancelStaleLoadAndWriteJobs() {
        assertTrue(financeSessionAuthorizationChanged(first,first.copy(
            accessToken="synthetic-new-access",expiresAtEpochSeconds=5000,
        )))
    }
    @Test fun changedOwnerRequiresResetEvenIfBearerTextMatches() {
        assertTrue(financeSessionAuthorizationChanged(first,first.copy(
            userId="owner-2",
        )))
    }
    @Test fun sameBearerDowngradedAssuranceCancelsPreviouslyPrivilegedFetches() {
        assertTrue(financeSessionAuthorizationChanged(first,first.copy(
            assuranceLevel=AssuranceLevel.AAL1,
        )))
    }
    @Test fun sameBearerUpgradedAssuranceUsesNewAuthorizationBoundary() {
        assertTrue(financeSessionAuthorizationChanged(first.copy(
            assuranceLevel=AssuranceLevel.AAL1,
        ),first))
    }

    @Test fun priorOwnerFinanceStateCannotRenderOrLogOutNewOwnerBeforeAttachment() {
        val previousFailure = FinanceProductState.Failure("synthetic owner-A private error", true)
        assertSame(FinanceProductState.Loading,
            visibleFinanceStateForSession(authorized = false, observed = previousFailure))
        // A stale AuthRejected from a former bearer must not be attributed to
        // the newly authenticated owner by the root's logout side effect.
        assertSame(FinanceProductState.Loading,
            visibleFinanceStateForSession(authorized = false, observed = FinanceProductState.AuthRejected))
        assertSame(previousFailure, visibleFinanceStateForSession(true, previousFailure))
    }

    @Test fun staleCardPanAndCvvAndCleanupNeverReachNewOwnersComposeFrame() {
        val previous = CardSecretUiState.Revealed(
            cardId = "old-owner-card",
            pan = "synthetic-test-pan",
            expiry = "12/30",
            cvv = "synthetic-test-cvv",
        )
        val masked = visibleCardSecretsForSession(authorized = false, observed = previous)
        assertEquals(CardSecretUiState.Hidden(), masked)
        assertSame(previous, visibleCardSecretsForSession(authorized = true, observed = previous))
        val oldCleanup = CardSecretCleanupUiState.Complete("old-owner-card")
        assertSame(CardSecretCleanupUiState.Idle,
            visibleCardCleanupForSession(authorized = false, observed = oldCleanup))
        assertSame(oldCleanup, visibleCardCleanupForSession(true, oldCleanup))
    }

    @Test fun financeStateMustBeRevokedAfterSameOwnerBearerAndAalReattachment() {
        val previous = FinanceProductState.Failure("synthetic previous bearer error", true)
        assertSame(FinanceProductState.Idle,
            financePresentationAfterAuthorizationChange(first, first.copy(accessToken = "new-bearer"), previous))
        assertSame(FinanceProductState.Idle,
            financePresentationAfterAuthorizationChange(first, first.copy(assuranceLevel = AssuranceLevel.AAL1), previous))
        assertSame(FinanceProductState.Idle,
            financePresentationAfterAuthorizationChange(first, first.copy(userId = "new-owner"), FinanceProductState.AuthRejected))
        assertSame(previous,
            financePresentationAfterAuthorizationChange(first, first.copy(refreshToken = "refresh-only"), previous))
        assertSame(previous, financePresentationAfterAuthorizationChange(null, first, previous))
    }

    @Test fun rootServerSnapshotsAndIbanHistoryRequireSameOwnerBearerAndAal() {
        assertTrue(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first)))
        assertTrue(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first.copy(
            refreshToken = "changed-refresh-only",
        ))))
        assertFalse(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first.copy(
            assuranceLevel = AssuranceLevel.AAL1,
        ))))
        assertFalse(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first.copy(
            accessToken = "new-bearer",
        ))))
        assertFalse(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first.copy(
            userId = "new-owner",
        ))))
        assertFalse(mayPublishRootServerSnapshot(first, AuthShellUiState.Ready(first, offline = true)))
        assertFalse(mayPublishRootServerSnapshot(first, AuthShellUiState.Login()))
    }

    @Test fun delayedServerMetadataAndDeviceCallbacksRequireExactOnlineSession() {
        assertTrue(sessionScopedFinanceCallbackAuthorized(first, first, true))
        assertTrue(sessionScopedFinanceCallbackAuthorized(first.copy(refreshToken = "refreshed"), first, true))
        assertFalse(sessionScopedFinanceCallbackAuthorized(null, first, true))
        assertFalse(sessionScopedFinanceCallbackAuthorized(first.copy(userId = "another-owner"), first, true))
        assertFalse(sessionScopedFinanceCallbackAuthorized(first.copy(accessToken = "new-token"), first, true))
        assertFalse(sessionScopedFinanceCallbackAuthorized(first.copy(assuranceLevel = AssuranceLevel.AAL1), first, true))
        assertFalse(sessionScopedFinanceCallbackAuthorized(first, first, false))
    }

    @Test fun rotatingSameOwnerBearerStillIsBoundaryIfAssuranceChanges() {
        assertTrue(financeSessionAuthorizationChanged(first,first.copy(
            accessToken="synthetic-rotated-aal1",assuranceLevel=AssuranceLevel.AAL1,
        )))
    }
}
