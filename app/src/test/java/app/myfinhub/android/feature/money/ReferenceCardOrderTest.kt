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

    @Test fun largeCardRosterCentersActiveDotAndClampsAtEitherEnd() {
        assertEquals(0, referenceCardDotOffset(0, 49, 48, 340, 248))
        assertEquals(148, referenceCardDotOffset(6, 49, 48, 340, 248))
        assertEquals(248, referenceCardDotOffset(11, 49, 48, 340, 248))
        assertEquals(0, referenceCardDotOffset(-1, 49, 48, 340, 248))
        assertEquals(0, referenceCardDotOffset(4, 49, 48, 0, 248))
        assertEquals(0, referenceCardDotOffset(2, 49, 48, 340, 0))
        assertEquals(10, referenceCardDotOffset(Int.MAX_VALUE, Int.MAX_VALUE, 48, 320, 10))
    }

    @Test fun staleCardIdsAreDiscardedWithoutLosingValidTopCard() {
        assertEquals(listOf("credit-b", "virtual-c", "debit-a"),
            referenceReconcileCardOrder(listOf("credit-b", "removed", "virtual-c"), three))
        assertEquals(listOf("credit-b", "debit-a"),
            referenceReconcileCardOrder(listOf("credit-b", "credit-b", "removed"), listOf("debit-a", "credit-b")))
        assertEquals(emptyList<String>(), referenceReconcileCardOrder(three, emptyList()))
    }
}
