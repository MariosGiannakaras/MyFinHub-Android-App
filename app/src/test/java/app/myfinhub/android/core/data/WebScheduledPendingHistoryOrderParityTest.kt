package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web scheduled.ts pendingScheduled / scheduledHistory sorting goldens. */
class WebScheduledPendingHistoryOrderParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"transactions":[]},
       "state":{"scheduled":[
         {"id":"old-completed","dueDate":"2026-09-01","status":"completed",
          "createdAt":"2026-08-01T10:00:00Z","updatedAt":"2026-10-05T09:00:00Z"},
         {"id":"later","dueDate":"2026-11-12","status":"pending",
          "createdAt":"2026-09-01T10:00:00Z"},
         {"id":"new-skipped","dueDate":"2026-07-02","status":"skipped",
          "createdAt":"2026-06-01T10:00:00Z","updatedAt":"2026-10-15T10:00:00Z"},
         {"id":"same-day-late","dueDate":"2026-10-25","status":"pending",
          "createdAt":"2026-09-03T10:00:00Z"},
         {"id":"same-day-early","dueDate":"2026-10-25","status":"pending",
          "createdAt":"2026-09-01T10:00:00Z"},
         {"id":"cancelled","dueDate":"2026-10-03","status":"cancelled",
          "createdAt":"2026-09-02T10:00:00Z","updatedAt":"2026-10-09T10:00:00Z"}
       ]}}
    """.trimIndent()).jsonObject)

    @Test fun pendingScheduledItemsFollowWebDueDateThenCreatedAt() {
        val pending=doc.canonicalScheduledManagementOrder().filter { it.status=="pending" }
        assertEquals(listOf("same-day-early","same-day-late","later"),pending.map { it.id })
    }

    @Test fun closedHistoryIsMostRecentlyUpdatedFirstRegardlessOfDueDate() {
        val history=doc.canonicalScheduledManagementOrder().filter { it.status!="pending" }
        assertEquals(listOf("new-skipped","cancelled","old-completed"),history.map { it.id })
        assertEquals(listOf("2026-10-15T10:00:00Z","2026-10-09T10:00:00Z",
            "2026-10-05T09:00:00Z"),history.map { it.updatedAt })
    }

    @Test fun pendingAlwaysPrecedesHistoryEvenWhenHistoricalDueDatesAreEarlier() {
        assertEquals(listOf("same-day-early","same-day-late","later",
            "new-skipped","cancelled","old-completed"),
            doc.canonicalScheduledManagementOrder().map { it.id })
        assertEquals(6,doc.canonicalScheduled().size)
    }
}
