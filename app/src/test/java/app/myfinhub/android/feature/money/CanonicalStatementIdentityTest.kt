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

    @Test fun inlineAndDetailPaymentRequireSameCanonicalSafeSource() {
        val due = statement("source-id", "card-A")
        val foreign = statement("source-id", "card-B")
        val safe = listOf(due)
        val collisions = listOf(due, foreign)
        assertTrue(canOpenCanonicalStatementPayment("card-A", "source-id", 25.0, safe,
            mapOf("source-id" to 1)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", 25.0, collisions,
            mapOf("source-id" to 2)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", 25.0, safe,
            mapOf("source-id" to 2)))
        assertFalse(canOpenCanonicalStatementPayment("card-B", "source-id", 25.0, safe,
            mapOf("source-id" to 1)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", 25.01, safe,
            mapOf("source-id" to 1)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", Double.NaN, safe,
            mapOf("source-id" to 1)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", 0.0,
            listOf(due.copy(remaining = 0.0)), mapOf("source-id" to 1)))
        assertFalse(canOpenCanonicalStatementPayment("card-A", "source-id", 25.0,
            listOf(due.copy(remaining = Double.POSITIVE_INFINITY)), mapOf("source-id" to 1)))
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

    @Test fun localDuplicatesStillFailClosedAndPreviewRemainsStandalone() {
        val duplicate = listOf(statement("same", "card-A"), statement("same", "card-A"))
        assertFalse(canonicalStatementIdentityUnique("same", duplicate))
        assertTrue(canonicalStatementIdentityUnique("safe", listOf(statement("safe", "card-A"))))
    }
}
