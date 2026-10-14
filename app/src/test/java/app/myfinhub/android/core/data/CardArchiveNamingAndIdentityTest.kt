package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CardArchiveNamingAndIdentityTest {
    @Test fun reversibleArchiveCannotBeAdvertisedAsPermanentDeletion() {
        assertEquals("Αρχειοθέτηση κάρτας",
            DeactivateCanonicalCard("credit-1", "2026-10-15T00:00:00Z").description)
        assertEquals("Επαναφορά αρχειοθετημένης κάρτας",
            RestoreCanonicalCard("credit-1", "2026-10-15T01:00:00Z").description)
        assertEquals("Οριστική διαγραφή εξοφλημένης κάρτας",
            PermanentlyDeleteCanonicalCard("credit-1", "2026-10-15T01:00:00Z").description)
    }

    @Test fun archiveRetainsCanonicalHistoryAndRestoresSameCardIdentity() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{"cards":[
            {"id":"credit-1","kind":"credit","active":true,"bankId":"piraeus",
             "nickname":"Card","network":"visa","createdAt":"2026-01-01T00:00:00Z"}
          ],"events":[
            {"id":"old-movement","kind":"card_purchase","cardId":"credit-1",
             "date":"2026-09-01","amount":20,"legs":[]}
          ]}}
        """.trimIndent()).jsonObject)
        val archived = DeactivateCanonicalCard("credit-1","2026-10-15T00:00:00Z").apply(doc)
        val item = archived.state.array("cards").single() as JsonObject
        assertFalse(item.bool("active") == true)
        assertEquals("2026-10-15T00:00:00Z",item.string("archivedAt"))
        assertEquals(doc.state.array("events"),archived.state.array("events"))
        val restored = RestoreCanonicalCard("credit-1","2026-10-15T01:00:00Z").apply(archived)
        val same = restored.state.array("cards").single() as JsonObject
        assertTrue(same.bool("active") == true)
        assertEquals("credit-1",same.string("id"))
        assertEquals(null,same.string("archivedAt"))
        assertEquals(doc.state.array("events"),restored.state.array("events"))
    }
}
