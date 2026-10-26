package app.myfinhub.android.core.data

import app.myfinhub.android.app.projectCanonicalProduct
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardWebMetadataParityTest {
    @Test
    fun walletArchivedCardsUseWebArchiveRecencyWithUpdatedAtFallbackAndStableTies() {
        // Web archivedCardsForBank sorts newest archivedAt (or updatedAt)
        // first; equal dates retain the earlier allCards createdAt/ID order.
        // Stored-array order is deliberately neither of those orders.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"snapshots":[{"date":"2026-10-01","balances":{}}]},
           "state":{"settings":{},"cards":[
             {"id":"b-tie","bankId":"alpha","kind":"debit","network":"visa",
              "nickname":"Same B","active":false,"createdAt":"2026-01-01",
              "archivedAt":"2026-07-10","updatedAt":"2026-07-11"},
             {"id":"oldest","bankId":"alpha","kind":"debit","network":"visa",
              "nickname":"Old","active":false,"createdAt":"2025-10-01",
              "archivedAt":"2026-05-01","updatedAt":"2026-10-09"},
             {"id":"newest","bankId":"alpha","kind":"debit","network":"visa",
              "nickname":"Newest","active":false,"createdAt":"2026-04-01",
              "archivedAt":"2026-09-01","updatedAt":"2026-09-02"},
             {"id":"live","bankId":"alpha","kind":"credit","network":"visa",
              "nickname":"Live","active":true,"createdAt":"2026-02-01"},
             {"id":"fallback","bankId":"alpha","kind":"debit","network":"visa",
              "nickname":"Legacy","active":false,"createdAt":"2026-03-01",
              "updatedAt":"2026-08-12"},
             {"id":"a-tie","bankId":"alpha","kind":"debit","network":"visa",
              "nickname":"Same A","active":false,"createdAt":"2026-01-01",
              "archivedAt":"2026-07-10","updatedAt":"2026-07-12"}
           ],"events":[]}}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        val wallet = projectCanonicalProduct(imported, LocalDate.of(2026, 10, 14)).moneyState
        assertEquals(listOf("newest", "fallback", "a-tie", "b-tie", "oldest"),
            wallet.archivedCards.map { it.id })
        assertEquals(listOf("live"), wallet.cards.map { it.id })
        assertEquals("2026-09-01", wallet.archivedCards.first().archivedAt)
        assertEquals(original, imported.raw)
    }

    @Test
    fun walletActiveCardsFollowWebCreatedAtThenIdWithoutReorderingArchive() {
        // Web allCards sorts by creation and locale-aware ID. An imported
        // persisted array can have later cards first and ties in reverse order.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"snapshots":[{"date":"2026-10-01","balances":{}}]},
           "state":{"settings":{},"cards":[
            {"id":"late","kind":"credit","bankId":"alpha","nickname":"Later",
             "network":"visa","active":true,"createdAt":"2026-09-15"},
            {"id":"b-early","kind":"debit","bankId":"alpha","nickname":"Early B",
             "network":"visa","active":true,"createdAt":"2026-01-01"},
            {"id":"archived","kind":"debit","bankId":"alpha","nickname":"Archived",
             "network":"visa","active":false,"createdAt":"2025-12-01"},
            {"id":"a-early","kind":"credit","bankId":"alpha","nickname":"Early A",
             "network":"visa","active":true,"createdAt":"2026-01-01"}
          ],"events":[]}}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        val wallet = projectCanonicalProduct(imported, LocalDate.of(2026, 10, 14)).moneyState
        assertEquals(listOf("a-early", "b-early", "late"), wallet.cards.map { it.id })
        assertEquals(listOf("archived"), wallet.archivedCards.map { it.id })
        assertEquals(original, imported.raw)
    }

    @Test
    fun missingOrNullActiveFlagKeepsWebLegacyCardsActiveInBothProjections() {
        // Web uses card.active !== false. Older imported card profiles might
        // omit active or contain null; only explicit false means archived.
        val imported = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{},"state":{"cards":[
            {"id":"missing","kind":"credit","nickname":"Missing","createdAt":"2026-01-01"},
            {"id":"null","kind":"debit","nickname":"Null","active":null,"createdAt":"2026-01-02"},
            {"id":"archived","kind":"credit","nickname":"Archived","active":false,"createdAt":"2026-01-03"},
            {"id":"explicit","kind":"credit","nickname":"Explicit","active":true,"createdAt":"2026-01-04"}
          ]}}
        """.trimIndent()).jsonObject)
        val original = imported.raw
        assertEquals(listOf(true, true, false, true),
            imported.canonicalCards().map { it.active })
        assertEquals(listOf(true, true, false, true),
            imported.cards().map { it.active })
        assertEquals(original, imported.raw)
    }

    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
       {"seed":{"accounts":[],"snapshots":[{"date":"2026-10-01","balances":{}}]},
        "state":{"cards":[
         {"id":"pay-card","bankId":"piraeus","nickname":"Salary Visa","kind":"debit","network":"visa",
          "formFactor":"physical","designId":"piraeus-yellow","holderName":"CARD HOLDER",
          "last4":"1234","active":true,"createdAt":"2026-09-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"},
         {"id":"credit-1","bankId":"alpha","nickname":"Credit","kind":"credit","network":"mastercard",
          "formFactor":"virtual","designId":"alpha-bonus","last4":"9876","creditLimit":2000,
          "statementClosingDay":25,"statementDueDay":5,"statementBoundaryRule":"next-cycle",
          "active":true,"createdAt":"2026-09-02T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"},
         {"id":"archived","bankId":"revolut","nickname":"Retired","kind":"credit","network":"visa",
          "active":false,"archivedAt":"2026-10-01T00:00:00Z",
          "createdAt":"2026-08-01T00:00:00Z","updatedAt":"2026-10-01T00:00:00Z"}
        ],"deletedCards":[
          {"id":"legacy-removed","kind":"credit","createdAt":"2026-07-01T00:00:00Z","deletedAt":"2026-09-01T00:00:00Z"},
          {"id":"unrelated","kind":"debit","createdAt":"2026-07-01T00:00:00Z","deletedAt":"2026-09-01T00:00:00Z"}
        ]}}
    """.trimIndent()).jsonObject)

    @Test
    fun canonicalCardMetadataIsNeverLostAcrossTypeProjection() {
        val cards = document.canonicalCards()
        assertEquals(3, cards.size)
        assertEquals("piraeus", cards.first().bankId)
        assertEquals("piraeus-yellow", cards.first().designId)
        assertEquals("physical", cards.first().formFactor)
        assertEquals("CARD HOLDER", cards.first().holderName)
        assertEquals("2026-09-01T00:00:00Z", cards.first().createdAt)
        assertEquals(25, cards[1].statementClosingDay)
        assertEquals(5, cards[1].statementDueDay)
        assertEquals("next-cycle", cards[1].statementBoundaryRule)
        assertFalse(cards[2].active)
        assertEquals("2026-10-01T00:00:00Z", cards[2].archivedAt)
    }

    @Test
    fun walletActiveCardsKeepWebDesignAndStatementMetadataWithoutShowingArchivedCard() {
        val projected = projectCanonicalProduct(document, LocalDate.of(2026,10,14)).moneyState.cards
        assertEquals(2, projected.size)
        assertEquals("piraeus-yellow", projected[0].designId)
        assertEquals("piraeus", projected[0].bankId)
        assertEquals("alpha-bonus", projected[1].designId)
        assertEquals("virtual", projected[1].formFactor)
        assertEquals(25, projected[1].statementClosingDay)
        assertEquals("next-cycle", projected[1].statementBoundaryRule)
        assertTrue(projected.none { it.id == "archived" })
    }

    @Test
    fun deletedCreditTombstonesPreserveHistoricalCardIdentityWithoutSensitiveFields() {
        val deleted = document.deletedCreditCardReferences()
        assertEquals(listOf("legacy-removed"), deleted.map { it.id })
        assertEquals("2026-07-01T00:00:00Z", deleted.single().createdAt)
    }
}
