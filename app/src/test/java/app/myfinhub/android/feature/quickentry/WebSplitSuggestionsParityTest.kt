package app.myfinhub.android.feature.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSplitSuggestionsParityTest {
    @Test fun webParsesMultilineEuroAmountsAndSemanticKindsWithoutMutatingForm() {
        val input="""
        Τρόφιμα: 12,50 € + 2,50 €
        Καύσιμα: 15 €
        ΕΠΙΣΤΡΟΦΗ 10 €
        Pay&Save 3 €
        ΔΩΡΟ: 20 €
        ΜΕΤΑΦΟΡΑ 4 €
        ΔΙΟΡΘΩΣΗ 1 €
        """.trimIndent()
        val rows=canonicalWebSplitSuggestions(input,"Τρόφιμα")
        assertEquals(listOf("expense","expense","refund","saving","income","transfer","reconciliation"),
            rows.map { it.kind })
        assertEquals(listOf("15","15","10","3","20","4","1"),rows.map { it.amountText })
        assertEquals("Τρόφιμα",rows[0].label)
        assertEquals("Καύσιμα",rows[1].label)
    }

    @Test fun exactExpenseSuggestionAppliesOnlyOnExplicitClickAndCentsMatch() {
        val draft=QuickEntryUiState(
            kind=QuickEntryKind.SPLIT,
            amountText="27.50",
            note="Κρέας: 12,50 €\nΛαχανικά 15 €",
            category="Τρόφιμα",
        )
        assertEquals(2,draft.safeWebExpenseSplitSuggestions().size)
        assertTrue(draft.splitParts.all { it.amountText.isEmpty() })
        val accepted=reduceQuickEntry(draft,QuickEntryAction.ApplySuggestedExpenseSplit)
        assertEquals(listOf("12.5","15"),accepted.splitParts.map { it.amountText })
        assertEquals(listOf("Κρέας","Λαχανικά"),accepted.splitParts.map { it.label })
        assertEquals(27.5,accepted.splitTotal,0.001)
        assertTrue(accepted.dirty)
        assertTrue(draft.splitParts.all { it.amountText.isEmpty() })
    }

    @Test fun neverOverwriteEditedPartsOrDowncastRefundIncomeOrImbalancedNotes() {
        val basis=QuickEntryUiState(
            kind=QuickEntryKind.SPLIT,amountText="20",
            category="Τρόφιμα",note="Μαγαζί 10 €\nΠρομήθεια 10 €",
        )
        val refund=basis.copy(note="Μαγαζί 10 €\nΕΠΙΣΤΡΟΦΗ 10 €")
        val mismatch=basis.copy(amountText="21")
        val manual=basis.copy(splitParts=basis.splitParts.mapIndexed { i,row ->
            if (i == 0) row.copy(amountText="5") else row
        })
        val wrongKind=basis.copy(kind=QuickEntryKind.INCOME)
        for (state in listOf(refund,mismatch,manual,wrongKind)) {
            assertTrue(state.safeWebExpenseSplitSuggestions().isEmpty())
            assertEquals(state,reduceQuickEntry(state,QuickEntryAction.ApplySuggestedExpenseSplit))
        }
    }

    @Test fun emptyOrMalformedSuggestionLinesCannotCreateSyntheticPayments() {
        assertTrue(canonicalWebSplitSuggestions("Καφές\nτίποτα").isEmpty())
        assertTrue(canonicalWebSplitSuggestions("Μικρό ποσό 0 €").isEmpty())
        assertTrue(canonicalWebSplitSuggestions("Σύνολο 20").isEmpty())
    }
}
