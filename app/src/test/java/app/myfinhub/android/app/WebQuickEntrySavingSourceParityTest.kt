package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalTransactionEntryDraft
import app.myfinhub.android.core.data.canonicalSavingsBreakdown
import app.myfinhub.android.core.data.createCanonicalTransactionEntryMutation
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web SavingsPage source routing plus canonical mutation/offline replay goldens. */
class WebQuickEntrySavingSourceParityTest {
    private val date = "2026-10-15"
    private val now = "2026-10-15T10:00:00Z"
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[
            {"id":"payroll","name":"Μισθοδοσία","kind":"bank","bankAccountCategory":"payroll"},
            {"id":"save","name":"Αποταμίευση","kind":"savings"}
        ],"snapshots":[{"date":"2026-10-01","balances":{"payroll":200,"save":30}}],
        "transactions":[]},"state":{"settings":{},"events":[]}}
    """.trimIndent()).jsonObject)

    @Test fun nativeSavingEntryAlwaysWritesManualTransferSourceWithIndependentUserNote() {
        val state = QuickEntryUiState(
            kind = QuickEntryKind.SAVING,
            amountText = "12,50",
            dateText = date,
            note = "Δώρο γενεθλίων",
            fromAccountId = "payroll",
            toAccountId = "save",
        )
        val intent = createQuickEntryCanonicalMutation(document, state, "save-new", now)
        val first = intent.apply(document)
        val second = intent.apply(first)
        assertEquals(first.raw, second.raw)
        val event = first.state["events"] as kotlinx.serialization.json.JsonArray
        val new = event.first() as JsonObject
        assertEquals("manual_transfer", (new["savingSource"] as kotlinx.serialization.json.JsonPrimitive).content)
        assertEquals("Δώρο γενεθλίων", (new["note"] as kotlinx.serialization.json.JsonPrimitive).content)
        val savings = first.canonicalSavingsBreakdown("2026-10",date)
        assertEquals(12.50, savings.bySource.getValue("manual_transfer"),0.001)
        assertEquals(0.0, savings.bySource.getValue("cash_offset"),0.001)
        assertEquals(0.0, savings.bySource.getValue("pay_and_save"),0.001)
        assertEquals("manual_transfer",savings.rows.single().source)
        assertEquals(12.50,first.monthlyFlow("2026-10",date).saving,0.001)
        assertEquals(0.0,first.monthlyFlow("2026-10",date).expense,0.001)
    }

    @Test fun userPayAndSaveWordsDoNotOverrideExplicitTransferProvenance() {
        val intent = createQuickEntryCanonicalMutation(document,
            QuickEntryUiState(
                kind=QuickEntryKind.SAVING,amountText="5",dateText=date,
                note="Pay & Save gift",fromAccountId="payroll",toAccountId="save",
            ),"save-note",now)
        val rows = intent.apply(document).canonicalSavingsBreakdown("2026-10",date)
        assertEquals("manual_transfer",rows.rows.single().source)
        assertEquals(5.0,rows.bySource.getValue("manual_transfer"),0.001)
    }

    @Test fun oldUnannotatedSavingEventsRetainLegacySourceInference() {
        val intent = createCanonicalTransactionEntryMutation(document,
            CanonicalTransactionEntryDraft(
                kind="saving_cash_offset",date=date,amount=7.0,
                note="Αποταμίευση",fromAccountId="payroll",toAccountId="save",
            ),"legacy-native",now)
        val row = intent.apply(document).canonicalSavingsBreakdown("2026-10",date)
        assertEquals("cash_offset",row.rows.single().source)
        assertEquals(7.0,row.total,0.001)
    }

    @Test(expected=IllegalArgumentException::class)
    fun malformedSavingSourceCannotEnterCanonicalQueue() {
        createCanonicalTransactionEntryMutation(document,
            CanonicalTransactionEntryDraft(
                kind="saving_cash_offset",date=date,amount=7.0,
                fromAccountId="payroll",toAccountId="save",savingSource="unknown_source",
            ),"invalid",now)
    }

    @Test(expected=IllegalArgumentException::class)
    fun nonSavingEventCannotSmuggleSavingsSource() {
        createCanonicalTransactionEntryMutation(document,
            CanonicalTransactionEntryDraft(
                kind="income",date=date,amount=7.0,
                accountId="payroll",savingSource="manual_transfer",
            ),"invalid-income",now)
    }

    @Test fun explicitCashOffsetRemainsValidForOtherWebSavingFlows() {
        val first = createCanonicalTransactionEntryMutation(document,
            CanonicalTransactionEntryDraft(
                kind="saving_cash_offset",date=date,amount=10.0,
                fromAccountId="payroll",toAccountId="save",savingSource="cash_offset",
            ),"cash-offset",now).apply(document)
        val row = first.canonicalSavingsBreakdown("2026-10",date)
        assertEquals("cash_offset",row.rows.single().source)
        assertEquals(10.0,row.total,0.001)
        assertTrue(first.monthlyFlow("2026-10",date).saving>0.0)
    }
}
