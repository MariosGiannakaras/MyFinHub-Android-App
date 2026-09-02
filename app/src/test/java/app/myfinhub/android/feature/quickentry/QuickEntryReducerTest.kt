package app.myfinhub.android.feature.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryReducerTest {
    @Test
    fun ordinaryEntry_rejectsMissingAmountButAllowsBlankDescription() {
        val invalid = reduceQuickEntry(
            QuickEntryUiState(note = ""),
            QuickEntryAction.Save,
        )
        assertEquals("Βάλε ποσό μεγαλύτερο από μηδέν.", invalid.validationMessage)

        val valid = reduceQuickEntry(
            QuickEntryUiState(amountText = "12,50", note = ""),
            QuickEntryAction.Save,
        )
        assertNull(valid.validationMessage)
        assertTrue(valid.savedSummary.orEmpty().contains("Έξοδο"))
        assertFalse(valid.persisted)
    }

    @Test
    fun withdrawal_requiresCashDestination() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.WITHDRAWAL,
                amountText = "50",
                fromAccountId = "acc-main",
                toAccountId = "acc-save",
            ),
            QuickEntryAction.Save,
        )

        assertEquals("Η ανάληψη πρέπει να καταλήγει σε λογαριασμό μετρητών.", result.validationMessage)
    }

    @Test
    fun saving_requiresSavingsDestination() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.SAVING,
                amountText = "50",
                fromAccountId = "acc-main",
                toAccountId = "acc-cash",
            ),
            QuickEntryAction.Save,
        )

        assertEquals("Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης.", result.validationMessage)
    }

    @Test
    fun lending_requiresPersonAndValidReturnChronology() {
        val missingPerson = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.LENDING,
                amountText = "30",
                person = "",
            ),
            QuickEntryAction.Save,
        )
        assertEquals("Συμπλήρωσε το πρόσωπο για τα δανεικά.", missingPerson.validationMessage)

        val earlyReturn = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.LENDING,
                amountText = "30",
                dateText = "2026-09-02",
                person = "Άννα",
                expectedReturnDateText = "2026-09-01",
            ),
            QuickEntryAction.Save,
        )
        assertEquals(
            "Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης.",
            earlyReturn.validationMessage,
        )
    }

    @Test
    fun reconciliation_requiresActualBalanceInsteadOfTransactionAmount() {
        val invalid = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.RECONCILIATION,
                amountText = "999",
                actualBalanceText = "",
            ),
            QuickEntryAction.Save,
        )
        assertEquals("Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο.", invalid.validationMessage)

        val valid = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.RECONCILIATION,
                amountText = "",
                actualBalanceText = "1035,25",
            ),
            QuickEntryAction.Save,
        )
        assertNull(valid.validationMessage)
        assertTrue(valid.savedSummary.orEmpty().contains("1035,25"))
    }

    @Test
    fun split_derivesTotalFromActualCategorizedParts() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.SPLIT,
                amountText = "999",
                splitParts = listOf(
                    QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10,10"),
                    QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "4,90"),
                ),
            ),
            QuickEntryAction.Save,
        )

        assertNull(result.validationMessage)
        assertEquals(15.0, result.amount ?: 0.0, 0.001)
        assertTrue(result.savedSummary.orEmpty().contains("15"))
        assertTrue(result.savedSummary.orEmpty().contains("2 μέρη"))
    }

    @Test
    fun split_rejectsInvalidPartAmount() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.SPLIT,
                splitParts = listOf(
                    QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10"),
                    QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "0"),
                ),
            ),
            QuickEntryAction.Save,
        )

        assertEquals("Το ποσό στο μέρος 2 πρέπει να είναι θετικό.", result.validationMessage)
    }

    @Test
    fun changingDraftMarksDirtyAndResetClearsDraft() {
        val changed = reduceQuickEntry(
            QuickEntryUiState(dirty = false),
            QuickEntryAction.AmountChanged("42"),
        )
        assertTrue(changed.dirty)

        val reset = reduceQuickEntry(changed, QuickEntryAction.Reset)
        assertFalse(reset.dirty)
        assertEquals("", reset.amountText)
        assertNull(reset.validationMessage)
        assertNull(reset.savedSummary)
    }

    @Test
    fun selectingIncomeUsesIncomeDefaultAndTaxonomy() {
        val state = QuickEntryUiState(
            accountId = "acc-cash",
            category = "Τρόφιμα",
            defaultIncomeAccountId = "acc-main",
        )
        val result = reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.INCOME))

        assertEquals("acc-main", result.accountId)
        assertEquals("Μισθός", result.category)
        assertTrue(result.dirty)
    }
}
