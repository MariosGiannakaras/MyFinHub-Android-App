package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalDataIntegrityTest {
    @Test
    fun emptyCanonicalCollections_areValid() {
        val document = document("""{"updatedAt":"2026-09-02T00:00:00Z","seed":{},"state":{}}""")

        assertNull(CanonicalDataIntegrity.validateDocument(document))
        assertNull(CanonicalDataIntegrity.validateEnvelope(CanonicalFinanceEnvelope(document, "1", "saved")))
    }

    @Test
    fun duplicateStableIds_areRejected() {
        val document = document(
            """{"seed":{},"state":{"events":[{"id":"same","date":"2026-09-01","amount":1},{"id":"same","date":"2026-09-02","amount":2}]}}""",
        )

        assertEquals("DUPLICATE_EVENTS_ID", CanonicalDataIntegrity.validateDocument(document)?.code)
    }

    @Test
    fun malformedKnownDate_isRejectedWithoutInspectingUnknownFields() {
        val document = document(
            """{"seed":{},"state":{"events":[{"id":"event-1","date":"2026-99-99","amount":1}],"desktopOnly":{"date":"not-a-date"}}}""",
        )

        assertEquals("INVALID_EVENTS_DATE", CanonicalDataIntegrity.validateDocument(document)?.code)
    }

    @Test
    fun extremeKnownMoneyValue_isRejected() {
        val document = document(
            """{"seed":{},"state":{"events":[{"id":"event-1","date":"2026-09-02","amount":1e20}]}}""",
        )

        assertEquals("INVALID_EVENTS_AMOUNT", CanonicalDataIntegrity.validateDocument(document)?.code)
    }

    @Test
    fun mutation_preservesUnknownDesktopOwnedFieldsLosslessly() {
        val document = document(
            """{"schemaVersion":99,"desktopRoot":{"future":true},"seed":{},"state":{"events":[],"desktopOnly":{"nested":"keep-me"}}}""",
        )
        val rootUnknown = document.raw["desktopRoot"]
        val stateUnknown = document.state["desktopOnly"]

        val mutated = UpsertOverallBudget(
            month = "2026-09",
            amount = 1200.50,
            alertThreshold = 80,
            budgetId = "budget-test",
            nowIso = "2026-09-02T00:00:00Z",
        ).apply(document)

        assertEquals(rootUnknown, mutated.raw["desktopRoot"])
        assertEquals(stateUnknown, mutated.state["desktopOnly"])
        assertNull(CanonicalDataIntegrity.validateDocument(mutated))
        assertTrue(mutated.raw.containsKey("schemaVersion"))
    }

    @Test
    fun invalidRevision_isRejectedEvenWhenDocumentIsValid() {
        val document = document("""{"seed":{},"state":{}}""")
        val issue = CanonicalDataIntegrity.validateEnvelope(CanonicalFinanceEnvelope(document, "etag-value", "saved"))
        assertEquals("INVALID_REVISION", issue?.code)
    }

    private fun document(raw: String) = CanonicalFinanceDocument(Json.parseToJsonElement(raw).jsonObject)
}
