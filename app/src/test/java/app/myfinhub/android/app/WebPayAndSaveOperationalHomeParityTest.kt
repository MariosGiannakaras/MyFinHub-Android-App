package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalOperationalMonthlyFlow
import app.myfinhub.android.core.data.canonicalSavingsBreakdown
import app.myfinhub.android.core.data.monthlyFlow
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web selectors.ts -> savings.ts operationalMonthlyFlow is the Home contract. */
class WebPayAndSaveOperationalHomeParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"salary","kind":"bank","bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"salary":500}}],
        "recurring":[],"loans":[],"lending":[],
        "transactions":[
          {"id":"pay-save","date":"2026-10-04","type":"expense","amount":30,
            "accountId":"salary","category":"Αποταμίευση","note":"Pay & Save"},
          {"id":"grocery","date":"2026-10-05","type":"expense","amount":20,
            "accountId":"salary","category":"Τρόφιμα","note":"Groceries"},
          {"id":"reviewed","date":"2026-10-06","type":"expense","amount":10,
            "accountId":"salary","category":"Pay & Save","note":"Already confirmed"},
          {"id":"income-mechanism","date":"2026-10-07","type":"income","amount":5,
            "accountId":"salary","category":"Άλλο","note":"PAY SAVE"},
          {"id":"future-pay-save","date":"2026-10-29","type":"expense","amount":40,
            "accountId":"salary","category":"Άλλο","note":"Pay&Save"}
        ]
      },"state":{
        "reviewDecisions":{"reviewed":{"status":"confirmed",
          "semanticKind":"saving_cash_offset"}},
        "events":[
          {"id":"salary-income","date":"2026-10-02","kind":"income",
           "amount":100,"legs":[{"accountId":"salary","amount":100}]},
          {"id":"grocery-refund","date":"2026-10-05","kind":"refund",
           "amount":5,"category":"Τρόφιμα","legs":[{"accountId":"salary","amount":5}]},
          {"id":"saved-event","date":"2026-10-10","kind":"saving_cash_offset",
           "amount":7,"savingAmount":7,"savingSource":"pay_and_save","legs":[]}
        ],"scheduled":[],"settings":{}}
      }
    """.trimIndent()).jsonObject)

    @Test fun webDashboardReclassifiesLegacyPaySaveWithoutChangingCanonicalLedger() {
        val base = doc.monthlyFlow("2026-10","2026-10-17")
        assertEquals(105.0,base.income,0.001)
        assertEquals(55.0,base.expense,0.001)
        assertEquals(17.0,base.saving,0.001)
        val operation = doc.canonicalOperationalMonthlyFlow("2026-10","2026-10-17")
        assertEquals(100.0,operation.income,0.001)
        assertEquals(25.0,operation.expense,0.001)
        assertEquals(52.0,operation.saving,0.001)
        assertEquals(5.0,operation.refunds,0.001)
        assertEquals(75.0,operation.net,0.001)
        assertEquals(52.0,doc.canonicalSavingsBreakdown("2026-10","2026-10-17").total,0.001)
    }

    @Test fun currentHomeUsesOperationalFlowAndExcludesFuturePaySave() {
        val home = projectCanonicalProduct(doc,LocalDate.parse("2026-10-17")).homeState
        assertEquals(100.0,home.monthFlow.income,0.001)
        assertEquals(25.0,home.monthFlow.expense,0.001)
        assertEquals(52.0,home.monthFlow.saving,0.001)
        assertEquals(92.0,doc.canonicalOperationalMonthlyFlow("2026-10").saving,0.001)
        assertEquals(doc.canonicalOperationalMonthlyFlow("2026-10"),
          doc.canonicalOperationalMonthlyFlow("2026-10","2026-09-15"))
    }
}
