package app.myfinhub.android.core.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedFinanceLocalStoreTest {
    private lateinit var context: Context
    private lateinit var store: EncryptedFinanceLocalStore

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        store = EncryptedFinanceLocalStore(context)
        store.clear()
    }

    @After
    fun tearDown() = runBlocking {
        store.clear()
    }

    @Test
    fun encryptedSnapshotAndMixedPendingMutations_surviveStoreRecreationWithoutPlaintext() = runBlocking {
        val secretMarker = "OFFLINE-FINANCE-PLAINTEXT-MUST-NOT-APPEAR"
        val document = document(secretMarker)
        val append = PendingCanonicalMutationIntent.fromMutation(
            mutation = AppendCanonicalEvent(event("evt-offline-1", "Καφές"), NOW),
            intentId = "intent-append",
        )
        val edit = PendingCanonicalMutationIntent.fromMutation(
            mutation = EditCanonicalActivity("evt-existing", "Offline edit", "Food", NOW),
            intentId = "intent-edit",
        )
        val budget = PendingCanonicalMutationIntent.fromMutation(
            mutation = UpsertOverallBudget("2026-09", 420.0, 75, "budget-offline", NOW),
            intentId = "intent-budget",
        )
        val card = PendingCanonicalMutationIntent.fromMutation(
            mutation = DeactivateCanonicalCard("card-1", NOW),
            intentId = "intent-card",
            syncState = PendingMutationSyncState.NEEDS_REVIEW,
        )

        store.save(
            FinanceLocalSnapshot(
                userId = "owner-user",
                serverDocument = document,
                pendingMutations = listOf(append, edit, budget, card),
                lastSuccessfulSync = "2026-09-03T19:59:00Z",
            ),
        )

        val recreated = EncryptedFinanceLocalStore(context)
        val loaded = recreated.load("owner-user")
        requireNotNull(loaded)

        assertEquals(document.raw, loaded.serverDocument.raw)
        assertEquals(
            listOf("intent-append", "intent-edit", "intent-budget", "intent-card"),
            loaded.pendingMutations.map(PendingCanonicalMutationIntent::intentId),
        )
        assertEquals(PendingMutationKind.APPEND_EVENT, loaded.pendingMutations[0].kind)
        assertEquals(PendingMutationKind.EDIT_ACTIVITY, loaded.pendingMutations[1].kind)
        assertEquals(PendingMutationKind.UPSERT_OVERALL_BUDGET, loaded.pendingMutations[2].kind)
        assertEquals(PendingMutationKind.DEACTIVATE_CARD, loaded.pendingMutations[3].kind)
        assertEquals(PendingMutationSyncState.NEEDS_REVIEW, loaded.pendingMutations[3].syncState)
        assertEquals("2026-09-03T19:59:00Z", loaded.lastSuccessfulSync)

        val optimistic = loaded.pendingMutations.fold(loaded.serverDocument) { current, pending ->
            pending.asMutation().apply(current)
        }
        assertTrue(optimistic.state.array("events").any { (it as? JsonObject)?.string("id") == "evt-offline-1" })
        assertEquals("Offline edit", optimistic.state.array("events").mapNotNull { it as? JsonObject }.first { it.string("id") == "evt-existing" }.string("note"))
        assertEquals(false, optimistic.state.array("cards").mapNotNull { it as? JsonObject }.first { it.string("id") == "card-1" }.bool("active"))

        val dataStoreFile = File(context.filesDir, "datastore/finance_offline_v1.preferences_pb")
        assertTrue(dataStoreFile.exists())
        assertFalse(dataStoreFile.readBytes().decodeToString().contains(secretMarker))
        assertFalse(dataStoreFile.readBytes().decodeToString().contains("Offline edit"))
    }

    @Test
    fun snapshot_isScopedToAuthenticatedUser() = runBlocking {
        val document = CanonicalFinanceDocument(
            JsonObject(
                mapOf(
                    "schemaVersion" to JsonPrimitive(3),
                    "seed" to JsonObject(emptyMap()),
                    "state" to JsonObject(emptyMap()),
                ),
            ),
        )
        store.save(
            FinanceLocalSnapshot(
                userId = "owner-user",
                serverDocument = document,
                pendingMutations = emptyList(),
                lastSuccessfulSync = null,
            ),
        )

        assertNull(EncryptedFinanceLocalStore(context).load("different-user"))
        assertEquals("owner-user", EncryptedFinanceLocalStore(context).load("owner-user")?.userId)
    }

    private fun document(marker: String): CanonicalFinanceDocument = CanonicalFinanceDocument(
        Json.parseToJsonElement(
            """
            {
              "schemaVersion":3,
              "updatedAt":"$NOW",
              "seed":{"accounts":[],"transactions":[]},
              "state":{
                "events":[{"id":"evt-existing","date":"2026-09-03","kind":"expense","amount":9.0,"note":"Original","category":"Old"}],
                "cards":[{"id":"card-1","active":true,"nickname":"Test"}],
                "privateMarker":"$marker"
              }
            }
            """.trimIndent(),
        ).jsonObject,
    )

    private fun event(id: String, note: String): JsonObject = JsonObject(
        mapOf(
            "id" to JsonPrimitive(id),
            "date" to JsonPrimitive("2026-09-03"),
            "kind" to JsonPrimitive("expense"),
            "amount" to JsonPrimitive(5.0),
            "note" to JsonPrimitive(note),
        ),
    )

    private companion object {
        const val NOW = "2026-09-03T20:00:00Z"
    }
}
