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

    @Test fun editableLegacyActivityAccountsRejectAmbiguousAndBlankIds() {
        val options = listOf(
            ActivityAccountOption("cash", "Μετρητά A"),
            ActivityAccountOption("cash", "Μετρητά B"),
            ActivityAccountOption("reserve", "Αποταμίευση"),
            ActivityAccountOption("  ", "Ιστορικός κενός"),
        )
        assertEquals(
            listOf("reserve"), selectableLegacyActivityAccounts(options).map { it.id },
        )
    }

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
    @Test fun legacyAccountLabelsDisambiguateSameNamedSourcesWithoutChangingIds() {
        val options = listOf(
            ActivityAccountOption("bank-00000001", "Μετρητά"),
            ActivityAccountOption("wallet-00000002", "μετρητά"),
            ActivityAccountOption("salary-00000003", "Μισθοδοσία"),
            ActivityAccountOption("same", "Πρώτη"),
            ActivityAccountOption("same", "Δεύτερη"),
            ActivityAccountOption(" ", "Κενός"),
        )
        assertEquals(
            listOf(
                "bank-00000001" to "Μετρητά · 00000001",
                "wallet-00000002" to "μετρητά · 00000002",
                "salary-00000003" to "Μισθοδοσία",
            ),
            selectableLegacyActivityAccountChoices(options),
        )
    }

    @Test fun collidingLastEightIdCharactersUseFullIdentifierToStayDistinct() {
        val options = listOf(
            ActivityAccountOption("bank-duplicate", "Κοινός"),
            ActivityAccountOption("cash-duplicate", "Κοινός"),
        )
        assertEquals(
            listOf(
                "bank-duplicate" to "Κοινός · bank-duplicate",
                "cash-duplicate" to "Κοινός · cash-duplicate",
            ),
            selectableLegacyActivityAccountChoices(options),
        )
    }

}
