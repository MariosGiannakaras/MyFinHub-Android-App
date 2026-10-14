package app.myfinhub.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiStateTest {
    @Test
    fun amounts_areHiddenByDefault_andToggleDeterministically() {
        val initial = syntheticHomeUiState()
        assertFalse(initial.amountsVisible)

        val visible = reduceHomeState(initial, HomeAction.ToggleAmounts)
        assertTrue(visible.amountsVisible)

        val hiddenAgain = reduceHomeState(visible, HomeAction.ToggleAmounts)
        assertFalse(hiddenAgain.amountsVisible)
    }

    @Test
    fun quickEntry_openSelectAndClose_arePureStateTransitions() {
        val initial = syntheticHomeUiState()
        val opened = reduceHomeState(initial, HomeAction.OpenQuickEntry)
        assertTrue(opened.quickEntryOpen)

        val selected = reduceHomeState(
            opened,
            HomeAction.SelectQuickEntry(HomeQuickEntryType.TRANSFER),
        )
        assertEquals(HomeQuickEntryType.TRANSFER, selected.selectedQuickEntryType)

        val closed = reduceHomeState(selected, HomeAction.CloseQuickEntry)
        assertFalse(closed.quickEntryOpen)
        assertEquals(null, closed.selectedQuickEntryType)
    }

    @Test
    fun syntheticFixture_containsHomeDecisionSections() {
        val state = syntheticHomeUiState()
        assertTrue(state.accounts.any { it.group == HomeAccountGroup.LIQUID })
        assertTrue(state.accounts.any { it.group == HomeAccountGroup.SAVINGS })
        assertTrue(state.attentionItems.isNotEmpty())
        assertTrue(state.upcomingItems.isNotEmpty())
        assertTrue(state.monthFlow.budgetProgress in 0f..1f)
    }

    @Test
    fun homeHeadlineIncludesOnlyDailyCashAndPayroll() {
        val original = syntheticHomeUiState()
        assertEquals(2651.20, original.liquidTotal, 0.001)
        val otherBank = HomeAccount("other", "Άλλη τράπεζα", "Τρεχούμενος", 99999.0, HomeAccountGroup.LIQUID)
        val reserve = HomeAccount("reserve", "Καβάτζα", "Μετρητά", 8000.0, HomeAccountGroup.LIQUID)
        assertEquals(original.liquidTotal,
            original.copy(accounts = original.accounts + otherBank + reserve).liquidTotal, 0.001)
    }

    @Test
    fun primaryAccountsPreserveSemanticSlotsWithoutFallingBackToOtherBanks() {
        val accounts = syntheticHomeUiState().accounts
            .filterNot { it.primaryRole == HomePrimaryRole.PAYROLL }
        val primary = homePrimaryAccounts(accounts)
        assertEquals(listOf("cash", "piraeus-savings"), primary.map(HomeAccount::id))
    }
}
