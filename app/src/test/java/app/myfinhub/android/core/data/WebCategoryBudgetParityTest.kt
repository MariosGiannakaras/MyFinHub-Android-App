package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web budgets.ts categories, reviewed split/semantic and refund golden vectors. */
class WebCategoryBudgetParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"cash","kind":"cash"}],"transactions":[
        {"id":"legacy-food","type":"expense","date":"2026-10-02","amount":100,"category":"Τρόφιμα"},
        {"id":"legacy-split","type":"expense","date":"2026-10-03","amount":80,"category":"Άλλο"},
        {"id":"legacy-general","type":"expense","date":"2026-10-03","amount":15,"category":"Γενικά"},
        {"id":"legacy-savings","type":"expense","date":"2026-10-05","amount":25,"category":"Άλλο"},
        {"id":"legacy-future","type":"expense","date":"2026-10-25","amount":200,"category":"Τρόφιμα"}
      ]},"state":{
        "reviewDecisions":{
          "legacy-split":{"status":"confirmed","semanticKind":"split","parts":[
            {"id":"a","kind":"expense","category":"Τρόφιμα","amount":50},
            {"id":"b","kind":"refund","category":"Εστιατόρια","amount":30}
          ]},
          "legacy-savings":{"status":"confirmed","semanticKind":"saving_cash_offset"}
        },
        "events":[
          {"id":"credit","kind":"card_purchase","date":"2026-10-09","amount":40,"category":"Τρόφιμα"},
          {"id":"refund","kind":"refund","date":"2026-10-10","amount":20,"category":"Τρόφιμα"},
          {"id":"split","kind":"split","date":"2026-10-11","amount":25,"parts":[
            {"id":"a","kind":"expense","category":"Τρόφιμα","amount":10},
            {"id":"b","kind":"refund","category":"Τρόφιμα","amount":5},
            {"id":"c","kind":"saving","category":"Άλλο","amount":20}
          ]},
          {"id":"transfer","kind":"transfer","date":"2026-10-12","amount":500,"category":"Τρόφιμα"},
          {"id":"savings","kind":"saving_cash_offset","date":"2026-10-12","amount":90,"category":"Τρόφιμα"}
        ],
        "budgets":[
          {"id":"overall","month":"2026-10","scope":"overall","amount":200,"alertThreshold":0.8},
          {"id":"food","month":"2026-10","scope":"category","category":"Τρόφιμα","amount":150,"alertThreshold":0.8},
          {"id":"restaurants","month":"2026-10","scope":"category","category":"Εστιατόρια","amount":25,"alertThreshold":75},
          {"id":"other-month","month":"2026-09","scope":"category","category":"Τρόφιμα","amount":900}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun reviewedSplitRefundAndCardPurchaseAreCountedExactlyOnce() {
        val r=doc.categoryBudgetSpending("2026-10","2026-10-15")
        assertEquals(175.0,r["Τρόφιμα"]!!,0.001)
        assertEquals(-30.0,r["Εστιατόρια"]!!,0.001)
        assertEquals(15.0,r["Γενικά"]!!,0.001)
        assertEquals(160.0,r.values.sum(),0.001)
        assertTrue("Άλλο" !in r)
    }

    @Test fun budgetThresholdStatusRawVsClampedNetAndCategorySortingMatchWeb() {
        val r=doc.webBudgetProgress("2026-10","2026-10-15")
        assertEquals(listOf("restaurants","food","overall"),r.map { it.id })
        assertEquals(-30.0,r.first().rawUsed,0.001)
        assertEquals(0.0,r.first().used,0.001)
        assertEquals(0.75,r.first().alertThreshold,0.001)
        assertEquals("ok",r.first().status)
        assertEquals("exceeded",r[1].status)
        assertEquals(175.0,r[1].used,0.001)
        assertEquals("near",r[2].status)
        assertEquals(160.0,r[2].used,0.001)
        assertEquals(40.0,r[2].remaining,0.001)
    }

    @Test fun asOfOnlyLimitsCurrentMonthFutureEvents() {
        assertEquals(160.0,doc.categoryBudgetSpending("2026-10","2026-10-15").values.sum(),0.001)
        assertEquals(360.0,doc.categoryBudgetSpending("2026-10","2026-09-15").values.sum(),0.001)
    }

    @Test fun oldAndNewBudgetThresholdFormsProduceIdenticalUiPercent() {
        val canonical = doc.overallBudget("2026-10")
        assertEquals(80, canonical?.alertThreshold)
        val converted = UpsertOverallBudget("2026-10",300.0,99,"migration","2026-10-15T09:00:00Z").apply(doc)
        val actual = converted.state.array("budgets")
            .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .first { it.string("id")=="overall" }
        assertEquals(0.99,actual.number("alertThreshold")!!,0.001)
        assertEquals(99,converted.overallBudget("2026-10")?.alertThreshold)
    }

    @Test fun oldOfflineBudgetsReconcileBothLegacyAndNormalizedServerRepresentations() {
        val oldMutation = UpsertOverallBudget(
            "2026-10", 300.0, 25, "old-budget-id", "2026-10-15T10:00:00Z",
        )
        val intent = PendingCanonicalMutationIntent.fromMutation(
            oldMutation, "pending-old-budget", PendingMutationSyncState.NEEDS_REVIEW,
        )
        val modernServer = oldMutation.apply(doc)
        val modern = modernServer.state.array("budgets")
            .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .first { it.string("scope") == "overall" }
        assertEquals(0.50, modern.number("alertThreshold")!!, 0.001)
        assertTrue(reconcileSatisfiedPendingMutations(modernServer, listOf(intent)).isEmpty())
        val historical = kotlinx.serialization.json.JsonObject(
            modern + mapOf(
                "alertThreshold" to kotlinx.serialization.json.JsonPrimitive(25),
            ),
        )
        val historicalServer = CanonicalFinanceDocument(kotlinx.serialization.json.JsonObject(
            modernServer.raw + (
                "state" to kotlinx.serialization.json.JsonObject(
                    modernServer.state + (
                        "budgets" to kotlinx.serialization.json.JsonArray(
                            modernServer.state.array("budgets").map { raw ->
                                val row = raw as? kotlinx.serialization.json.JsonObject
                                if (row?.string("scope") == "overall") historical else raw
                            },
                        )
                    )
                )
            )
        ))
        assertTrue(reconcileSatisfiedPendingMutations(historicalServer, listOf(intent)).isEmpty())
        val oldFull = UpsertOverallBudget(
            "2026-10", 301.0, 100, "old-budget-id", "2026-10-15T11:00:00Z",
        ).apply(doc)
        assertEquals(
            0.99,
            oldFull.state.array("budgets").mapNotNull {
                it as? kotlinx.serialization.json.JsonObject
            }.first { it.string("scope") == "overall" }.number("alertThreshold")!!,
            0.001,
        )
    }

    @Test fun canonicalThresholdPercentAndFractionAreBothReadWithoutSilentDrift() {
        val prior=UpsertOverallBudget("2026-10",500.0,85,"new-id","2026-10-15T09:00:00Z").apply(doc)
        val row=prior.webBudgetProgress("2026-10","2026-10-15").first { it.scope=="overall" }
        assertEquals(0.85,row.alertThreshold,0.001)
        assertEquals(85,prior.overallBudget("2026-10")?.alertThreshold)
        assertEquals(0.85,prior.state.array("budgets")
            .mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            .first { it.string("id")=="overall" }.number("alertThreshold")!!,0.001)
    }
}
