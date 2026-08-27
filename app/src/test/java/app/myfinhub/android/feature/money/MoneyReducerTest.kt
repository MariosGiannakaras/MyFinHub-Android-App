package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyReducerTest {
    @Test
    fun invalidSavingsTarget_isRejected() {
        val state = MoneyUiState(
            savingsPlan = SavingsPlan(
                targetAmountText = "0",
                targetDateLabel = "Δεκ 2027",
                monthlyContributionText = "250",
            ),
        )

        val result = reduceMoney(state, MoneyAction.SaveSavingsDraft)

        assertEquals("Ο στόχος πρέπει να είναι μεγαλύτερος από μηδέν.", result.frontendMessage)
    }

    @Test
    fun validSavingsTarget_isAccepted() {
        val state = MoneyUiState(
            savingsPlan = SavingsPlan(
                targetAmountText = "7500,50",
                targetDateLabel = "Δεκ 2027",
                monthlyContributionText = "275",
            ),
        )

        val result = reduceMoney(state, MoneyAction.SaveSavingsDraft)

        assertTrue(result.frontendMessage.orEmpty().contains("αποθηκεύτηκαν"))
    }

    @Test
    fun updateLoan_changesOnlySelectedStableId() {
        val second = LoanItem(
            id = "loan-second",
            name = "Δεύτερο",
            lender = "Τράπεζα",
            remaining = 500.0,
            monthlyPayment = 50.0,
            nextPaymentLabel = "20 Σεπ",
            originalAmount = 1_000.0,
        )
        val state = MoneyUiState(loans = syntheticLoans() + second)
        val target = state.loans.first()

        val result = reduceMoney(
            state,
            MoneyAction.UpdateLoan(
                id = target.id,
                name = "Νέο όνομα",
                lender = "Νέος πιστωτής",
                remaining = 3_900.0,
                monthlyPayment = 190.0,
                nextPaymentLabel = "13 Σεπ",
            ),
        )

        assertEquals("Νέο όνομα", result.loans.first().name)
        assertEquals(3_900.0, result.loans.first().remaining, 0.0)
        assertEquals(second, result.loans.last())
    }

    @Test
    fun lendingSettledToggle_isStableById() {
        val second = LendingItem(
            id = "lend-second",
            personLabel = "Δεύτερη απαίτηση",
            amount = 50.0,
            dueLabel = "20 Σεπ",
        )
        val state = MoneyUiState(lendingItems = syntheticLendingItems() + second)
        val targetId = state.lendingItems.first().id

        val settled = reduceMoney(state, MoneyAction.ToggleLendingSettled(targetId))
        val reopened = reduceMoney(settled, MoneyAction.ToggleLendingSettled(targetId))

        assertTrue(settled.lendingItems.first { it.id == targetId }.settled)
        assertFalse(reopened.lendingItems.first { it.id == targetId }.settled)
        assertEquals(second, settled.lendingItems.last())
    }

    @Test
    fun loanPauseToggle_isStableById() {
        val state = MoneyUiState()
        val targetId = state.loans.first().id

        val paused = reduceMoney(state, MoneyAction.ToggleLoanPause(targetId))
        val resumed = reduceMoney(paused, MoneyAction.ToggleLoanPause(targetId))

        assertTrue(paused.loans.first { it.id == targetId }.paused)
        assertFalse(resumed.loans.first { it.id == targetId }.paused)
    }
}
