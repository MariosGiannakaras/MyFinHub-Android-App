package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web budgets.ts explicit commitments, including months beyond the next due. */
class WebBudgetFutureCommitmentsParityTest {
    @Test fun importedScheduledBudgetsFollowWebDueDateAndCreationOrder() {
        // Physical order is intentionally reversed; Web pendingScheduled sorts
        // dueDate/createdAt before projecting, and appends recurring afterward.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "scheduled":[
              {"id":"late","kind":"expense","status":"pending","dueDate":"2026-12-22",
               "createdAt":"2026-11-01T09:00:00Z","category":"Σπίτι","amount":40},
              {"id":"same-late","kind":"expense","status":"pending","dueDate":"2026-12-15",
               "createdAt":"2026-11-10T20:00:00Z","category":"Σπίτι","amount":30},
              {"id":"skipped","kind":"expense","status":"skipped","dueDate":"2026-12-02",
               "createdAt":"2026-11-01T08:00:00Z","category":"Σπίτι","amount":500},
              {"id":"early","kind":"expense","status":"pending","dueDate":"2026-12-08",
               "createdAt":"2026-11-30T09:00:00Z","category":"Σπίτι","amount":10},
              {"id":"same-early","kind":"expense","status":"pending","dueDate":"2026-12-15",
               "createdAt":"2026-11-01T09:00:00Z","category":"Σπίτι","amount":20}
            ],
            "recurringCustom":[
              {"id":"recurring","name":"Internet","amount":50,"day":20,
               "accountId":"cash","category":"Σπίτι","active":true}
            ],
            "budgets":[{"id":"home","month":"2026-12","scope":"category",
                        "category":"Σπίτι","amount":200}]
          }}
        """.trimIndent()).jsonObject)
        val before = imported.raw
        val rows = imported.canonicalBudgetProjectedCommitments("2026-12", "2026-12-01")
        assertEquals(listOf("early","same-early","same-late","late","recurring"),
            rows.map { it.id })
        assertEquals(listOf("scheduled","scheduled","scheduled","scheduled","recurring"),
            rows.map { it.source })
        assertEquals(150.0, rows.sumOf { it.amount }, 0.001)
        val budget = imported.webBudgetProgress("2026-12", "2026-12-01").single()
        assertEquals(150.0, budget.projectedDue, 0.001)
        assertEquals(50.0, budget.projectedRemaining, 0.001)
        assertEquals(before, imported.raw)
    }

    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[]},"state":{
        "recurringCustom":[
          {"id":"fixed","name":"ΔΕΗ","amount":40,"day":15,
           "accountId":"cash","category":"Σπίτι","active":true,
           "recurrenceUnit":"month","recurrenceInterval":1},
          {"id":"variable","name":"Νερό","amount":25,"day":18,
           "accountId":"cash","category":"Σπίτι","active":true,
           "amountMode":"variable"}
        ],
        "scheduled":[
          {"id":"scheduled","kind":"expense","status":"pending",
           "dueDate":"2026-12-15","note":"ΔΕΗ",
           "accountId":"cash","category":"Σπίτι","amount":40}
        ],
        "budgets":[
          {"id":"home-dec","month":"2026-12","scope":"category",
           "category":"Σπίτι","amount":200}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun forecastFutureBudgetMonthEvenWhenAnotherRecurringCycleIsCloser() {
        val commitments = document.canonicalBudgetProjectedCommitments("2026-12", "2026-10-24")
        assertEquals(listOf("scheduled", "fixed"), commitments.map { it.id })
        assertTrue(commitments.all { it.date == "2026-12-15" })
        assertEquals(listOf("scheduled", "recurring"), commitments.map { it.source })
        assertEquals(80.0, commitments.sumOf { it.amount }, 0.001)
    }

    @Test fun variableEstimatesAreNotBookedAndIndependentScheduledRowsAreNotDeduplicated() {
        val current = document.canonicalBudgetProjectedCommitments("2026-12", "2026-12-01")
        assertEquals(2, current.size)
        assertTrue(current.none { it.id == "variable" })
        val budget = document.webBudgetProgress("2026-12", "2026-10-24")
            .single { it.id == "home-dec" }
        assertEquals(80.0, budget.projectedDue, 0.001)
        assertEquals(80.0, budget.projectedUsed, 0.001)
        assertEquals(120.0, budget.projectedRemaining, 0.001)
    }
    @Test fun whitespaceOnlyCompletedEventIdDoesNotInventScheduledLiability() {
        // Web budgetProjectedCommitments: !item.completedEventId is false for
        // " " but true for null/empty/missing. An already linked historical
        // scheduled record must not reappear as a forecast obligation.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "scheduled":[
              {"id":"whitespace","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","accountId":"cash",
               "category":"Σπίτι","amount":60,"completedEventId":"   "},
              {"id":"linked","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","accountId":"cash",
               "category":"Σπίτι","amount":50,"completedEventId":"actual-event"},
              {"id":"empty","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","accountId":"cash",
               "category":"Σπίτι","amount":10,"completedEventId":""},
              {"id":"missing","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","accountId":"cash",
               "category":"Σπίτι","amount":20},
              {"id":"null","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","accountId":"cash",
               "category":"Σπίτι","amount":30,"completedEventId":null}
            ],
            "budgets":[{"id":"all","month":"2026-12","scope":"overall",
                        "amount":250}]
          }}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        val due = imported.canonicalBudgetProjectedCommitments("2026-12","2026-12-01")
        assertEquals(listOf("empty","missing","null"), due.map { it.id })
        assertEquals(60.0, due.sumOf { it.amount }, 0.00001)
        assertTrue(due.all { it.source == "scheduled" })
        val budget = imported.webBudgetProgress("2026-12","2026-12-01").single()
        assertEquals(60.0, budget.projectedDue, 0.00001)
        assertEquals(190.0, budget.projectedRemaining, 0.00001)
        assertEquals(original, imported.raw)
        assertTrue(imported.state.array("events").isEmpty())
    }

    @Test fun importedWhitespaceCategoriesRemainTruthyWebBudgetCommitments() {
        // Web budgets.ts checks item.category, not category.trim():
        // nonempty whitespace is truthy and counts in an overall budget.
        // Empty strings are falsy and excluded from the future projection.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "scheduled":[
              {"id":"space-scheduled","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","category":"   ","amount":20},
              {"id":"empty-scheduled","kind":"expense","status":"pending",
               "dueDate":"2026-12-15","category":"","amount":90}
            ],
            "recurringCustom":[
              {"id":"space-recurring","name":"Service","amount":30,
               "accountId":"cash","category":" ","day":15,
               "active":true,"recurrenceUnit":"month","recurrenceInterval":1},
              {"id":"empty-recurring","name":"Other","amount":40,
               "accountId":"cash","category":"","day":15,
               "active":true,"recurrenceUnit":"month","recurrenceInterval":1}
            ],
            "budgets":[{"id":"all","month":"2026-12","scope":"overall","amount":100}]
          }}
        """.trimIndent()).jsonObject)
        val untouched = imported.raw
        val commitments = imported.canonicalBudgetProjectedCommitments("2026-12", "2026-10-25")
        assertEquals(listOf("space-scheduled","space-recurring"), commitments.map { it.id })
        assertEquals(listOf("scheduled","recurring"), commitments.map { it.source })
        assertEquals(50.0, commitments.sumOf { it.amount }, 0.00001)
        val budget = imported.webBudgetProgress("2026-12", "2026-10-25").single()
        assertEquals(50.0, budget.projectedDue, 0.00001)
        assertEquals(50.0, budget.projectedRemaining, 0.00001)
        assertEquals(untouched, imported.raw)
    }

    @Test fun repeatedHistoricalRecurringIdsRetainSeparateSubcategoryAndAmounts() {
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"recurring":[
            {"id":"duplicate","name":"Energy","amount":35,"day":17,
              "accountId":"cash","category":"Σπίτι","subcategory":"Ενέργεια",
              "active":true,"recurrenceUnit":"month","recurrenceInterval":1}
          ]},"state":{
            "recurringCustom":[
              {"id":"duplicate","name":"Internet","amount":20,"day":17,
               "accountId":"cash","category":"Σπίτι","subcategory":"Σύνδεση",
               "active":true,"recurrenceUnit":"month","recurrenceInterval":1}
            ],
            "budgets":[
              {"id":"energy","month":"2026-11","scope":"subcategory",
                "category":"Σπίτι","subcategory":"Ενέργεια","amount":60},
              {"id":"internet","month":"2026-11","scope":"subcategory",
                "category":"Σπίτι","subcategory":"Σύνδεση","amount":50}
            ]
          }}
        """.trimIndent()).jsonObject)
        val before = imported.raw
        val commitments = imported.canonicalBudgetProjectedCommitments("2026-11", "2026-10-24")
        assertEquals(2, commitments.size)
        assertEquals(listOf("duplicate", "duplicate"), commitments.map { it.id })
        assertEquals(listOf("Ενέργεια", "Σύνδεση"), commitments.map { it.subcategory })
        assertEquals(listOf(35.0, 20.0), commitments.map { it.amount })
        val budgets = imported.webBudgetProgress("2026-11", "2026-10-24").associateBy { it.id }
        assertEquals(35.0, budgets.getValue("energy").projectedDue, 0.001)
        assertEquals(20.0, budgets.getValue("internet").projectedDue, 0.001)
        assertEquals(before, imported.raw)
    }

}
