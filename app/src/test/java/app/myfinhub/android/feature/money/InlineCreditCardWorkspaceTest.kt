package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineCreditCardWorkspaceTest {
    private fun card(debt: Double, limit: Double?) = MoneyCard(
        id = "credit-1", nickname = "Card", last4 = "1234",
        kind = "Πιστωτική", currentBalance = debt, limit = limit,
        canonicalKind = "credit", vaultState = VaultState.LOCKED,
    )

    @Test fun utilizationNeverExceedsOneAndMissingOrInvalidLimitsRemainSafe() {
        assertEquals(0.25f, creditUtilizationRatio(card(250.0,1000.0)), 0.0001f)
        assertEquals(1f, creditUtilizationRatio(card(1500.0,1000.0)), 0.0001f)
        assertEquals(0f, creditUtilizationRatio(card(-20.0,1000.0)), 0.0001f)
        assertEquals(0f, creditUtilizationRatio(card(90.0,null)), 0.0001f)
        assertEquals(0f, creditUtilizationRatio(card(90.0,0.0)), 0.0001f)
    }
}
