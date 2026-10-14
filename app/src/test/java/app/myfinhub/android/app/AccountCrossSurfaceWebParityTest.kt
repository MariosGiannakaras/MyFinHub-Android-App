package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import app.myfinhub.android.feature.money.walletAccountGroup
import app.myfinhub.android.feature.money.walletAvailableTotal
import app.myfinhub.android.feature.money.WalletAccountGroup
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountCrossSurfaceWebParityTest {
    @Test
    fun bankClassifiedSavingsAndCashReserveRespectHomeWalletAndQuickEntrySemantics() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
         {"seed":{"accounts":[
          {"id":"cash","name":"Μετρητά","kind":"cash","cashRole":"daily"},
          {"id":"reserve","name":"Καβάτζα","kind":"cash","cashRole":"reserve"},
          {"id":"salary","name":"Salary","kind":"bank","bankAccountCategory":"payroll","providerId":"piraeus"},
          {"id":"save","name":"Savings","kind":"bank","bankAccountCategory":"savings","providerId":"piraeus"},
          {"id":"other","name":"Other bank","kind":"bank","bankAccountCategory":"current"}
         ],"snapshots":[{"date":"2026-10-01","balances":{
          "cash":150,"reserve":700,"salary":1050,"save":3200,"other":1400
         }}]},
         "state":{"settings":{"defaultExpenseAccount":"cash"}}}
        """.trimIndent()).jsonObject)
        val projection = projectCanonicalProduct(doc, LocalDate.of(2026,10,14))
        assertEquals(1200.0, projection.homeState.liquidTotal, 0.001)
        assertEquals(listOf("cash","salary","save"),
            projection.homeState.accounts.filter { it.primaryRole != null }.map { it.id })
        val money = projection.moneyState
        val saving = money.accounts.single { it.id == "save" }
        assertEquals(WalletAccountGroup.SAVINGS, walletAccountGroup(saving))
        assertEquals(2600.0, walletAvailableTotal(money), 0.001)
        val projectedQuick = projection.quickEntryState
        assertEquals("save", projectedQuick.toAccountId)
        val savingDraft = reduceQuickEntry(projectedQuick, QuickEntryAction.SelectKind(QuickEntryKind.SAVING))
        assertEquals("salary", savingDraft.fromAccountId)
        assertEquals("save", savingDraft.toAccountId)
        assertTrue(savingDraft.accounts.single { it.id == "save" }.isSavingsDestination)
        assertTrue(savingDraft.accounts.single { it.id == "salary" }.isSavingContributionSource)
    }
}
