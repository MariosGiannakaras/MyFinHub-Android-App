package app.myfinhub.android.feature.receipt

import app.myfinhub.android.feature.activity.ActivityCategoryOption
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryCategoryOption
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.nio.ByteBuffer
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

/** Synthetic format vectors; never a real receipt or network request. */
internal fun syntheticReceiptQr(
    timestamp: String = "2611261037",
    amount: String = "18.35",
    sequence: String = "00000001",
): String = "https://www1.aade.gr/tameiakes/myweb/q1.php?SIG=" +
    "TEST0000001" + sequence + "0123456789ABCDEF0123456789ABCD" + timestamp + amount

class ReceiptQrTest {
    @Test
    fun acceptsDocumentedFieldsExactlyAndWithoutNetwork() {
        val parsed = requireNotNull(parseAadeReceiptQr(syntheticReceiptQr()))
        assertEquals(1835L, parsed.totalCents)
        assertEquals(LocalDateTime.of(2026, 11, 26, 10, 37), parsed.issuedAt)
        assertEquals(64, parsed.identity.length)
        assertEquals(1L, parseAadeReceiptQr(syntheticReceiptQr(amount = "0.01"))?.totalCents)
        assertEquals(99999999L, parseAadeReceiptQr(syntheticReceiptQr(amount = "999999.99"))?.totalCents)
    }

    @Test
    fun failsClosedOnInvalidGrammarDatesAmountsAndUnexpectedUrls() {
        val valid = syntheticReceiptQr()
        listOf(
            valid.replace("https:", "http:"),
            valid.replace("www1.aade.gr", "www1.aade.gr.evil.test"),
            valid.replace("www1.aade.gr", "evil.test@www1.aade.gr"),
            valid.replace("www1.aade.gr", "www1.aade.gr:443"),
            valid.replace("q1.php", "q2.php"), valid + "&x=1", valid + "#fragment",
            valid.replace("SIG=", "sig="), valid.replace("TEST0000001", "TEST000000"),
            valid.replace("0123456789ABCDEF", "NOTHEX56789ABCDE"),
            syntheticReceiptQr(timestamp = "2611311037"),
            syntheticReceiptQr(timestamp = "2611262437"),
            syntheticReceiptQr(timestamp = "2602291037"),
            syntheticReceiptQr(amount = "0.00"), syntheticReceiptQr(amount = "-1.00"),
            syntheticReceiptQr(amount = "18,35"), syntheticReceiptQr(amount = "18.351"),
            syntheticReceiptQr(amount = "1000000.00"), "1234567890123",
            "https://example.test/detail/receipt",
        ).forEach { assertNull(it, parseAadeReceiptQr(it)) }
    }

    @Test
    fun repeatedOneCodeIsUsableButConflictingFiscalCodesAreNot() {
        val one = syntheticReceiptQr()
        val different = syntheticReceiptQr(sequence = "00000002")
        assertEquals(ReceiptQrStatus.AADE, classifyReceiptQrs(listOf(one, one)).status)
        assertEquals(ReceiptQrStatus.AADE, classifyReceiptQrs(listOf(one, "https://example.test/rewards")).status)
        assertEquals(ReceiptQrStatus.AMBIGUOUS, classifyReceiptQrs(listOf(one, different)).status)
        assertEquals(ReceiptQrStatus.AMBIGUOUS, classifyReceiptQrs(listOf(one, syntheticReceiptQr(amount = "-1"))).status)
        assertEquals(ReceiptQrStatus.UNSUPPORTED, classifyReceiptQrs(listOf("https://example.test/receipt")).status)
        assertEquals(ReceiptQrStatus.NONE, classifyReceiptQrs(emptyList()).status)
    }

    @Test
    fun qrWinsButDisagreementAndCurrencyFenceRemainVisibleForReview() {
        val read = classifyReceiptQrs(listOf(syntheticReceiptQr()))
        val matching = mergeReceiptQr(parseReceiptText("STORE\n26/11/2026\nTOTAL 18.35"), read)
        assertEquals("STORE", matching.merchant)
        assertEquals(1835L, matching.totalCents)
        assertEquals(ReceiptCurrency.EUR, matching.currency)
        assertFalse(requireNotNull(matching.qrReview).requiresConfirmation)
        val disagreed = mergeReceiptQr(parseReceiptText("STORE\n25/11/2026\nTOTAL 19.35 EUR"), read)
        assertEquals(1835L, disagreed.totalCents)
        assertEquals(LocalDate.of(2026, 11, 26), disagreed.date)
        assertEquals(1935L, disagreed.qrReview?.ocrTotalCents)
        assertTrue(requireNotNull(disagreed.qrReview).requiresConfirmation)
        val ambiguous = mergeReceiptQr(parseReceiptText("TOTAL 18.35 EUR\nPAYABLE 19.35 EUR"), read)
        assertTrue(requireNotNull(ambiguous.qrReview).requiresConfirmation)
        assertTrue(requireNotNull(ambiguous.qrReview).ocrAmbiguous)
        val foreign = mergeReceiptQr(parseReceiptText("STORE\nTOTAL 18.35 USD"), read)
        assertEquals(ReceiptCurrency.FOREIGN, foreign.currency)
        assertTrue(requireNotNull(foreign.qrReview).requiresConfirmation)
        val unknown = mergeReceiptQr(parseReceiptText("STORE\nTOTAL 12.34 EUR"),
            classifyReceiptQrs(listOf("https://example.test/receipt")))
        assertEquals(1234L, unknown.totalCents)
        assertEquals("STORE", unknown.merchant)
    }

    @Test
    fun liveLumaDecoderHonorsCropStrideAndOffset() {
        val size = 320
        val code = QRCodeWriter().encode(syntheticReceiptQr(), BarcodeFormat.QR_CODE, size, size)
        val left = 7
        val top = 5
        val pixelStride = 2
        val rowStride = (size + left + 3) * pixelStride + 7
        val offset = 11
        val data = ByteArray(offset + (top + size + 4) * rowStride) { 255.toByte() }
        for (y in 0 until size) for (x in 0 until size) {
            data[offset + (top + y) * rowStride + (left + x) * pixelStride] =
                if (code[x, y]) 0.toByte() else 255.toByte()
        }
        val buffer = ByteBuffer.wrap(data).apply { position(offset) }
        val read = readReceiptQrLuma(buffer, rowStride, pixelStride, left, top, size, size)
        assertEquals(1835L, read.fiscal?.totalCents)
        assertEquals(offset, buffer.position())
        assertEquals(ReceiptQrStatus.NONE, readReceiptQrLuma(
            ByteBuffer.wrap(ByteArray(8)), rowStride, pixelStride, left, top, size, size,
        ).status)
    }

    @Test
    fun merchantSuggestionRequiresUnambiguousCurrentTaxonomyAndExplicitPrefill() {
        val option = ActivityCategoryOption("Food", listOf("Groceries"))
        fun row(category: String, subcategory: String, note: String = "TEST STORE") = ActivityItem(
            id = category, dateLabel = "01/11/2026", kind = ActivityKind.EXPENSE,
            title = note, subtitle = "", amount = -12.34, accountLabel = "Cash",
            category = category, subcategory = subcategory, note = note,
            canonicalKind = "expense", financeExpenseContribution = 12.34,
        )
        val matching = row("Food", "Groceries")
        val suggested = receiptCategorySuggestion(listOf(matching), listOf(option), "test store")
        assertEquals(ReceiptCategorySuggestion("Food", "Groceries"), suggested)
        assertNull(receiptCategorySuggestion(listOf(matching.copy(pendingSync = true)), listOf(option), "test store"))
        assertNull(receiptCategorySuggestion(listOf(matching), listOf(option), "another store"))
        assertNull(receiptCategorySuggestion(listOf(matching, row("Other", "")),
            listOf(option, ActivityCategoryOption("Other")), "test store"))
        val state = QuickEntryUiState(expenseCategories = listOf(QuickEntryCategoryOption("Food", listOf("Groceries"))))
        val value = ReceiptPrefill("18.35", "2026-11-26", "TEST STORE", categorySuggestion = suggested)
        val draft = reduceQuickEntry(state, QuickEntryAction.ReceiptPrefilled(value))
        assertEquals("Food", draft.category)
        assertEquals("Groceries", draft.subcategory)
        assertFalse(draft.persisted)
        assertFalse(draft.pendingSync)
        val stale = reduceQuickEntry(state, QuickEntryAction.ReceiptPrefilled(
            value.copy(categorySuggestion = ReceiptCategorySuggestion("Food", "Deleted")),
        ))
        assertEquals("", stale.category)
    }
}
