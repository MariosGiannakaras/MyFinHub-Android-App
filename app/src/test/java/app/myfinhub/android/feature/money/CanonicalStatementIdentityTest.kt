package app.myfinhub.android.feature.money

import app.myfinhub.android.core.data.CanonicalCreditStatementView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalStatementIdentityTest {
    private fun statement(id: String, owner: String) = CanonicalCreditStatementView(
        id = id, cardId = owner, openDate = "2026-09-01",
        closeDate = "2026-09-30", dueDate = "2026-10-25",
        boundaryRule = "include-closing-day",
        purchaseIds = emptyList(), paymentIds = emptyList(),
        purchaseTotal = 0.0, paymentTotal = 0.0,
        remaining = 25.0, status = "due",
    )

    @Test fun globalCollisionOrMissingCanonicalIdIsNotSelectible() {
        val own = listOf(statement("shared", "card-A"), statement("safe", "card-A"))
        val globalCounts = mapOf("shared" to 2, "safe" to 1)
        assertFalse(canonicalStatementIdentityUnique("shared", own, globalCounts))
        assertTrue(canonicalStatementIdentityUnique("safe", own, globalCounts))
        assertFalse(canonicalStatementIdentityUnique("not-in-ledger", own, globalCounts))
        assertFalse(canonicalStatementIdentityUnique(" ", listOf(statement(" ", "card-A"))))
    }

    @Test fun nativePayableFilterPreservesHistoryAndExcludesInvalidMoney() {
        val unpaid = statement("unpaid", "card-A")
        val paid = statement("paid", "card-A").copy(remaining = 0.0, status = "paid")
        val invalid = statement("invalid", "card-A").copy(remaining = Double.NaN)
        val all = listOf(unpaid, paid, invalid)
        assertEquals(all, filteredCardStatements(all, CardStatementFilter.ALL))
        assertEquals(listOf(unpaid), filteredCardStatements(all, CardStatementFilter.PAYABLE))
        assertEquals(3, all.size)
    }

    @Test fun directStatementPaymentRequiresCorrectOwnerUniqueSourceAndExactValidDebt() {
        val eligible = statement("s1", "credit-A")
        val foreign = statement("s2", "credit-B")
        val selected = listOf(eligible, foreign)
        val counts = mapOf("s1" to 1, "s2" to 1)
        assertTrue(canOpenCreditStatementPayment("credit-A", "s1", 25.0, selected, counts))
        assertFalse(canOpenCreditStatementPayment("credit-B", "s1", 25.0, selected, counts))
        assertFalse(canOpenCreditStatementPayment("credit-A", "s2", 25.0, selected, counts))
        assertFalse(canOpenCreditStatementPayment("credit-A", "s1", 24.0, selected, counts))
        assertFalse(canOpenCreditStatementPayment("credit-A", "s1", Double.NaN, selected, counts))
        assertFalse(canOpenCreditStatementPayment("credit-A", "s1", 25.0, selected, mapOf("s1" to 2)))
        assertFalse(canOpenCreditStatementPayment("credit-A", "s1", 25.0, selected + eligible, counts))
        assertFalse(canOpenCreditStatementPayment(
            "credit-A", "s1", 25.0, listOf(eligible.copy(remaining = Double.NaN)), counts))
        assertFalse(canOpenCreditStatementPayment(
            "credit-A", "s1", 25.0, listOf(eligible.copy(remaining = 0.0)), counts))
    }

    @Test fun localDuplicatesStillFailClosedAndPreviewRemainsStandalone() {
        val duplicate = listOf(statement("same", "card-A"), statement("same", "card-A"))
        assertFalse(canonicalStatementIdentityUnique("same", duplicate))
        assertTrue(canonicalStatementIdentityUnique("safe", listOf(statement("safe", "card-A"))))
    }
}
