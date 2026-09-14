package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_30_DAYS
import app.myfinhub.android.feature.insights.INSIGHTS_PERIOD_MONTH
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalInsightsProjectionTest {
    @Test
    fun currentPartialMonth_comparesOnlyEquivalentPreviousPeriod() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        val scope = state.periodScope(INSIGHTS_PERIOD_MONTH)

        assertEquals("1 Αυγ – 10 Αυγ 2026", scope.comparison.previousLabel)
        assertEquals("1 Σεπ – 10 Σεπ 2026", scope.comparison.currentLabel)
        assertEquals(120.0, scope.comparison.currentExpense, 0.001)
        assertEquals(100.0, scope.comparison.previousExpense, 0.001)
        assertEquals(20.0, scope.comparison.expenseChangePercent ?: 0.0, 0.001)
        assertTrue(scope.contextLabel.contains("Μερικός μήνας"))

        val august = state.monthlyTrend.first { it.label.startsWith("Αυγ", ignoreCase = true) }
        val september = state.monthlyTrend.last()
        assertEquals(1_000.0, august.expense, 0.001)
        assertTrue(september.isPartial)
        assertEquals("έως 10 Σεπ", september.periodDetail)
        assertEquals(120.0, september.expense, 0.001)
    }

    @Test
    fun thirtyDayComparison_hasExactlyEqualAdjacentWindowLength() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        val scope = state.periodScope(INSIGHTS_PERIOD_30_DAYS)
        assertEquals("12 Αυγ – 10 Σεπ 2026", scope.comparison.currentLabel)
        assertEquals("13 Ιουλ – 11 Αυγ 2026", scope.comparison.previousLabel)
    }

    @Test
    fun topCategories_includeRemainderSoSharesCoverCompleteDenominator() {
        val categories = insightCategories(categoryFixture(), LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))
        assertEquals(6, categories.size)
        val other = categories.last()
        assertEquals("Λοιπά", other.name)
        assertEquals(setOf("Ζ", "Η"), other.sourceCategories.toSet())
        assertEquals(1.0, categories.sumOf { it.share.toDouble() }, 0.0001)
        assertEquals(30.0, other.amount, 0.001)
    }

    @Test
    fun zeroPreviousExpense_isUnavailablePercentageNotFakeZeroChange() {
        val state = projectCanonicalInsightsState(categoryFixture(), LocalDate.of(2026, 9, 10))
        assertNull(state.periodScope(INSIGHTS_PERIOD_MONTH).comparison.expenseChangePercent)
    }

    @Test
    fun averageMonthlySpend_excludesPartialCurrentMonth() {
        val state = projectCanonicalInsightsState(comparisonFixture(), LocalDate.of(2026, 9, 10))
        assertEquals(1_000.0 / 3.0, state.averageMonthlySpend, 0.001)
        assertEquals(120.0, state.periodScope(INSIGHTS_PERIOD_MONTH).categories.single { it.name == "Τρόφιμα" }.amount, 0.001)
    }
}

private fun comparisonFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "schemaVersion":3,
          "seed":{
            "accounts":[],"snapshots":[],
            "transactions":[
              {"id":"aug-early","date":"2026-08-05","type":"expense","amount":100.0,"category":"Τρόφιμα","note":"Πρώτο δεκαήμερο"},
              {"id":"aug-late","date":"2026-08-20","type":"expense","amount":900.0,"category":"Στέγαση","note":"Μετά το συγκρίσιμο διάστημα"},
              {"id":"sep-early","date":"2026-09-05","type":"expense","amount":120.0,"category":"Τρόφιμα","note":"Τρέχων μήνας"}
            ]
          },
          "state":{"deleted":[],"overrides":{},"customTransactions":[],"events":[],"settings":{}}
        }
        """.trimIndent(),
    ).jsonObject,
)

private fun categoryFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "schemaVersion":3,
          "seed":{
            "accounts":[],"snapshots":[],
            "transactions":[
              {"id":"a","date":"2026-09-02","type":"expense","amount":80.0,"category":"Α"},
              {"id":"b","date":"2026-09-03","type":"expense","amount":70.0,"category":"Β"},
              {"id":"c","date":"2026-09-04","type":"expense","amount":60.0,"category":"Γ"},
              {"id":"d","date":"2026-09-05","type":"expense","amount":50.0,"category":"Δ"},
              {"id":"e","date":"2026-09-06","type":"expense","amount":40.0,"category":"Ε"},
              {"id":"z","date":"2026-09-07","type":"expense","amount":20.0,"category":"Ζ"},
              {"id":"h","date":"2026-09-08","type":"expense","amount":10.0,"category":"Η"}
            ]
          },
          "state":{"deleted":[],"overrides":{},"customTransactions":[],"events":[],"settings":{}}
        }
        """.trimIndent(),
    ).jsonObject,
)
