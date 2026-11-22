package app.myfinhub.android.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.myfinhub.android.core.security.EncryptedPayload
import app.myfinhub.android.core.security.SecureValueCipher
import java.io.File
import kotlinx.coroutines.flow.first
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
    fun failedDecryptKeepsCiphertextAndPreventsSilentOverwriteEvenAfterRecreation() = runBlocking {
        val original = FinanceLocalSnapshot(
            userId = "owner-user", serverDocument = document("PENDING-DO-NOT-LOSE"),
            pendingMutations = listOf(PendingCanonicalMutationIntent.fromMutation(
                mutation = AppendCanonicalEvent(event("evt-pending", "Εκκρεμής κίνηση"), NOW),
                intentId = "intent-do-not-lose",
            )), lastSuccessfulSync = NOW,
        )
        store.save(original)
        val originalPreferences = context.financeOfflineDataStore.data.first()
        val slotHash = financeCacheOwnerSlotHash("owner-user")
        val ivKey = stringPreferencesKey("finance_iv_owner_$slotHash")
        val ciphertextKey = stringPreferencesKey("finance_ciphertext_owner_$slotHash")
        val originalIv = requireNotNull(originalPreferences[ivKey])
        val originalCiphertext = requireNotNull(originalPreferences[ciphertextKey])
        val brokenCipher = object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                throw UnsupportedOperationException("Test must never encrypt with bad cipher")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                throw IllegalStateException("Injected decrypt failure")
        }
        val failingStore = EncryptedFinanceLocalStore(context, cipher = brokenCipher)
        assertTrue(runCatching { failingStore.load("owner-user") }
            .exceptionOrNull() is FinanceLocalRestoreException)
        val afterFailure = context.financeOfflineDataStore.data.first()
        assertEquals(originalIv, afterFailure[ivKey])
        assertEquals(originalCiphertext, afterFailure[ciphertextKey])
        assertEquals(true, afterFailure[booleanPreferencesKey("finance_recovery_hold_owner_$slotHash")])
        // The safety hold is durable across store recreation and owner actions.
        assertTrue(runCatching { EncryptedFinanceLocalStore(context).load("owner-user") }
            .exceptionOrNull() is FinanceLocalRestoreException)
        assertTrue(runCatching { store.save(original.copy(pendingMutations = emptyList())) }
            .exceptionOrNull() is FinanceLocalRestoreException)
        val afterRejectedSave = context.financeOfflineDataStore.data.first()
        assertEquals(originalIv, afterRejectedSave[ivKey])
        assertEquals(originalCiphertext, afterRejectedSave[ciphertextKey])
    }

    @Test
    fun differentUsersCanSwitchWithoutOverwritingEachOthersUnsentFinancialWork() = runBlocking {
        val alice = ownerSnapshot("owner-alice", "ALICE-PRIVATE", "evt-alice")
        val bob = ownerSnapshot("owner-bob", "BOB-PRIVATE", "evt-bob")
        store.save(alice)
        assertNull(EncryptedFinanceLocalStore(context).load("owner-bob"))
        store.save(bob)
        val restoredAlice = EncryptedFinanceLocalStore(context).load("owner-alice")
        val restoredBob = EncryptedFinanceLocalStore(context).load("owner-bob")
        assertEquals(alice.serverDocument.raw, restoredAlice?.serverDocument?.raw)
        assertEquals(alice.pendingMutations, restoredAlice?.pendingMutations)
        assertEquals(bob.serverDocument.raw, restoredBob?.serverDocument?.raw)
        assertEquals(bob.pendingMutations, restoredBob?.pendingMutations)
        assertNull(EncryptedFinanceLocalStore(context).load("never-logged-in"))
    }

    @Test
    fun installedLegacySingleSlotSurvivesAnotherOwnersFirstLoginAndSave() = runBlocking {
        val existing = ownerSnapshot("legacy-owner", "LEGACY-PRIVATE", "evt-legacy")
        store.save(existing)
        val hash = financeCacheOwnerSlotHash(existing.userId)
        val ownedIv = stringPreferencesKey("finance_iv_owner_$hash")
        val ownedCiphertext = stringPreferencesKey("finance_ciphertext_owner_$hash")
        val legacyIv = stringPreferencesKey("finance_iv")
        val legacyCiphertext = stringPreferencesKey("finance_ciphertext")
        // Reconstruct exactly an installed v1 single-slot payload using the
        // real Keystore ciphertext written by the same application.
        context.financeOfflineDataStore.edit { prefs ->
            prefs[legacyIv] = requireNotNull(prefs[ownedIv])
            prefs[legacyCiphertext] = requireNotNull(prefs[ownedCiphertext])
            prefs.remove(ownedIv)
            prefs.remove(ownedCiphertext)
        }
        val prior = context.financeOfflineDataStore.data.first()
        val originalIv = requireNotNull(prior[legacyIv])
        val originalCiphertext = requireNotNull(prior[legacyCiphertext])
        assertNull(store.load("other-owner"))
        store.save(ownerSnapshot("other-owner", "OTHER-PRIVATE", "evt-other"))
        val after = context.financeOfflineDataStore.data.first()
        assertEquals(originalIv, after[legacyIv])
        assertEquals(originalCiphertext, after[legacyCiphertext])
        assertEquals(existing.pendingMutations,
            EncryptedFinanceLocalStore(context).load(existing.userId)?.pendingMutations)
        assertEquals("other-owner", EncryptedFinanceLocalStore(context).load("other-owner")?.userId)

        // Once the legacy owner saves again, only their scoped slot changes.
        store.save(existing.copy(lastSuccessfulSync = "2026-09-04T00:00:00Z"))
        assertEquals("2026-09-04T00:00:00Z",
            EncryptedFinanceLocalStore(context).load(existing.userId)?.lastSuccessfulSync)
        assertEquals(originalCiphertext, context.financeOfflineDataStore.data.first()[legacyCiphertext])
    }

    @Test
    fun corruptedOwnerSlotDoesNotDeleteOtherOwnersIndependentSnapshot() = runBlocking {
        val alice = ownerSnapshot("owner-alice", "ALICE", "evt-alice")
        val bob = ownerSnapshot("owner-bob", "BOB", "evt-bob")
        store.save(alice)
        store.save(bob)
        val failingStore = EncryptedFinanceLocalStore(context, cipher = object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                throw UnsupportedOperationException("Unexpected encrypt")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                throw IllegalStateException("Injected decrypt failure")
        })
        assertTrue(runCatching { failingStore.load(alice.userId) }
            .exceptionOrNull() is FinanceLocalRestoreException)
        assertEquals(bob.pendingMutations, store.load(bob.userId)?.pendingMutations)
        assertTrue(runCatching { store.save(alice) }
            .exceptionOrNull() is FinanceLocalRestoreException)
        store.save(bob.copy(lastSuccessfulSync = "2026-09-05T00:00:00Z"))
        assertEquals("2026-09-05T00:00:00Z",
            EncryptedFinanceLocalStore(context).load(bob.userId)?.lastSuccessfulSync)
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

    private fun ownerSnapshot(owner: String, marker: String, eventId: String) =
        FinanceLocalSnapshot(
            userId = owner, serverDocument = document(marker),
            pendingMutations = listOf(PendingCanonicalMutationIntent.fromMutation(
                mutation = AppendCanonicalEvent(event(eventId, "Εκκρεμής εργασία"), NOW),
                intentId = "pending-$eventId",
            )),
            lastSuccessfulSync = NOW,
        )

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
