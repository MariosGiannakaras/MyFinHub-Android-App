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
        assertEquals("2026-08-23T19:30:00Z", stored.string("archivedAt"))
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
    @Test
    fun createCard_addsOnlySafeCanonicalMetadataAndIsIdempotent() {
        val mutation = CreateCanonicalCard(
            cardId = "card-new",
            bankId = "issuer",
            nickname = "Καθημερινή",
            kind = "credit",
            network = "visa",
            formFactor = "physical",
            last4 = "1234",
            creditLimit = 1500.0,
            nowIso = "2026-09-04T20:00:00Z",
        )
        val once = mutation.apply(canonicalFixture())
        val twice = mutation.apply(once)
        val card = twice.state.array("cards").mapNotNull { it as? JsonObject }.single { it.string("id") == "card-new" }

        assertEquals("issuer", card.string("bankId"))
        assertEquals("Καθημερινή", card.string("nickname"))
        assertEquals("credit", card.string("kind"))
        assertEquals("visa", card.string("network"))
        assertEquals("1234", card.string("last4"))
        assertTrue(card.bool("active") == true)
        assertFalse(card.keys.any { key ->
            key.lowercase() in setOf("pan", "cardnumber", "expiry", "expirydate", "cvv", "cvc", "securitycode")
        })
        assertEquals(1, twice.state.array("cards").count { (it as? JsonObject)?.string("id") == "card-new" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun createCard_rejectsMalformedLast4() {
        CreateCanonicalCard("new", "issuer", "Name", "debit", "visa", "physical", "12x4", null, "2026-09-04T20:00:00Z")
            .apply(canonicalFixture())
    }

    @Test
    fun restoreArchivedCard_preservesHistoryAndClearsArchivedAtWithoutRestoringSecrets() {
        val created = CreateCanonicalCard(
            cardId = "pay-credit", bankId = "piraeus", nickname = "Visa",
            kind = "credit", network = "visa", formFactor = "physical",
            last4 = "1234", creditLimit = 900.0, nowIso = "2026-10-01T09:00:00Z",
        ).apply(canonicalFixture())
        val archived = DeactivateCanonicalCard("pay-credit", "2026-10-02T09:00:00Z").apply(created)
        val archivedItem = archived.state.array("cards").single() as JsonObject
        assertFalse(archivedItem.bool("active") ?: true)
        assertEquals("2026-10-02T09:00:00Z", archivedItem.string("archivedAt"))
        val restored = RestoreCanonicalCard("pay-credit", "2026-10-03T09:00:00Z").apply(archived)
        val twice = RestoreCanonicalCard("pay-credit", "2026-10-04T09:00:00Z").apply(restored)
        val item = twice.state.array("cards").single() as JsonObject
        assertTrue(item.bool("active") == true)
        assertEquals(null, item.string("archivedAt"))
        assertEquals("2026-10-03T09:00:00Z", item.string("updatedAt"))
        assertEquals("1234", item.string("last4"))
        assertEquals("2026-10-01T09:00:00Z", item.string("createdAt"))
        assertEquals(1, twice.canonicalCards().count { it.id == "pay-credit" && it.active })
        assertTrue(item.keys.none { it.lowercase() in setOf("pan", "cvv", "cvc", "expiry") })
    }

    @Test(expected = IllegalArgumentException::class)
    fun restoreNeverResurrectsPermanentlyDeletedOrUnknownCardId() {
        RestoreCanonicalCard("deleted-card", "2026-10-14T00:00:00Z")
            .apply(canonicalFixture())
    }

}