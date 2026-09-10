package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalInsightsProjectionTest {
    @Test
    fun currentPartialMonth_comparesOnlyEquivalentPreviousPeriod() {
        val state = projectCanonicalInsightsState(
            document = comparisonFixture(),
            today = LocalDate.of(2026, 9, 10),
        )

        assertEquals("1–10 Σεπ 2026", state.comparison.currentLabel)
        assertEquals("1–10 Αυγ 2026", state.comparison.previousLabel)
        assertEquals(120.0, state.comparison.currentExpense, 0.001)
        assertEquals(100.0, state.comparison.previousExpense, 0.001)
        assertEquals(20.0, state.comparison.expenseChangePercent ?: 0.0, 0.001)

        val august = state.monthlyTrend.first { it.label.startsWith("Αυγ", ignoreCase = true) }
        val september = state.monthlyTrend.last()
        assertEquals(1_000.0, august.expense, 0.001)
        assertTrue(september.isPartial)
        assertEquals("έως 10 Σεπ", september.periodDetail)
        assertEquals(120.0, september.expense, 0.001)
    }

    @Test
    fun averageMonthlySpend_excludesPartialCurrentMonth() {
        val state = projectCanonicalInsightsState(
            document = comparisonFixture(),
            today = LocalDate.of(2026, 9, 10),
        )

        // June and July have zero spend, August has 1,000; September's partial 120 is excluded.
        assertEquals(1_000.0 / 3.0, state.averageMonthlySpend, 0.001)
        assertEquals(120.0, state.categories.single { it.name == "Τρόφιμα" }.amount, 0.001)
    }
}

private fun comparisonFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "schemaVersion":3,
          "seed":{
            "accounts":[],
            "snapshots":[],
            "transactions":[
              {"id":"aug-early","date":"2026-08-05","type":"expense","amount":100.0,"category":"Τρόφιμα","note":"Πρώτο δεκαήμερο"},
              {"id":"aug-late","date":"2026-08-20","type":"expense","amount":900.0,"category":"Στέγαση","note":"Μετά το συγκρίσιμο διάστημα"},
              {"id":"sep-early","date":"2026-09-05","type":"expense","amount":120.0,"category":"Τρόφιμα","note":"Τρέχων μήνας"}
            ]
          },
          "state":{
            "deleted":[],
            "overrides":{},
            "customTransactions":[],
            "events":[],
            "settings":{}
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
