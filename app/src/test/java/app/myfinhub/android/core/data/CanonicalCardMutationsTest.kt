package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalCardMutationsTest {
    @Test
    fun deactivateCard_preservesRecordHistoryAndUnknownFields() {
        val base = canonicalFixture()
        val card = JsonObject(
            mapOf(
                "id" to JsonPrimitive("card-stable"),
                "bankId" to JsonPrimitive("piraeus"),
                "nickname" to JsonPrimitive("Καθημερινή"),
                "kind" to JsonPrimitive("debit"),
                "network" to JsonPrimitive("VISA"),
                "last4" to JsonPrimitive("4242"),
                "active" to JsonPrimitive(true),
                "unknownCardField" to JsonPrimitive("preserve-me"),
            ),
        )
        val document = CanonicalFinanceDocument(
            base.raw.updated(
                "state",
                base.state.updated("cards", JsonArray(listOf(card))),
            ),
        )

        val mutation = DeactivateCanonicalCard(
            cardId = "card-stable",
            nowIso = "2026-08-23T19:30:00Z",
        )
        val once = mutation.apply(document)
        val twice = mutation.apply(once)
        val stored = twice.state.array("cards").single() as JsonObject

        assertFalse(stored.bool("active") ?: true)
        assertEquals("preserve-me", stored.string("unknownCardField"))
        assertEquals("2026-08-23T19:30:00Z", stored.string("updatedAt"))
        assertEquals(1, twice.state.array("cards").size)
        assertTrue(twice.canonicalCards().none { it.active })
        assertEquals("keep-state", twice.state.string("unknownState"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun deactivateCard_rejectsMissingStableId() {
        DeactivateCanonicalCard(
            cardId = "missing-card",
            nowIso = "2026-08-23T19:30:00Z",
        ).apply(canonicalFixture())
    }
}