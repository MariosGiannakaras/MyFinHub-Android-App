package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web lending.ts reconstructs only an exact, complete dated seed history. */
class WebLegacyLendingAsOfSnapshotParityTest {
    private val valid = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"lending":[
        {"person":"Μαρία","outstanding":120,"entries":[
          {"date":"2026-10-20","repaid":20},
          {"date":"2026-09-01","lent":100},
          {"date":"2026-11-06","haircut":10},
          {"date":"2026-10-04","lent":50}
        ]}
      ]},"state":{"events":[
        {"id":"dated","kind":"lending","date":"2026-10-07",
          "person":"Μαρία","receivableDelta":5},
        {"id":"future","kind":"repayment","date":"2026-11-15",
          "person":"Μαρία","receivableDelta":-15},
        {"id":"anonymous","kind":"lending","date":"2026-10-07",
          "receivableDelta":10}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun reconstructsOnlyCompleteCentExactChronologicalSeedEntries() {
        val before = valid.raw
        val rows = valid.canonicalLendingPersonRows("2026-10-08")
        assertEquals(1, rows.size)
        assertEquals(155.0, rows.single().outstanding, 0.001)
        assertEquals(2 + 1, rows.single().events)
        // Net worth includes unattributed delta even though per-person rows do not.
        assertEquals(165.0, valid.receivableOutstanding("2026-10-08"), 0.001)
        assertEquals(0.0, valid.canonicalLendingPersonRows("2026-08-31").single().outstanding, 0.001)
        assertEquals(120.0, valid.receivableOutstanding("2026-12-01"), 0.001)
        assertEquals(120.0, valid.receivableOutstanding(), 0.001)
        assertEquals(before, valid.raw)
    }

    @Test fun incompleteOrInvalidEntriesPreserveDocumentedSeedBalance() {
        val cases = listOf(
            """{"person":"A","outstanding":80,"entries":[{"date":"2026-09-10","lent":40}]}""",
            """{"person":"A","outstanding":80,"entries":[{"date":"2026-99-10","lent":80}]}""",
            """{"person":"A","outstanding":80,"entries":[{"date":"2026-09-10","lent":80.005}]}""",
            """{"person":"A","outstanding":80,"entries":[{"date":"2026-09-10","repaid":10},{"date":"2026-10-10","lent":90}]}""",
            """{"person":"A","outstanding":80,"entries":[{"date":"2026-09-10","lent":80},{"date":"2026-10-10","haircut":-1}]}"""
        )
        for (case in cases) {
            val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
                """{"seed":{"lending":[$case]},"state":{"events":[]}}"""
            ).jsonObject)
            val raw = doc.seed.array("lending").single().let { it as kotlinx.serialization.json.JsonObject }
            val snapshot = canonicalLegacyLendingSnapshot(raw, "2026-09-15")
            assertFalse(snapshot.reconstructed)
            assertEquals(80.0, snapshot.outstanding, 0.001)
            assertEquals(80.0, doc.receivableOutstanding("2026-09-15"), 0.001)
            assertEquals(80.0, doc.canonicalLendingPersonRows("2026-09-15").single().outstanding, 0.001)
        }
    }

    @Test fun validatedHistoryReportsOnlyEntriesUpToAsOfAndPreservesAllTime() {
        val record = valid.seed.array("lending").single() as kotlinx.serialization.json.JsonObject
        val history = canonicalLegacyLendingSnapshot(record, "2026-10-08")
        assertTrue(history.reconstructed)
        assertEquals(150.0, history.outstanding, 0.001)
        assertEquals(2, history.events)
        assertEquals(4, canonicalLegacyLendingSnapshot(record, null).events)
        assertEquals(120.0, canonicalLegacyLendingSnapshot(record, null).outstanding, 0.001)
    }
}
