package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web savings.ts operationalMonthlyFlow and selectors.selectMonthlyFlow vectors. */
class WebOperationalMonthlyFlowParityTest {
    internal val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"cash","kind":"cash"}],
        "snapshots":[{"date":"2026-09-01","balances":{"cash":1000}}],
        "transactions":[
          {"id":"september-save","date":"2026-09-30","type":"expense","amount":6,
           "note":"Pay & Save","accountId":"cash"},
          {"id":"salary","date":"2026-10-05","type":"income","amount":200,
           "note":"Μισθός","accountId":"cash"},
          {"id":"rounding-expense","date":"2026-10-10","type":"expense","amount":15,
           "note":"Pay & Save","accountId":"cash"},
          {"id":"rounding-income","date":"2026-10-12","type":"income","amount":7,
           "note":"Pay Save","accountId":"cash"},
          {"id":"ordinary-expense","date":"2026-10-13","type":"expense","amount":40,
           "note":"Λογαριασμός","accountId":"cash"},
          {"id":"reviewed-saving","date":"2026-10-14","type":"expense","amount":9,
           "note":"Pay & Save","accountId":"cash"},
          {"id":"future-saving","date":"2026-10-22","type":"expense","amount":200,
           "note":"Pay & Save","accountId":"cash"}
        ]},
        "state":{
          "settings":{},
          "reviewDecisions":{"reviewed-saving":{
            "status":"confirmed","semanticKind":"saving_cash_offset"
          }},
          "events":[{"id":"explicit","date":"2026-10-15","kind":"saving_cash_offset",
            "amount":11,"savingAmount":11,"savingSource":"cash_offset"}]
        }
      }
    """.trimIndent()).jsonObject)

    @Test fun historicalPayAndSaveNeverInflatesOperationalExpenseOrIncome() {
        val base = document.monthlyFlow("2026-10", "2026-10-17")
        assertEquals(207.0, base.income, 0.001)
        assertEquals(55.0, base.expense, 0.001)
        assertEquals(20.0, base.saving, 0.001)
        val web = document.canonicalOperationalMonthlyFlow("2026-10", "2026-10-17")
        assertEquals(200.0, web.income, 0.001)
        assertEquals(40.0, web.expense, 0.001)
        assertEquals(42.0, web.saving, 0.001)
        assertEquals(0.0, web.refunds, 0.001)
        assertEquals(160.0, web.net, 0.001)
        // A confirmed review counts its savings once, not both via review and label.
        assertEquals(document.canonicalSavingsBreakdown("2026-10","2026-10-17").total,
            web.saving, 0.001)
    }

    @Test fun historicalCutoffMatchesWebCurrentMonthOnlyRule() {
        val beforeSaving = document.canonicalOperationalMonthlyFlow("2026-10", "2026-10-09")
        assertEquals(200.0, beforeSaving.income, 0.001)
        assertEquals(0.0, beforeSaving.expense, 0.001)
        assertEquals(0.0, beforeSaving.saving, 0.001)
        val fullMonth = document.canonicalOperationalMonthlyFlow("2026-10")
        assertEquals(200.0, fullMonth.income, 0.001)
        assertEquals(40.0, fullMonth.expense, 0.001)
        assertEquals(242.0, fullMonth.saving, 0.001)
        // Passing a later-month asOf does not truncate an earlier report month.
        val september = document.canonicalOperationalMonthlyFlow("2026-09", "2026-10-17")
        assertEquals(0.0, september.expense, 0.001)
        assertEquals(6.0, september.saving, 0.001)
    }
    @Test fun refundHeavyPayAndSaveUsesRawSignedExpenseForNet() {
        val signed = CanonicalFinanceDocument(Json.parseToJsonElement("""
           {"seed":{"transactions":[
             {"id":"pay-save","date":"2026-10-03","type":"expense",
              "amount":15,"note":"Pay & Save"}
           ]},"state":{"events":[
             {"id":"salary","kind":"income","date":"2026-10-05","amount":100},
             {"id":"refund","kind":"refund","date":"2026-10-06","amount":90}
           ]}}
        """.trimIndent()).jsonObject)
        val raw = signed.monthlyFlow("2026-10")
        assertEquals(175.0, raw.net, 0.001)
        assertEquals(0.0, raw.expense, 0.001)
        val operational = signed.canonicalOperationalMonthlyFlow("2026-10")
        assertEquals(100.0, operational.income, 0.001)
        assertEquals(0.0, operational.expense, 0.001)
        assertEquals(15.0, operational.saving, 0.001)
        assertEquals(90.0, operational.refunds, 0.001)
        assertEquals(190.0, operational.net, 0.001)
        assertEquals(operational, signed.canonicalOperationalFlowBetween("2026-10-01", "2026-10-31"))
    }
}
