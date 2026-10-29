package app.myfinhub.android.feature.money

import app.myfinhub.android.core.data.CanonicalCreditStatementView
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

    @Test fun localDuplicatesStillFailClosedAndPreviewRemainsStandalone() {
        val duplicate = listOf(statement("same", "card-A"), statement("same", "card-A"))
        assertFalse(canonicalStatementIdentityUnique("same", duplicate))
        assertTrue(canonicalStatementIdentityUnique("safe", listOf(statement("safe", "card-A"))))
    }
}
