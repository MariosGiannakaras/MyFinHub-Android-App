package app.myfinhub.android.feature.money

import app.myfinhub.android.core.data.CanonicalCreditStatementView
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
    @Test fun invalidImportedCreditMetricsNeverBecomePlausibleZeroOrInfiniteProgress() {
        val invalidDebt = creditWorkspaceFinancialSnapshot(card(Double.NaN, 1500.0))
        assertEquals(null, invalidDebt.outstanding)
        assertEquals(null, invalidDebt.available)
        assertEquals(null, invalidDebt.utilization)
        assertEquals(0f, creditUtilizationRatio(card(Double.NaN, 1500.0)), 0.0001f)

        val infiniteLimit = creditWorkspaceFinancialSnapshot(card(300.0, Double.POSITIVE_INFINITY))
        assertEquals(300.0, infiniteLimit.outstanding)
        assertEquals(null, infiniteLimit.limit)
        assertEquals(null, infiniteLimit.available)
        assertEquals(null, infiniteLimit.utilization)

        val overflowRatio = creditWorkspaceFinancialSnapshot(card(Double.MAX_VALUE, Double.MIN_VALUE))
        assertEquals(null, overflowRatio.utilization)
        assertEquals(0.0, overflowRatio.available)
        assertEquals(0f, creditUtilizationRatio(card(Double.MAX_VALUE, Double.MIN_VALUE)), 0.0001f)
        assertEquals("Μη έγκυρο ποσό", creditWorkspaceMoney(Double.NEGATIVE_INFINITY, true))
        assertEquals("Μη έγκυρο ποσό", creditWorkspaceMoney(Double.NaN, true))
        assertEquals("•••• €", creditWorkspaceMoney(Double.NaN, false))
        assertEquals("Μη έγκυρο ποσό", creditWorkspaceOptionalMoney(null, true))
        assertEquals("•••• €", creditWorkspaceOptionalMoney(null, false))
    }

    @Test fun finiteCreditBalanceStillUsesCanonicalClampingAndUtilization() {
        val valid = creditWorkspaceFinancialSnapshot(card(1250.0, 1000.0))
        assertEquals(1250.0, valid.outstanding)
        assertEquals(1000.0, valid.limit)
        assertEquals(0.0, valid.available)
        assertEquals(1f, valid.utilization)
        val refund = creditWorkspaceFinancialSnapshot(card(-20.0, 1000.0))
        assertEquals(0.0, refund.outstanding)
        assertEquals(1000.0, refund.available)
        assertEquals(0f, refund.utilization)
    }

    @Test fun longCreditHistoryUsesBoundedBatchesAndSafeArithmetic() {
        assertEquals(0, inlineCreditVisibleCount(0, 1, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(8, inlineCreditVisibleCount(31, 1, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(16, inlineCreditVisibleCount(31, 2, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(31, inlineCreditVisibleCount(31, 4, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(4, inlineCreditVisibleCount(15, 1, INLINE_CREDIT_STATEMENT_BATCH))
        assertEquals(15, inlineCreditVisibleCount(15, 4, INLINE_CREDIT_STATEMENT_BATCH))
        assertEquals(0, inlineCreditVisibleCount(-10, 1, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(8, inlineCreditVisibleCount(8, 0, INLINE_CREDIT_ACTIVITY_BATCH))
        assertEquals(Int.MAX_VALUE, inlineCreditVisibleCount(Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE))
    }
    private fun sourceStatement(id: String, cardId: String) = CanonicalCreditStatementView(
        id = id, cardId = cardId, openDate = "2026-09-01", closeDate = "2026-09-30",
        dueDate = "2026-10-25", boundaryRule = "include-closing-day",
        purchaseIds = emptyList(), paymentIds = emptyList(),
        purchaseTotal = 90.0, paymentTotal = 10.0, remaining = 80.0, status = "due",
    )

    @Test fun statementScopeRejectsForeignAndBlankCardIdsWithoutReorderingOrDeduplicating() {
        val first = sourceStatement("first", "credit-1")
        val foreign = sourceStatement("confidential-other-card", "credit-2")
        val second = sourceStatement("second", "credit-1")
        val rows = listOf(first, foreign, second, first)
        assertEquals(listOf(first, second, first), canonicalStatementsForCard("credit-1", rows))
        assertEquals(listOf(foreign), canonicalStatementsForCard("credit-2", rows))
        assertEquals(emptyList<CanonicalCreditStatementView>(), canonicalStatementsForCard("", rows))
        assertEquals(rows, listOf(first, foreign, second, first))
    }

}
