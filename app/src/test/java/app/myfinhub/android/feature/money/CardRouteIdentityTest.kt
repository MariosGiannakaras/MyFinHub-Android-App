package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardRouteIdentityTest {
    private fun card(id: String, label: String = id) = MoneyCard(
        id = id, nickname = label, last4 = "1881",
        kind = "Πιστωτική", currentBalance = 1.0, limit = 1000.0,
        vaultState = VaultState.LOCKED, canonicalKind = "credit",
    )

    @Test fun uniqueCardRouteResolvesCorrectActiveSource() {
        val valid = card("valid")
        assertEquals(valid, MoneyUiState(cards = listOf(valid)).uniqueActiveCardForRoute("valid"))
    }

    @Test fun absentBlankOrUntrimmedIdsAreNotSafe() {
        val source = MoneyUiState(cards = listOf(card("valid"), card("bad "), card(" ")))
        assertNull(source.uniqueActiveCardForRoute("missing"))
        assertNull(source.uniqueActiveCardForRoute(""))
        assertNull(source.uniqueActiveCardForRoute(" "))
        assertNull(source.uniqueActiveCardForRoute("bad "))
    }

    @Test fun duplicatedActiveIdDoesNotSelectArbitraryFirstPhysicalCard() {
        assertNull(MoneyUiState(cards = listOf(
            card("same", "First"), card("same", "Second"),
        )).uniqueActiveCardForRoute("same"))
    }

    @Test fun archivedCardInActiveListStillCannotOpen() {
        val archived = card("archived").copy(archivedAt = "2026-11-19")
        assertNull(MoneyUiState(cards = listOf(archived)).uniqueActiveCardForRoute(archived.id))
    }

    @Test fun activeAndArchivedCollisionDoesNotExposeEitherRecord() {
        val original = card("collision")
        assertNull(MoneyUiState(
            cards = listOf(original),
            archivedCards = listOf(original.copy(archivedAt = "2026-11-19")),
        ).uniqueActiveCardForRoute("collision"))
    }
}
