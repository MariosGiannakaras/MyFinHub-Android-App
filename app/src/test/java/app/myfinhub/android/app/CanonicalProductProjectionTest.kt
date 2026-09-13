package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalFixture
import app.myfinhub.android.core.data.DeactivateCanonicalCard
import app.myfinhub.android.feature.money.canonicalCreditOutstanding
import app.myfinhub.android.feature.money.canonicalNetPosition
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.activity.ActivityFilter
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
    fun deactivatingCard_preservesTotalDebtAndDoesNotAssignItToRemainingCard() {
        val document = creditFixture()
        val before = projectCanonicalProduct(document, LocalDate.of(2026, 9, 10)).moneyState
        val removed = DeactivateCanonicalCard("old", "2026-09-10T12:00:00Z").apply(document)
        val afterProjection = projectCanonicalProduct(removed, LocalDate.of(2026, 9, 10))
        val after = afterProjection.moneyState

        assertEquals(250.0, canonicalCreditOutstanding(before), 0.001)
        assertEquals(canonicalCreditOutstanding(before), canonicalCreditOutstanding(after), 0.001)
        assertEquals(750.0, canonicalNetPosition(after), 0.001)
        assertEquals("remaining", after.cards.single().id)
        assertEquals(50.0, after.cards.single().currentBalance, 0.001)
        assertEquals(document.state["events"], removed.state["events"])
        val retainedPurchase = afterProjection.activityState.items.first { it.id == "old-purchase" }
        assertEquals("card_purchase", retainedPurchase.canonicalKind)
        assertEquals("Αγορά με κάρτα", retainedPurchase.typeLabel)
        assertEquals("old", retainedPurchase.cardId)
        assertEquals("Κάρτα", retainedPurchase.cardLabel)
    }

    @Test
    fun aggregateDebtSurvivesNoVisibleCardsAndIncludesSnapshotDebt() {
        val projection = projectCanonicalProduct(creditFixture(snapshotDebt = 300.0, cards = ""), LocalDate.of(2026, 9, 10))
        assertTrue(projection.moneyState.cards.isEmpty())
        assertEquals(550.0, canonicalCreditOutstanding(projection.moneyState), 0.001)
        assertEquals(450.0, canonicalNetPosition(projection.moneyState), 0.001)
    }

    private fun creditFixture(
        snapshotDebt: Double = 0.0,
        cards: String = """{"id":"old","kind":"credit","active":true},{"id":"remaining","kind":"credit","active":true}""",
    ) = CanonicalFinanceDocument(Json.parseToJsonElement("""
        {"seed":{"accounts":[{"id":"bank","name":"Bank","kind":"bank"}],
          "snapshots":[{"date":"2026-09-01","balances":{"bank":1000,"credit-card":${-snapshotDebt}}}]},
         "state":{"cards":[$cards],"events":[
          {"id":"old-purchase","date":"2026-09-02","kind":"card_purchase","cardId":"old","amount":200,"creditDelta":-200,"legs":[{"accountId":"credit-card","amount":-200}]},
          {"id":"new-purchase","date":"2026-09-03","kind":"card_purchase","cardId":"remaining","amount":80,"creditDelta":-80,"legs":[{"accountId":"credit-card","amount":-80}]},
          {"id":"payment","date":"2026-09-04","kind":"card_payment","cardId":"remaining","amount":30,"creditDelta":30,"legs":[{"accountId":"credit-card","amount":30}]},
          {"id":"future","date":"2026-12-01","kind":"card_purchase","cardId":"remaining","amount":900,"creditDelta":-900,"legs":[{"accountId":"credit-card","amount":-900}]}
         ]}}
    """.trimIndent()).jsonObject)

    @Test
    fun canonicalDocument_projectsAcrossAllProductDestinations() {
        val projection = projectCanonicalProduct(
            document = canonicalFixture(),
            today = LocalDate.of(2026, 8, 23),
        )

        assertEquals(2, projection.homeState.accounts.size)
        assertEquals(1_155.0, projection.homeState.accounts.first { it.id == "acc-main" }.balance, 0.001)
        assertEquals(7, projection.homeState.accounts.first { it.id == "acc-main" }.balanceTrend.size)
        assertEquals(200.0, projection.homeState.monthFlow.income, 0.001)
        assertEquals(155.0, projection.homeState.monthFlow.expense, 0.001)
        assertEquals(800.0, projection.homeState.monthFlow.budget, 0.001)

        assertTrue(projection.activityState.items.any { it.id == "tx-exp" && it.amount == -120.0 })
        assertTrue(projection.activityState.items.any {
            it.id == "evt-transfer" &&
                it.kind == ActivityKind.TRANSFER &&
                it.canonicalKind == "transfer" &&
                it.typeLabel == "Μεταφορά"
        })
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
        assertEquals(1_695.0, projection.planState.forecastStartBalance, 0.001)
        assertEquals(1_695.0, projection.planState.forecastEndBalance, 0.001)
        assertEquals(155.0, projection.planState.budgetSpent, 0.001)
        assertTrue(projection.planState.budgetMonthLabel.contains("2026"))
        assertTrue(projection.planState.categoryBudgets.isEmpty())
        assertTrue(projection.planState.rules.isEmpty())
        assertTrue(projection.planState.forecastWindows.isEmpty())
        projection.planState.items.forEach { item ->
            assertEquals("", item.category)
            assertEquals("", item.accountLabel)
            assertEquals("", item.note)
        }

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
            activityState = original.activityState.copy(query = "καφ", selectedId = "evt-exp",
                filter = ActivityFilter.EXPENSE, accountFilterId = "acc-main",
                ledgerCategoryFilter = "Τρόφιμα", ledgerDateFrom = "2026-08-02", ledgerDateTo = "2026-08-22",
                typeFilterId = "card_purchase",
                categoryFilter = "Τρόφιμα", dateFrom = "2026-08-01", dateTo = "2026-08-23"),
        )

        val refreshed = projectCanonicalProduct(
            document = canonicalFixture(),
            today = LocalDate.of(2026, 8, 23),
            previous = previous,
        )

        assertTrue(refreshed.homeState.amountsVisible)
        assertEquals("καφ", refreshed.activityState.query)
        assertEquals("evt-exp", refreshed.activityState.selectedId)
        assertEquals(ActivityFilter.EXPENSE, refreshed.activityState.filter)
        assertEquals("acc-main", refreshed.activityState.accountFilterId)
        assertEquals("Τρόφιμα", refreshed.activityState.ledgerCategoryFilter)
        assertEquals("2026-08-02", refreshed.activityState.ledgerDateFrom)
        assertEquals("2026-08-22", refreshed.activityState.ledgerDateTo)
        assertEquals("card_purchase", refreshed.activityState.typeFilterId)
        assertEquals("Τρόφιμα", refreshed.activityState.categoryFilter)
        assertEquals("2026-08-01", refreshed.activityState.dateFrom)
        assertEquals("2026-08-23", refreshed.activityState.dateTo)
    }

    @Test
    fun everyCategoryDrillDown_reconcilesToItsCanonicalTotal() {
        val projection = projectCanonicalProduct(canonicalFixture(), LocalDate.of(2026, 8, 23))
        projection.insightsState.categories.forEach { category ->
            val rows = projection.activityState.forCategory(category.name,
                projection.insightsState.categoryStartDate, projection.insightsState.categoryEndDate).visibleItems
            assertEquals(category.amount, -rows.sumOf { it.amount }, 0.001)
            assertTrue(rows.all { it.rawDate in "2026-08-01".."2026-08-23" })
        }
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
        assertTrue(projection.planState.categoryBudgets.isEmpty())
        assertTrue(projection.planState.rules.isEmpty())
        assertTrue(projection.planState.forecastWindows.isEmpty())
        assertTrue(projection.insightsState.categories.isEmpty())
        assertEquals(0.0, projection.homeState.monthFlow.income, 0.001)
        assertEquals(0.0, projection.homeState.monthFlow.expense, 0.001)
    }
}
