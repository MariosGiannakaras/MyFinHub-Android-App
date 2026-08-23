package app.myfinhub.android.feature.quickentry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryReducerTest {
    @Test
    fun saveRejectsMissingAmount() {
        val result = reduceQuickEntry(
            QuickEntryUiState(note = "Καφές"),
            QuickEntryAction.Save,
        )

        assertEquals("Βάλε ποσό μεγαλύτερο από μηδέν.", result.validationMessage)
        assertNull(result.savedSummary)
        assertFalse(result.persisted)
    }

    @Test
    fun transferRequiresDestination() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.TRANSFER,
                amountText = "50",
                note = "Μεταφορά",
                destination = "",
            ),
            QuickEntryAction.Save,
        )

        assertEquals("Διάλεξε προορισμό μεταφοράς.", result.validationMessage)
        assertFalse(result.persisted)
    }

    @Test
    fun validSplitCreatesPreviewWithoutClaimingPersistence() {
        val result = reduceQuickEntry(
            QuickEntryUiState(
                kind = QuickEntryKind.SPLIT,
                amountText = "36,50",
                note = "Δείπνο",
                splitPeople = 3,
            ),
            QuickEntryAction.Save,
        )

        assertNull(result.validationMessage)
        assertTrue(result.savedSummary.orEmpty().contains("Μοίρασμα σε 3 μέρη"))
        assertEquals(36.5, result.amount ?: 0.0, 0.001)
        assertFalse(result.persisted)
    }
}
