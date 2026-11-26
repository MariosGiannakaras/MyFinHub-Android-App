package app.myfinhub.android.feature.receipt

import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.security.PinAttemptStatus
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.canSubmitDraft
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReceiptParserTest {
    @Test
    fun externalReceiptResultMayResumeOnlyForSameOwnerAfterLock() {
        val session = AuthSession(
            accessToken = "synthetic-access", refreshToken = "synthetic-refresh",
            expiresAtEpochSeconds = 1000L, userId = "owner-a", assuranceLevel = AssuranceLevel.AAL2,
        )
        assertTrue(mayKeepReceiptRequest("owner-a", AuthShellUiState.Locked(
            session, false, PinAttemptStatus(true, 5),
        )))
        assertTrue(mayKeepReceiptRequest("owner-a", AuthShellUiState.Ready(session)))
        assertFalse(mayKeepReceiptRequest("owner-a", AuthShellUiState.Ready(session.copy(userId = "owner-b"))))
        assertFalse(mayKeepReceiptRequest("owner-a", AuthShellUiState.Login()))
    }

    @Test
    fun greekReceiptDoesNotMistakeVatCashAndSubtotalForTotal() {
        val receipt = parseReceiptText("""
            ΚΑΤΑΣΤΗΜΑ ΔΟΚΙΜΗ
            ΑΠΟΔΕΙΞΗ ΛΙΑΝΙΚΗΣ
            26/11/2026
            ΓΑΛΑ 2,30
            ΨΩΜΙ 1,70
            ΥΠΟΣΥΝΟΛΟ 4,00
            ΦΠΑ 0,50
            ΣΥΝΟΛΟ 4,00 €
            ΜΕΤΡΗΤΑ 20,00
            ΡΕΣΤΑ 16,00
        """.trimIndent())
        assertEquals("ΚΑΤΑΣΤΗΜΑ ΔΟΚΙΜΗ", receipt.merchant)
        assertEquals(LocalDate.of(2026, 11, 26), receipt.date)
        assertEquals(400L, receipt.totalCents)
        assertEquals(ReceiptCurrency.EUR, receipt.currency)
        assertEquals(listOf(230L, 170L), receipt.items.map { it.cents })
        assertEquals(2, reviewedReceiptSplit(receipt.items, receipt.totalCents, true).size)
    }

    @Test
    fun ambiguousTotalsInvalidDatesAndForeignCurrencyAreNotTrusted() {
        assertNull(parseReceiptText("STORE\nTOTAL 10.00 EUR\nAMOUNT DUE 11.00").totalCents)
        assertNull(parseReceiptText("STORE\n02/31/2026\nTOTAL 10.00").date)
        assertNull(parseReceiptText("STORE\nTAX 2.00\nCASH 50.00").totalCents)
        assertEquals(ReceiptCurrency.FOREIGN, parseReceiptText("TOTAL 12.34 USD").currency)
        assertEquals(ReceiptCurrency.UNKNOWN, parseReceiptText("TOTAL 12.34").currency)
        assertEquals(123456L, parseReceiptText("TOTAL 1.234,56 EUR").totalCents)
        assertEquals(123456L, parseReceiptText("TOTAL 1,234.56 EUR").totalCents)
        assertEquals(1234L, parseReceiptText("TOTAL\n12,34 €").totalCents)
    }

    @Test
    fun splitRequiresReviewedPositiveExactCentsAndNativePartCount() {
        val parts = listOf(ReceiptLine("Γάλα", 230), ReceiptLine("Ψωμί", 170))
        assertTrue(reviewedReceiptSplit(parts, 400, false).isEmpty())
        assertTrue(reviewedReceiptSplit(parts, 401, true).isEmpty())
        assertTrue(reviewedReceiptSplit(parts + ReceiptLine("bad", 0), 400, true).isEmpty())
        assertTrue(reviewedReceiptSplit(List(13) { ReceiptLine("item", 100) }, 1300, true).isEmpty())
        assertNull(receiptReviewedCents("1.234"))
        assertNull(receiptReviewedCents("-1"))
    }

    @Test
    fun exactNearbyPurchaseMatchesAreAdvisoryNotTransfersOrDifferentCents() {
        fun row(id: String, kind: String, date: String, cents: Double) = ActivityItem(
            id = id, dateLabel = date, kind = ActivityKind.EXPENSE,
            title = "STORE", subtitle = "", amount = -cents, accountLabel = "Cash",
            category = "Other", rawDate = date, note = "STORE",
            canonicalKind = kind, financeExpenseContribution = cents,
        )
        val rows = listOf(
            row("near", "expense", "2026-11-25", 12.34),
            row("credit", "card_purchase", "2026-11-26", 12.34),
            row("transfer", "transfer", "2026-11-26", 12.34),
            row("different", "expense", "2026-11-26", 12.35),
            row("far", "expense", "2026-11-20", 12.34),
        )
        assertEquals(listOf("credit", "near"), receiptPurchaseMatches(rows, 1234, LocalDate.of(2026, 11, 26), "store").map { it.id })
        assertTrue(receiptPurchaseMatches(rows, null, LocalDate.of(2026, 11, 26), "store").isEmpty())
    }

    @Test
    fun reviewedReceiptOnlyPrefillsDraftAndRequiresExplicitCategorySelection() {
        val initial = QuickEntryUiState()
        val reviewed = ReceiptPrefill("4.00", "2026-11-26", "Store")
        val draft = reduceQuickEntry(initial, QuickEntryAction.ReceiptPrefilled(reviewed))
        assertEquals("4.00", draft.amountText)
        assertEquals("Store", draft.note)
        assertTrue(draft.dirty)
        assertTrue(draft.receiptDraft)
        assertFalse(draft.persisted)
        assertFalse(draft.pendingSync)
        assertFalse(draft.canSubmitDraft())
        val categorized = reduceQuickEntry(draft, QuickEntryAction.CategoryChanged("Τρόφιμα"))
        assertTrue(categorized.canSubmitDraft())
        assertFalse(reduceQuickEntry(categorized, QuickEntryAction.Reset).receiptDraft)

        val split = reviewed.copy(splitLines = listOf(
            ReceiptPrefillLine("A", "2.30"), ReceiptPrefillLine("B", "1.70"),
        ))
        val splitDraft = reduceQuickEntry(initial, QuickEntryAction.ReceiptPrefilled(split))
        assertEquals(QuickEntryKind.SPLIT, splitDraft.kind)
        assertEquals(listOf("", ""), splitDraft.splitParts.map { it.category })
        assertFalse(splitDraft.canSubmitDraft())
        assertEquals(initial, reduceQuickEntry(initial, QuickEntryAction.ReceiptPrefilled(split.copy(amountText = "4.01"))))
    }
}
