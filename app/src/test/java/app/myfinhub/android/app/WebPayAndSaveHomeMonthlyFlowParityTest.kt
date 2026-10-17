package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Production Home must consume Web Dashboard's operational selector, not raw monthlyFlow. */
class WebPayAndSaveHomeMonthlyFlowParityTest {
    @Test fun payAndSaveAppearsAsSavingsWithoutInflatingHomeExpenses() {
        val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{
            "accounts":[{"id":"daily","kind":"cash","cashRole":"daily","name":"Μετρητά"}],
            "snapshots":[{"date":"2026-10-01","balances":{"daily":300}}],
            "transactions":[
              {"id":"salary","type":"income","date":"2026-10-05","amount":100,
                "note":"Μισθός","accountId":"daily"},
              {"id":"pay-save","type":"expense","date":"2026-10-06","amount":15,
                "note":"Pay & Save","accountId":"daily"},
              {"id":"food","type":"expense","date":"2026-10-07","amount":20,
                "note":"Φαγητό","accountId":"daily"}
            ]}
           ,"state":{"settings":{},"events":[]}}
        """.trimIndent()).jsonObject)
        val product = projectCanonicalProduct(document, LocalDate.parse("2026-10-17"))
        assertEquals(100.0, product.homeState.monthFlow.income, 0.001)
        assertEquals(20.0, product.homeState.monthFlow.expense, 0.001)
        assertEquals(15.0, product.homeState.monthFlow.saving, 0.001)
        // Raw ledger remains available for immutable finance accounting.
        assertEquals(35.0, document.monthlyFlow("2026-10", "2026-10-17").expense, 0.001)
    }
}
