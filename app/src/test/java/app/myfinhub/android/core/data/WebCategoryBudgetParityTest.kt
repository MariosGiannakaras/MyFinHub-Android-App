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
        // Raw legacy 75 is capped at Web budgetProgress's 99% read threshold.
        assertEquals(0.99,r.first().alertThreshold,0.001)
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
    @Test fun renamedCategoryAndSubcategoryAliasesPreserveBudgetSpendingAndCarryover() {
        val renamed = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "settings":{"categoryIdentities":{
              "cat-food":{"id":"cat-food","kind":"expense","label":"Διατροφή",
                "aliases":["Τρόφιμα"],"parentId":null},
              "sub-grocery":{"id":"sub-grocery","kind":"expense","parentId":"cat-food",
                "label":"Σούπερ μάρκετ","aliases":["Παντοπωλείο"]}
            }},
            "events":[
              {"id":"prev","kind":"expense","date":"2026-09-20","amount":15,
                "category":"Τρόφιμα","subcategory":"Παντοπωλείο"},
              {"id":"old-name","kind":"expense","date":"2026-10-10","amount":10,
                "category":"Τρόφιμα","subcategory":"Παντοπωλείο"},
              {"id":"new-name","kind":"expense","date":"2026-10-12","amount":20,
                "category":"Διατροφή","subcategory":"Σούπερ μάρκετ"}
            ],
            "budgets":[
              {"id":"old-cat","month":"2026-09","scope":"category",
                "category":"Τρόφιμα","categoryIdentityId":"cat-food",
                "amount":100},
              {"id":"current-cat","month":"2026-10","scope":"category",
                "category":"Διατροφή","categoryIdentityId":"cat-food",
                "amount":200,"carryover":true},
              {"id":"old-sub","month":"2026-09","scope":"subcategory",
                "category":"Τρόφιμα","subcategory":"Παντοπωλείο",
                "categoryIdentityId":"cat-food","subcategoryIdentityId":"sub-grocery",
                "amount":80},
              {"id":"current-sub","month":"2026-10","scope":"subcategory",
                "category":"Διατροφή","subcategory":"Σούπερ μάρκετ",
                "categoryIdentityId":"cat-food","subcategoryIdentityId":"sub-grocery",
                "amount":90,"carryover":true}
            ]
          }}
        """.trimIndent()).jsonObject)
        val budgets = renamed.webBudgetProgress("2026-10", "2026-10-16").associateBy { it.id }
        val category = budgets.getValue("current-cat")
        assertEquals(30.0, category.used, 0.001)
        assertEquals(85.0, category.carried, 0.001)
        assertEquals(285.0, category.limit, 0.001)
        val subcategory = budgets.getValue("current-sub")
        assertEquals(30.0, subcategory.used, 0.001)
        assertEquals(65.0, subcategory.carried, 0.001)
        assertEquals(155.0, subcategory.limit, 0.001)
    }

    @Test fun rawHistoricalAlertThresholdIsWebClampedNotConvertedFromPercent() {
        // Web budgets.ts directly clamps Number(alertThreshold ?? .8) to [.5,.99].
        // A nonnormalized historical value 80 is 0.99 (not 0.80);
        // the distinct mutation/editor compatibility conversion remains intact.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "events":[{"id":"rent","date":"2026-10-10","kind":"expense",
                       "category":"Σπίτι","amount":90}],
            "budgets":[
              {"id":"percent-legacy","month":"2026-10","scope":"category",
               "category":"Σπίτι","amount":100,"alertThreshold":80},
              {"id":"small","month":"2026-10","scope":"category",
               "category":"Άλλο","amount":100,"alertThreshold":0.2},
              {"id":"empty","month":"2026-10","scope":"overall","amount":100},
              {"id":"fraction","month":"2026-10","scope":"category",
               "category":"Σπίτι","amount":100,"alertThreshold":0.85}
            ]
          }}
        """.trimIndent()).jsonObject)
        val before = imported.raw
        val rows = imported.webBudgetProgress("2026-10","2026-10-15").associateBy { it.id }
        val historical = rows.getValue("percent-legacy")
        assertEquals(0.99, historical.alertThreshold, 0.00001)
        assertEquals(90.0, historical.used, 0.00001)
        assertEquals("ok", historical.status)
        assertEquals("ok", historical.projectedStatus)
        assertEquals(0.5, rows.getValue("small").alertThreshold, 0.00001)
        assertEquals(0.8, rows.getValue("empty").alertThreshold, 0.00001)
        assertEquals(0.85, rows.getValue("fraction").alertThreshold, 0.00001)
        assertEquals("near", rows.getValue("fraction").status)
        assertEquals(before, imported.raw)
    }

    @Test fun webBudgetOrderingUsesIdRatherThanSubcategoryAfterSameCategory() {
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{"budgets":[
            {"id":"z-first-sub","month":"2026-10","scope":"subcategory",
             "category":"Σπίτι","subcategory":"Α","amount":50},
            {"id":"a-last-sub","month":"2026-10","scope":"subcategory",
             "category":"Σπίτι","subcategory":"Ω","amount":75},
            {"id":"m-middle","month":"2026-10","scope":"subcategory",
             "category":"Σπίτι","subcategory":"Κ","amount":65}
          ]}}
        """.trimIndent()).jsonObject)
        val before = imported.raw
        assertEquals(listOf("a-last-sub", "m-middle", "z-first-sub"),
            imported.webBudgetProgress("2026-10").map { it.id })
        assertEquals(before, imported.raw)
    }

    @Test fun variantCategoryLabelsCoalesceIntoOneWebPostingBucket() {
        // Web spending() uses categoryKey(identity) as the aggregate key and
        // records the last physical posting label for categoryBudgetSpending.
        // This is presentation/read-only; don't rewrite canonical event labels.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[]},"state":{
            "events":[
              {"id":"first","kind":"expense","date":"2026-10-08",
               "amount":12.0,"category":"Τρόφιμα"},
              {"id":"second","kind":"expense","date":"2026-10-09",
               "amount":8.0,"category":"ΤΡΟΦΙΜΑ"}
            ],
            "budgets":[
              {"id":"food","month":"2026-10","scope":"category",
               "category":"Τρόφιμα","amount":40}
            ]
          }}
        """.trimIndent()).jsonObject)
        val source = imported.raw
        val spent = imported.categoryBudgetSpending("2026-10", "2026-10-15")
        assertEquals(mapOf("ΤΡΟΦΙΜΑ" to 20.0), spent)
        val budget = imported.webBudgetProgress("2026-10", "2026-10-15").single()
        assertEquals(20.0, budget.used, 0.0001)
        assertEquals(20.0, budget.remaining, 0.0001)
        assertEquals(source, imported.raw)
    }

    @Test fun fractionalImportedPostingsRoundEachExpenseToWebCentsBeforeAdding() {
        // Web budgets.ts add(): moneyToCents(amount) per posting, then
        // centsToMoney(sum). Two half-cent events each round to one cent.
        // Android must not first sum 0.005 + 0.005 and yield just 0.01.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[]},"state":{
            "events":[
              {"id":"a","kind":"expense","date":"2026-10-05",
               "category":"Τρόφιμα","amount":0.005},
              {"id":"b","kind":"expense","date":"2026-10-06",
               "category":"Τρόφιμα","amount":0.005}
            ],
            "budgets":[{"id":"food","month":"2026-10","scope":"category",
                        "category":"Τρόφιμα","amount":1}]
          }}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        assertEquals(0.02,
            imported.categoryBudgetSpending("2026-10","2026-10-15").getValue("Τρόφιμα"),
            0.000001)
        val row=imported.webBudgetProgress("2026-10","2026-10-15").single()
        assertEquals(0.02,row.rawUsed,0.000001)
        assertEquals(0.98,row.remaining,0.000001)
        assertEquals(original,imported.raw)
    }

    @Test fun whitespaceOnlyReviewedCategorySuppressesBudgetSpendingAsInWeb() {
        // Web budgets.ts evaluates decision?.category || tx.category:
        // "   " is truthy, but addExpense rejects it after trim().
        // Empty-string review category is falsy and falls back to seed.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[
            {"id":"whitespace","date":"2026-10-10","type":"expense","amount":20,
             "category":"Τρόφιμα","subcategory":"Λαχανικά"},
            {"id":"empty","date":"2026-10-11","type":"expense","amount":10,
             "category":"Τρόφιμα","subcategory":"Λαχανικά"},
            {"id":"no-decision","date":"2026-10-12","type":"expense","amount":5,
             "category":"Τρόφιμα","subcategory":"Λαχανικά"}
          ]},"state":{
            "reviewDecisions":{
              "whitespace":{"status":"confirmed","semanticKind":"expense","category":"   "},
              "empty":{"status":"confirmed","semanticKind":"expense","category":""}
            },
            "budgets":[
              {"id":"overall","month":"2026-10","scope":"overall","amount":50},
              {"id":"food","month":"2026-10","scope":"category",
               "category":"Τρόφιμα","amount":50},
              {"id":"vegetables","month":"2026-10","scope":"subcategory",
               "category":"Τρόφιμα","subcategory":"Λαχανικά","amount":50}
            ]
          }}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        assertEquals(mapOf("Τρόφιμα" to 15.0),
            imported.categoryBudgetSpending("2026-10","2026-10-15"))
        val sub = imported.canonicalSubcategoryBudgetSpending("2026-10","2026-10-15")
        assertEquals(15.0,sub.values.sum(),0.00001)
        val budgets = imported.webBudgetProgress("2026-10","2026-10-15").associateBy { it.id }
        assertEquals(15.0,budgets.getValue("overall").used,0.00001)
        assertEquals(15.0,budgets.getValue("food").used,0.00001)
        assertEquals(15.0,budgets.getValue("vegetables").used,0.00001)
        assertEquals(original,imported.raw)
    }

    @Test fun reviewedLegacyCategoryCorrectionMovesBothBudgetDimensions() {
        val reviewed = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"transactions":[
            {"id":"old","date":"2026-10-12","type":"expense","amount":20,
             "category":"Τρόφιμα","subcategory":"Takeaway"}
          ]},"state":{
            "reviewDecisions":{"old":{"status":"confirmed","semanticKind":"expense",
              "category":"Εστιατόρια"}},
            "budgets":[
              {"id":"old-food","month":"2026-10","scope":"category",
               "category":"Τρόφιμα","amount":100},
              {"id":"new-dining","month":"2026-10","scope":"category",
               "category":"Εστιατόρια","amount":50},
              {"id":"new-dining-sub","month":"2026-10","scope":"subcategory",
               "category":"Εστιατόρια","subcategory":"Takeaway","amount":30}
            ]
          }}
        """.trimIndent()).jsonObject)
        val spending = reviewed.categoryBudgetSpending("2026-10", "2026-10-20")
        assertEquals(20.0, spending["Εστιατόρια"]!!, 0.001)
        assertTrue("Τρόφιμα" !in spending)
        val budgets = reviewed.webBudgetProgress("2026-10", "2026-10-20").associateBy { it.id }
        assertEquals(0.0, budgets.getValue("old-food").used, 0.001)
        assertEquals(20.0, budgets.getValue("new-dining").used, 0.001)
        assertEquals(20.0, budgets.getValue("new-dining-sub").used, 0.001)
    }

}
