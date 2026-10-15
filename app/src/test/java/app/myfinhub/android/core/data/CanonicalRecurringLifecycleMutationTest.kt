package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Canonical seeded/custom pause, resume, stop and replay integrity. */
class CanonicalRecurringLifecycleMutationTest {
    private val fixture=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"recurring":[
        {"id":"seeded","name":"Ενοίκιο","amount":100,"day":15,"accountId":"bank","category":"Σπίτι","active":true}
      ]},"state":{"recurringOverrides":{},
        "recurringCustom":[
          {"id":"custom","name":"Συνδρομή","amount":20,"day":20,"accountId":"bank","category":"Πάγια","active":true}
        ],"events":[],"unknownState":"keep"}}
    """.trimIndent()).jsonObject)
    private val whenIso="2026-10-15T14:00:00Z"

    @Test fun seededLifecycleMaintainsSeedAndReplaysStably() {
        val pause=SetCanonicalRecurringStatus("seeded","paused",whenIso)
        val once=pause.apply(fixture)
        assertEquals("paused",once.allCanonicalRecurringItems().first { it.id=="seeded" }.effectiveStatus)
        assertTrue(fixture.seed.array("recurring").any { (it as JsonObject).bool("active")==true })
        assertEquals(once.raw,pause.apply(once).raw)
        assertEquals("keep",once.state.string("unknownState"))
        val resumed=SetCanonicalRecurringStatus("seeded","active","2026-10-15T15:00:00Z").apply(once)
        assertEquals("active",resumed.allCanonicalRecurringItems().first { it.id=="seeded" }.effectiveStatus)
        assertEquals(1,resumed.state.obj("recurringOverrides").size)
    }

    @Test fun customStatusUpdatesInPlaceWithoutDuplicatingOrMutatingOtherRows() {
        val stopped=SetCanonicalRecurringStatus("custom","stopped",whenIso).apply(fixture)
        assertFalse(stopped.activeCanonicalRecurringItems().any { it.id=="custom" })
        assertEquals("stopped",stopped.allCanonicalRecurringItems().first { it.id=="custom" }.effectiveStatus)
        assertEquals(1,stopped.state.array("recurringCustom").size)
        assertEquals(emptyMap<String,String>(),stopped.state.obj("recurringOverrides").mapValues { it.value.toString() })
    }

    @Test fun invalidIdentityOrStatusIsRejectedWithoutSilentCreation() {
        assertThrows(IllegalArgumentException::class.java) {
            SetCanonicalRecurringStatus("unknown","active",whenIso).apply(fixture)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SetCanonicalRecurringStatus("seeded","deleted",whenIso).apply(fixture)
        }
    }
    @Test fun durableOfflineStatusIntentRoundTripsAndReconcilesSafely() {
        val intended=SetCanonicalRecurringStatus("seeded","paused",whenIso)
        val queued=PendingCanonicalMutationIntent.fromMutation(
            intended,"intent-pause",PendingMutationSyncState.NEVER_SENT)
        assertEquals(PendingMutationKind.SET_RECURRING_STATUS,queued.kind)
        assertFalse(queued.isSatisfiedBy(fixture))
        val first=queued.asMutation().apply(fixture)
        assertEquals("paused",first.allCanonicalRecurringItems().first { it.id=="seeded" }.effectiveStatus)
        assertTrue(queued.isSatisfiedBy(first))
        assertEquals(first.raw,queued.asMutation().apply(first).raw)
        assertEquals(emptyList<PendingCanonicalMutationIntent>(),
            reconcileSatisfiedPendingMutations(first,listOf(queued)))
        val remoteRemoved=CanonicalFinanceDocument(Json.parseToJsonElement(
            """{"seed":{"recurring":[]},"state":{"recurringCustom":[]}}"""
        ).jsonObject)
        assertTrue(queued.isSatisfiedBy(remoteRemoved))
    }

}
