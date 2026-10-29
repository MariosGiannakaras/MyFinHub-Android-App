package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityLedgerIdentityTest {
    private fun row(id: String, title: String) = ActivityItem(
        id = id,
        dateLabel = "19 Οκτ",
        kind = ActivityKind.EXPENSE,
        title = title,
        subtitle = "",
        amount = -5.0,
        accountLabel = "Μετρητά",
        category = "Τρόφιμα",
        rawDate = "2026-10-19",
    )

    @Test fun blankAndWhitespaceIdsCannotNavigateEvenWhenOnlyOneRowExists() {
        for (id in listOf("", "   ", "\t")) {
            val ledger = ActivityUiState(items = listOf(row(id, "Παλιά κίνηση")))
            assertFalse(ledger.isUniqueMutationTarget(id))
            assertEquals("Χωρίς αναγνωριστικό · η κίνηση δεν ανοίγει",
                activityLedgerIdentityWarning(id, ledger.ambiguousIds))
        }
    }

    @Test fun duplicateIdsRemainNonNavigableAcrossHiddenFilteredRows() {
        val ledger = ActivityUiState(items = listOf(
            row("same", "Πρώτη"), row("same", "Δεύτερη"), row("unique", "Τρίτη"),
        ))
        assertFalse(ledger.isUniqueMutationTarget("same"))
        assertEquals("Διπλό αναγνωριστικό · απαιτεί έλεγχο",
            activityLedgerIdentityWarning("same", ledger.ambiguousIds))
        assertTrue(ledger.isUniqueMutationTarget("unique"))
        assertEquals(null, activityLedgerIdentityWarning("unique", ledger.ambiguousIds))
        val filtered = ledger.copy(query = "Πρώτη")
        assertFalse(filtered.isUniqueMutationTarget("same"))
        assertFalse(filtered.isUniqueMutationTarget("not-present"))
    }
}
