package app.myfinhub.android.feature.money

import app.myfinhub.android.core.data.CanonicalCreditStatementView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Historical Web creditStatementEvents never invents a settled missing event. */
class WebCardStatementLinkedRowsParityTest {
    private fun statement(purchases: List<String>, payments: List<String> = emptyList()) =
        CanonicalCreditStatementView(
            id = "statement", cardId = "credit-1",
            openDate = "2026-09-01", closeDate = "2026-09-30",
            dueDate = "2026-10-25", boundaryRule = "include-closing-day",
            purchaseIds = purchases, paymentIds = payments,
            purchaseTotal = 50.0, paymentTotal = 0.0,
            remaining = 50.0, status = "due",
        )
    private fun purchase(id: String, label: String = "Καφές") =
        MoneyCardActivity(id,"2026-09-01",label,25.0,MoneyCardActivityKind.PURCHASE)
    private fun payment(id: String) =
        MoneyCardActivity(id,"2026-09-10","Εξόφληση",25.0,MoneyCardActivityKind.PAYMENT)

    @Test fun intactPurchasesAndPaymentsPreserveOrderAndTrueAmounts() {
        val purchases = listOf(purchase("a"), purchase("b", ""))
        val paid = payment("c")
        val original = purchases + paid
        val rows = canonicalStatementLinkedRows(
            statement(listOf("b","a"), listOf("c")),original)
        assertEquals(listOf("Χωρίς περιγραφή","Καφές","Εξόφληση"),
            rows.map { it.displayTitle })
        assertEquals(listOf("Αγορά","Αγορά","Πληρωμή"),rows.map { it.kindLabel })
        assertEquals(listOf("b","a","c"),rows.map { it.openActivityId })
        assertEquals(listOf(25.0,25.0,25.0),rows.map { it.amount })
        assertEquals(original, purchases + paid)
    }

    @Test fun missingHistoricEventNeverDisclosesRawEventIdOrImaginedAmount() {
        val id="sensitive-legacy-raw-event-identity"
        val row=canonicalStatementLinkedRows(statement(listOf(id)),emptyList()).single()
        assertNull(row.openActivityId)
        assertNull(row.amount)
        assertTrue(!row.displayTitle.contains(id))
        assertTrue(!row.warning.orEmpty().contains(id))
    }

    @Test fun duplicateLinkedIdsInOneStatementCannotOpenArbitraryActivity() {
        val rows=canonicalStatementLinkedRows(
            statement(listOf("duplicate"),listOf("duplicate")),
            listOf(purchase("duplicate")),
        )
        assertEquals(2,rows.size)
        assertTrue(rows.all { it.openActivityId == null && it.amount == null })
        assertTrue(rows.all { it.warning.orEmpty().contains("Πολλαπλή") })
    }

    @Test fun duplicatePhysicalActivityIdentitiesStayFailClosed() {
        val rows=canonicalStatementLinkedRows(
            statement(listOf("duplicate")),
            listOf(purchase("duplicate"),purchase("duplicate")),
        )
        assertNull(rows.single().openActivityId)
        assertNull(rows.single().amount)
    }

    @Test fun wrongKindCannotNavigateToAccidentalPurchaseOrPayment() {
        val rows=canonicalStatementLinkedRows(
            statement(listOf("credit-paid"),listOf("purchased")),
            listOf(payment("credit-paid"),purchase("purchased")),
        )
        assertTrue(rows.all { it.openActivityId == null })
        assertTrue(rows.all { it.warning.orEmpty().contains("Ασυμφωνία") })
    }
}
