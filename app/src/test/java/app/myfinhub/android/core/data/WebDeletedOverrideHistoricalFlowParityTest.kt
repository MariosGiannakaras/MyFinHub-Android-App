package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Web src/lib/domain.ts historical golden cases: deletedSet, legacyDeltaAgainstSeed,
 * baseSnapshot, monthlyFlow and categoryTotals. All checks are read-only.
 */
class WebDeletedOverrideHistoricalFlowParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"payroll","kind":"bank"}],
        "snapshots":[{"date":"2026-10-01",
          "balances":{"payroll":1000,"credit-card":400}}],
        "transactions":[
          {"id":"deleted","date":"2026-10-04","type":"expense",
            "accountId":"payroll","amount":40,"category":"Τρόφιμα"},
          {"id":"income","date":"2026-10-05","type":"income",
            "accountId":"payroll","amount":100,"category":"Μισθός"},
          {"id":"reviewed-split","date":"2026-10-08","type":"expense",
            "accountId":"payroll","amount":8,"category":"Άλλο"}
        ]
      },"state":{
        "deleted":["deleted"],
        "overrides":{"deleted":{"id":"deleted","date":"2026-10-04",
          "type":"expense","accountId":"payroll","amount":90,
          "category":"Τρόφιμα"}},
        "reviewDecisions":{
          "reviewed-split":{"status":"confirmed","semanticKind":"split",
            "parts":[
              {"category":"Υπηρεσίες","amount":6,"kind":"expense"},
              {"category":"Υπηρεσίες","amount":2,"kind":"refund"}
            ]}
        },
        "events":[
          {"id":"refund","date":"2026-10-06","kind":"refund",
            "amount":100,"category":"Τρόφιμα","legs":[]},
          {"id":"split","date":"2026-10-09","kind":"split","amount":21,
            "parts":[
              {"category":"Μεταφορές","amount":18,"kind":"expense"},
              {"category":"Μεταφορές","amount":3,"kind":"refund"}
            ],"legs":[]},
          {"id":"future","date":"2026-10-25","kind":"expense",
            "amount":200,"category":"Σπίτι","legs":[]}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun deletionDominatesStaleOverrideWithoutResurrectingLedgerAmount() {
        val balance = doc.accountBalances("2026-10-16")
        // Web: 1000 seed snapshot +40 reversal, no stale +90 override.
        assertEquals(1040.0, balance["payroll"]!!, 0.001)
        assertFalse(doc.effectiveLegacyTransactions().any { it.id == "deleted" })
        assertEquals(0.0, balance[CREDIT_ACCOUNT_ID]!!, 0.001)
        assertEquals(1040.0, doc.availableMoney("2026-10-16"), 0.001)
    }

    @Test fun netUsesUnclampedRefundHeavyExpenseAsWebMonthlyFlowDoes() {
        val now = doc.monthlyFlow("2026-10", "2026-10-16")
        assertEquals(100.0, now.income, 0.001)
        assertEquals(0.0, now.expense, 0.001)
        assertEquals(102.0, now.refunds, 0.001)
        assertEquals(175.0, now.net, 0.001)
        val all = doc.monthlyFlow("2026-10")
        assertEquals(125.0, all.expense, 0.001)
        assertEquals(-25.0, all.net, 0.001)
        // Web applies asOf only to the requested calendar month.
        assertEquals(all, doc.monthlyFlow("2026-10", "2026-09-15"))
    }

    @Test fun legacyReviewSplitWithoutIdsStillContributesToMonthlyFlowAndCategories() {
        assertEquals(175.0, doc.monthlyFlow("2026-10","2026-10-16").net, 0.001)
        val totals = doc.categoryTotals("2026-10","2026-10-16")
        assertEquals(21.0, totals["Μεταφορές"]!!, 0.001)
        assertEquals(4.0, totals["Υπηρεσίες"]!!, 0.001)
        assertFalse("Σπίτι" in totals)
        assertFalse("Τρόφιμα" in totals)
        assertEquals(listOf("Μεταφορές", "Υπηρεσίες"), totals.keys.toList())
        assertEquals(15.0, doc.categoryBudgetSpending("2026-10","2026-10-16")["Μεταφορές"]!!, 0.001)
        assertEquals(4.0, doc.categoryBudgetSpending("2026-10","2026-10-16")["Υπηρεσίες"]!!, 0.001)
    }

    @Test fun pastOrFutureMonthScopesDoNotTrimUnrelatedMonths() {
        assertEquals(-25.0,doc.monthlyFlow("2026-10","2026-09-15").net,0.001)
        assertEquals(200.0, doc.categoryTotals("2026-10","2026-09-15")["Σπίτι"]!!,0.001)
        assertEquals(175.0,doc.monthlyFlow("2026-10","2026-10-16").net,0.001)
    }
}
