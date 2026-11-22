package app.myfinhub.android.core.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic sessions only; never authenticates or reads user finance. */
class SensitiveUiSessionIdentityTest {
    private val ownerA = AuthSession(
        accessToken = "synthetic-old-bearer",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 1000L,
        userId = "owner-a",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test fun initialAuthenticationMustNotDisplayPreviousUiBeforeAttachEffect() {
        assertFalse(matchesSensitiveUiSession(null, ownerA))
        assertFalse(matchesSensitiveUiSession(ownerA, null))
        assertFalse(matchesSensitiveUiSession(null, null))
    }

    @Test fun ownerTransitionMustHideAllPreviousSessionFinancialAndVaultUi() {
        assertFalse(matchesSensitiveUiSession(ownerA, ownerA.copy(
            userId = "owner-b",
        )))
    }

    @Test fun rotatedBearerAndAssuranceBoundaryMustHidePreviousSensitiveFrame() {
        assertFalse(matchesSensitiveUiSession(ownerA, ownerA.copy(
            accessToken = "synthetic-rotated-bearer",
        )))
        assertFalse(matchesSensitiveUiSession(ownerA, ownerA.copy(
            assuranceLevel = AssuranceLevel.AAL1,
        )))
    }

    @Test fun unchangedOwnerBearerAndAssuranceMayRetainUiAcrossRefreshTokenRotation() {
        assertTrue(matchesSensitiveUiSession(ownerA, ownerA))
        assertTrue(matchesSensitiveUiSession(ownerA, ownerA.copy(
            refreshToken = "synthetic-other-refresh",
            expiresAtEpochSeconds = 3000L,
        )))
    }
}
