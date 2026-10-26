package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web goalProjections.ts shared-savings, contribution calendars and 90-day advisory goldens. */
class WebSavingsGoalProjectionParityTest {
    private fun fixture(payroll: Int = 480, bill: Int = 100) =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{
            "accounts":[
              {"id":"payroll","kind":"bank","bankAccountCategory":"payroll"},
              {"id":"savings","kind":"savings"}
            ],
            "snapshots":[{"date":"2026-10-26",
              "balances":{"payroll":$payroll,"savings":90}}],
            "recurring":[
              {"id":"electricity","name":"Electricity","amount":$bill,
               "day":28,"active":true,"accountId":"payroll","category":"Σπίτι"}
            ]
          },"state":{"events":[]}}
        """.trimIndent()).jsonObject)
    private fun goal(target: Double, date: String? = null): JsonObject =
        JsonObject(buildMap {
            put("id", JsonPrimitive("goal"))
            put("name", JsonPrimitive("Emergency fund"))
            put("targetAmount", JsonPrimitive(target))
            date?.let { put("targetDate", JsonPrimitive(it)) }
        })

    private val asOf = LocalDate.parse("2026-10-26")

    @Test fun usesSharedRealSavingsAndKnownNinetyDayObligationsWithoutPosting() {
        val data = fixture()
        val original = data.raw
        val result = data.canonicalSavingsGoalProjection(goal(290.0, "2027-01-30"), asOf, 12.34)
        assertEquals("active", result.status)
        assertEquals(4, result.contributionMonths)
        assertEquals(90.0, result.sharedSavingsBalance, 0.00001)
        assertEquals(200.0, result.remaining, 0.00001)
        assertEquals(50.0, result.suggestedMonthly!!, 0.00001)
        assertEquals(180.0, result.operatingLiquidityMinimum90d, 0.00001)
        assertEquals(3, result.knownObligationCount90d)
        assertEquals(0, result.forecastOmissions)
        assertEquals("known_movements", result.forecastCompleteness)
        assertEquals("none", result.suggestedContributionRisk)
        assertEquals(listOf("none", "suggested", "custom"),
            result.scenarios.map { it.label })
        assertEquals(listOf(90.0, 290.0, 139.36),
            result.scenarios.map { it.totalAtDeadline })
        assertEquals(listOf(200.0, 0.0, 150.64),
            result.scenarios.map { it.gapAtDeadline })
        assertTrue(data.canonicalEvents().isEmpty())
        assertEquals(original, data.raw)
    }

    @Test fun lowOperatingLiquidityWarnsWithoutTreatingSavingsAsSpendableCash() {
        val data = fixture(payroll = 60, bill = 50)
        val before = data.raw
        val result = data.canonicalSavingsGoalProjection(goal(290.0, "2027-01-30"), asOf)
        assertEquals(-90.0, result.operatingLiquidityMinimum90d, 0.00001)
        assertEquals("liquidity_warning", result.suggestedContributionRisk)
        assertEquals("known_movements", result.forecastCompleteness)
        assertEquals(before, data.raw)
    }

    @Test fun reachedUndatedDueTodayOverdueAndPartialMonthBehaveLikeWeb() {
        val data = fixture()
        val reached = data.canonicalSavingsGoalProjection(goal(50.0, "2025-01-01"), asOf)
        assertEquals("reached", reached.status)
        assertEquals(0.0, reached.suggestedMonthly!!, 0.00001)
        assertTrue(reached.scenarios.isEmpty())
        assertEquals("undated", data.canonicalSavingsGoalProjection(goal(250.0), asOf).status)
        assertEquals("no_date",
            data.canonicalSavingsGoalProjection(goal(250.0), asOf).suggestedContributionRisk)
        assertEquals("due_today",
            data.canonicalSavingsGoalProjection(goal(250.0, "2026-10-26"), asOf).status)
        assertEquals("overdue",
            data.canonicalSavingsGoalProjection(goal(250.0, "2026-10-25"), asOf).status)
        assertEquals(1, data.canonicalSavingsGoalProjection(
            goal(250.0, "2026-11-25"), asOf).contributionMonths)
        assertEquals(2, data.canonicalSavingsGoalProjection(
            goal(250.0, "2026-11-27"), asOf).contributionMonths)
    }

    @Test fun invalidMoneyDatesAndSubcentCustomInputFailClosedWithoutFinanceWrites() {
        val data = fixture()
        val original = data.raw
        assertThrows(IllegalArgumentException::class.java) {
            data.canonicalSavingsGoalProjection(goal(0.0), asOf)
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.canonicalSavingsGoalProjection(goal(50.001), asOf)
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.canonicalSavingsGoalProjection(goal(500.0, "2026-02-30"), asOf)
        }
        assertThrows(IllegalArgumentException::class.java) {
            data.canonicalSavingsGoalProjection(goal(500.0, "2027-01-30"), asOf, 0.001)
        }
        assertEquals(original, data.raw)
    }

    @Test fun noKnownMovementsReportsLimitedDataAndNoOperatingAccountsAreZero() {
        val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"s","kind":"savings"}],
            "snapshots":[{"date":"2026-10-26","balances":{"s":15}}]},
           "state":{"events":[]}}
        """.trimIndent()).jsonObject)
        val result = document.canonicalSavingsGoalProjection(goal(30.0, "2026-11-30"), asOf, 0.0)
        assertEquals("limited", result.forecastCompleteness)
        assertEquals(0, result.knownObligationCount90d)
        assertEquals(0.0, result.operatingLiquidityMinimum90d, 0.00001)
        assertEquals("liquidity_warning", result.suggestedContributionRisk)
        assertEquals("custom", result.scenarios.last().label)
        assertEquals(0.0, result.scenarios.last().monthly, 0.00001)
    }
}
