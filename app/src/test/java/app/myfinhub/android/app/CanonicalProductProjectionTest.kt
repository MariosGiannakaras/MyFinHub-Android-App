package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalFixture
import app.myfinhub.android.feature.activity.ActivityKind
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalProductProjectionTest {
    @Test
    fun canonicalDocument_projectsAcrossAllProductDestinations() {
        val projection = projectCanonicalProduct(
            document = canonicalFixture(),
            today = LocalDate.of(2026, 8, 23),
        )

        assertEquals(2, projection.homeState.accounts.size)
        assertEquals(1_155.0, projection.homeState.accounts.first { it.id == "acc-main" }.balance, 0.001)
        assertEquals(200.0, projection.homeState.monthFlow.income, 0.001)
        assertEquals(155.0, projection.homeState.monthFlow.expense, 0.001)
        assertEquals(800.0, projection.homeState.monthFlow.budget, 0.001)

        assertTrue(projection.activityState.items.any { it.id == "tx-exp" && it.amount == -120.0 })
        assertTrue(projection.activityState.items.any { it.id == "evt-transfer" && it.kind == ActivityKind.TRANSFER })
        assertFalse(projection.activityState.items.any { it.id == "tx-deleted" })

        assertEquals(1_155.0, projection.moneyState.accounts.first { it.id == "acc-main" }.balance, 0.001)
        assertEquals(540.0, projection.moneyState.savingsCurrent, 0.001)
        assertNull(projection.moneyState.savingsGoal)
        assertTrue(projection.moneyState.loans.isEmpty())
        assertTrue(projection.moneyState.lendingItems.isEmpty())
        assertEquals("", projection.moneyState.savingsPlan.targetAmountText)
        assertEquals("", projection.moneyState.savingsPlan.targetDateLabel)
        assertEquals("", projection.moneyState.savingsPlan.monthlyContributionText)

        assertEquals("800", projection.planState.budget.monthlyLimitText)
        assertEquals(1_695.0, projection.planState.forecastEndBalance, 0.001)

        val august = projection.insightsState.monthlyTrend.last()
        assertEquals(200.0, august.income, 0.001)
        assertEquals(155.0, august.expense, 0.001)
        assertEquals("Τρόφιμα", projection.insightsState.categories.first().name)
    }

    @Test
    fun reprojection_preservesEphemeralUiChoicesButReplacesFinanceData() {
        val original = projectCanonicalProduct(canonicalFixture(), LocalDate.of(2026, 8, 23))
        val previous = original.copy(
            homeState = original.homeState.copy(amountsVisible = true),
            activityState = original.activityState.copy(query = "καφ", selectedId = "evt-exp"),
        )

        val refreshed = projectCanonicalProduct(
            document = canonicalFixture(),
            today = LocalDate.of(2026, 8, 23),
            previous = previous,
        )

        assertTrue(refreshed.homeState.amountsVisible)
        assertEquals("καφ", refreshed.activityState.query)
        assertEquals("evt-exp", refreshed.activityState.selectedId)
    }

    @Test
    fun emptyCanonicalData_projectsToUsableFirstUseStates() {
        val empty = CanonicalFinanceDocument(
            Json.parseToJsonElement("""{"seed":{},"state":{}}""").jsonObject,
        )

        val projection = projectCanonicalProduct(
            document = empty,
            today = LocalDate.of(2026, 9, 2),
        )

        assertTrue(projection.homeState.accounts.isEmpty())
        assertTrue(projection.activityState.items.isEmpty())
        assertTrue(projection.moneyState.accounts.isEmpty())
        assertTrue(projection.moneyState.loans.isEmpty())
        assertTrue(projection.moneyState.lendingItems.isEmpty())
        assertTrue(projection.insightsState.categories.isEmpty())
        assertEquals(0.0, projection.homeState.monthFlow.income, 0.001)
        assertEquals(0.0, projection.homeState.monthFlow.expense, 0.001)
    }
}
