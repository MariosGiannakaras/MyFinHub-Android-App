package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class ReferenceCardOrderTest {
    private val three = listOf("debit-a", "credit-b", "virtual-c")

    @Test fun swipeUpMovesForwardAndSwipeDownMovesBackwardThroughThreeCards() {
        val forward = referenceCardOrderAfterSwipe(three, -1)
        assertEquals(listOf("credit-b", "virtual-c", "debit-a"), forward)
        assertEquals(three, referenceCardOrderAfterSwipe(forward, 1))
        assertEquals(listOf("virtual-c", "debit-a", "credit-b"), referenceCardOrderAfterSwipe(three, 1))
    }

    @Test fun wrapAroundSingleCardAndZeroDragAreDeterministic() {
        assertEquals(three, referenceCardOrderAfterSwipe(three, 0))
        assertEquals(emptyList<String>(), referenceCardOrderAfterSwipe(emptyList(), 1))
        assertEquals(listOf("debit-a"), referenceCardOrderAfterSwipe(listOf("debit-a"), -1))
        val back = referenceCardOrderAfterSwipe(three, 1)
        assertEquals(three, referenceCardOrderAfterSwipe(back, -1))
    }

    @Test fun staleCardIdsAreDiscardedWithoutLosingValidTopCard() {
        assertEquals(listOf("credit-b", "virtual-c", "debit-a"),
            referenceReconcileCardOrder(listOf("credit-b", "removed", "virtual-c"), three))
        assertEquals(listOf("credit-b", "debit-a"),
            referenceReconcileCardOrder(listOf("credit-b", "credit-b", "removed"), listOf("debit-a", "credit-b")))
        assertEquals(emptyList<String>(), referenceReconcileCardOrder(three, emptyList()))
    }
}
