package app.myfinhub.android.feature.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryReducerTest {
    @Test
    fun contextualPrimaryAccount_becomesSourceWhenChangingToTransferOrCardPayment() {
        val state = QuickEntryUiState().copy(
            kind = QuickEntryKind.EXPENSE,
            accountId = "acc-cash",
            fromAccountId = "acc-main",
        )

        val transfer = reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.TRANSFER))
        val cardPayment = reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT))

        assertEquals("acc-cash", transfer.fromAccountId)
        assertEquals("acc-cash", cardPayment.fromAccountId)
    }

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
    fun locallyCommittedPreview_isRecognizedAsAwaitingSyncAndEditingStartsANewDraft() {
        val preview = reduceQuickEntry(
            QuickEntryUiState(amountText = "5", note = "Καφές", dirty = true),
            QuickEntryAction.Save,
        )
        val locallyCommitted = preview.copy(persisted = false, dirty = false)

        assertTrue(locallyCommitted.awaitingSync)
        assertFalse(locallyCommitted.persisted)

        val edited = reduceQuickEntry(locallyCommitted, QuickEntryAction.AmountChanged("6"))
        assertFalse(edited.awaitingSync)
        assertTrue(edited.dirty)
        assertNull(edited.savedSummary)
    }

    @Test
    fun serverPersistedEntry_isNotReportedAsAwaitingSync() {
        val state = QuickEntryUiState(
            amountText = "5",
            savedSummary = "Έξοδο · 5 € · Καφές",
            persisted = true,
            pendingSync = false,
            dirty = false,
        )

        assertFalse(state.awaitingSync)
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
                amountText = "15,00",
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
                amountText = "10,00",
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
        assertFalse(reset.pendingSync)
        assertEquals("", reset.amountText)
        assertNull(reset.validationMessage)
        assertNull(reset.savedSummary)
    }

    @Test
    fun selectingIncomePreservesCompatibleAccountAndUsesIncomeTaxonomy() {
        val state = QuickEntryUiState(
            accountId = "acc-cash",
            category = "Τρόφιμα",
            defaultIncomeAccountId = "acc-main",
        )
        val result = reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.INCOME))

        assertEquals("acc-cash", result.accountId)
        assertEquals("Μισθός", result.category)
        assertTrue(result.dirty)
    }

    @Test
    fun allTwelveKinds_haveAValidCanonicalDraft() {
        QuickEntryKind.entries.forEach { kind ->
            val destination = when (kind) {
                QuickEntryKind.WITHDRAWAL -> "acc-cash"
                QuickEntryKind.SAVING -> "acc-save"
                else -> "acc-save"
            }
            val category = if (kind == QuickEntryKind.INCOME) "Μισθός" else "Τρόφιμα"
            val draft = QuickEntryUiState(
                kind = kind,
                amountText = if (kind == QuickEntryKind.RECONCILIATION) "" else "15,00",
                category = category,
                fromAccountId = "acc-main",
                toAccountId = destination,
                person = "Άννα",
                actualBalanceText = "1250,40",
                splitParts = listOf(
                    QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10,10"),
                    QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "4,90"),
                ),
            )

            val result = reduceQuickEntry(draft, QuickEntryAction.Save)

            assertNull("${kind.name}: ${result.validationMessage}", result.validationMessage)
            assertTrue("${kind.name} did not create a preview", result.savedSummary != null)
        }
    }

    @Test
    fun kindSwitch_preservesCompatibleValuesAndClearsIncompatibleDraftFields() {
        val lending = QuickEntryUiState(
            kind = QuickEntryKind.LENDING,
            amountText = "42,50",
            dateText = "2026-09-13",
            note = "Κοινό δείπνο",
            accountId = "acc-cash",
            category = "Τρόφιμα",
            subcategory = "Καφές",
            person = "Άννα",
            expectedReturnDateText = "2026-09-20",
        )

        val repayment = reduceQuickEntry(lending, QuickEntryAction.SelectKind(QuickEntryKind.REPAYMENT))
        assertEquals("42,50", repayment.amountText)
        assertEquals("2026-09-13", repayment.dateText)
        assertEquals("Κοινό δείπνο", repayment.note)
        assertEquals("acc-cash", repayment.accountId)
        assertEquals("Τρόφιμα", repayment.category)
        assertEquals("Καφές", repayment.subcategory)
        assertEquals("Άννα", repayment.person)
        assertEquals("", repayment.expectedReturnDateText)

        val reconciliation = reduceQuickEntry(
            repayment.copy(actualBalanceText = "stale"),
            QuickEntryAction.SelectKind(QuickEntryKind.RECONCILIATION),
        )
        assertEquals("", reconciliation.amountText)
        assertEquals("", reconciliation.person)
        assertEquals("", reconciliation.expectedReturnDateText)
        assertEquals("", reconciliation.actualBalanceText)

        val expense = reduceQuickEntry(
            reconciliation.copy(actualBalanceText = "1000"),
            QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE),
        )
        assertEquals("", expense.actualBalanceText)
    }

    @Test
    fun transferKindSwitch_keepsCompatibleRouteAndRepairsRestrictedDestination() {
        val transfer = QuickEntryUiState(
            kind = QuickEntryKind.TRANSFER,
            amountText = "20",
            fromAccountId = "acc-main",
            toAccountId = "acc-save",
        )

        val saving = reduceQuickEntry(transfer, QuickEntryAction.SelectKind(QuickEntryKind.SAVING))
        assertEquals("acc-main", saving.fromAccountId)
        assertEquals("acc-save", saving.toAccountId)
        assertEquals("20", saving.amountText)

        val withdrawal = reduceQuickEntry(saving, QuickEntryAction.SelectKind(QuickEntryKind.WITHDRAWAL))
        assertEquals("acc-main", withdrawal.fromAccountId)
        assertEquals("acc-cash", withdrawal.toAccountId)
        assertEquals("20", withdrawal.amountText)
    }

    @Test
    fun split_requiresDeclaredTotalToMatchPartsInExactCurrencyCents() {
        val base = QuickEntryUiState(
            kind = QuickEntryKind.SPLIT,
            amountText = "15,00",
            splitParts = listOf(
                QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10,10"),
                QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "4,90"),
            ),
        )

        val valid = reduceQuickEntry(base, QuickEntryAction.Save)
        assertNull(valid.validationMessage)
        assertEquals(0.0, valid.splitRemaining ?: Double.NaN, 0.0)

        val mismatched = reduceQuickEntry(base.copy(amountText = "15,01"), QuickEntryAction.Save)
        assertEquals("Τα μέρη πρέπει να ισούνται ακριβώς με το συνολικό ποσό.", mismatched.validationMessage)

        val tooPrecise = reduceQuickEntry(base.copy(amountText = "15,001"), QuickEntryAction.Save)
        assertEquals("Βάλε ποσό μεγαλύτερο από μηδέν.", tooPrecise.validationMessage)
    }

    @Test
    fun selectingSplit_startsFreshAllocationButKeepsSharedDateAndNote() {
        val state = QuickEntryUiState(
            kind = QuickEntryKind.EXPENSE,
            amountText = "22",
            dateText = "2026-09-13",
            note = "Απόδειξη",
            splitParts = listOf(
                QuickEntrySplitPartDraft("stale-1", category = "Τρόφιμα", amountText = "22"),
                QuickEntrySplitPartDraft("stale-2", category = "Τρόφιμα", amountText = "1"),
            ),
        )

        val split = reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.SPLIT))

        assertEquals("", split.amountText)
        assertEquals("2026-09-13", split.dateText)
        assertEquals("Απόδειξη", split.note)
        assertEquals(listOf("part-1", "part-2"), split.splitParts.map { it.id })
        assertTrue(split.splitParts.all { it.amountText.isBlank() })
    }


    @Test
    fun selectingTheCurrentKind_doesNotCreateOrDestroyDraftState() {
        val state = QuickEntryUiState(
            kind = QuickEntryKind.SPLIT,
            amountText = "15,00",
            splitParts = listOf(
                QuickEntrySplitPartDraft("p1", category = "Τρόφιμα", amountText = "10,10"),
                QuickEntrySplitPartDraft("p2", category = "Μετακίνηση", amountText = "4,90"),
            ),
            dirty = false,
        )

        assertEquals(state, reduceQuickEntry(state, QuickEntryAction.SelectKind(QuickEntryKind.SPLIT)))
    }

}
