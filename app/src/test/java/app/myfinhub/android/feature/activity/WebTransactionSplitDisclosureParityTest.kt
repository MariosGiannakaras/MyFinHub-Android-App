package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web TransactionSplitDetails/categoryPath goldens: historic source is read-only. */
class WebTransactionSplitDisclosureParityTest {
    @Test fun labelUsesTrimmedWebLabelOrSourceTaxonomyPath() {
        val labeled = ActivitySplitPart("  Πρωινό  ", "Τρόφιμα", "Καφές", 8.01)
        val unlabeled = ActivitySplitPart("  ", "Μεταφορές", "Εισιτήριο", 21.99)
        assertEquals("Πρωινό", labeled.displayLabel)
        assertEquals("Τρόφιμα › Καφές", labeled.categoryPath)
        assertEquals("Μεταφορές › Εισιτήριο", unlabeled.displayLabel)
        assertEquals("Μεταφορές › Εισιτήριο", unlabeled.categoryPath)
    }

    @Test fun emptyCategoryFallsBackToWebOtherButHistoricalWhitespaceIsPreserved() {
        val empty = ActivitySplitPart("", "", "Αγνωστο", 0.01)
        val whitespace = ActivitySplitPart("", "   ", "Ετικέτα", 0.02)
        assertEquals("Άλλο", empty.categoryPath)
        assertEquals("Άλλο", empty.displayLabel)
        assertEquals("   › Ετικέτα", whitespace.categoryPath)
        assertEquals("   › Ετικέτα", whitespace.displayLabel)
    }

    @Test fun twoPartReadOnlyDisclosureDoesNotDistortTotalOrCreateNewEvents() {
        val parts = listOf(
            ActivitySplitPart("Σνακ", "Τρόφιμα", "Καφές", 8.01),
            ActivitySplitPart("", "Μεταφορές", "Εισιτήριο", 21.99),
        )
        val item = ActivityItem(
            id = "split-source", dateLabel = "25 Οκτ",
            kind = ActivityKind.EXPENSE, title = "Αγορά",
            subtitle = "", amount = -30.0, accountLabel = "Μετρητά",
            category = "Διαχωρισμός", canonicalKind = "split",
            canonicalSourceOrdinal = 7, splitParts = parts,
        )
        assertEquals(listOf(8.01, 21.99), item.splitParts.map { it.amount })
        assertEquals(30.0, item.splitParts.sumOf { it.amount }, 0.000001)
        assertEquals(-30.0, item.amount, 0.000001)
        assertEquals(parts, item.splitParts)
        assertTrue(item.webLedgerDisplayTitle().isNotBlank())
        assertFalse(item.pendingSync)
    }
}
