package app.myfinhub.android.app

import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
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

    @Test fun rotatingSameOwnerBearerStillIsBoundaryIfAssuranceChanges() {
        assertTrue(financeSessionAuthorizationChanged(first,first.copy(
            accessToken="synthetic-rotated-aal1",assuranceLevel=AssuranceLevel.AAL1,
        )))
    }
}
