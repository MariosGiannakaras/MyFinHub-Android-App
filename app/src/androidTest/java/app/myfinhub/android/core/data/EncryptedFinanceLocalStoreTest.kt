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
    fun encryptedSnapshotAndMultiplePendingTransactions_surviveStoreRecreationWithoutPlaintext() = runBlocking {
        val secretMarker = "OFFLINE-FINANCE-PLAINTEXT-MUST-NOT-APPEAR"
        val document = CanonicalFinanceDocument(
            Json.parseToJsonElement(
                """
                {
                  "schemaVersion":3,
                  "updatedAt":"2026-09-03T20:00:00Z",
                  "seed":{"accounts":[],"transactions":[]},
                  "state":{"events":[],"privateMarker":"$secretMarker"}
                }
                """.trimIndent(),
            ).jsonObject,
        )
        val first = pending("evt-offline-1", "Καφές")
        val second = pending("evt-offline-2", "Μεταφορά")

        store.save(
            FinanceLocalSnapshot(
                userId = "owner-user",
                serverDocument = document,
                pendingTransactions = listOf(first, second),
                lastSuccessfulSync = "2026-09-03T19:59:00Z",
            ),
        )

        val recreated = EncryptedFinanceLocalStore(context)
        val loaded = recreated.load("owner-user")
        requireNotNull(loaded)

        assertEquals(document.raw, loaded.serverDocument.raw)
        assertEquals(listOf("evt-offline-1", "evt-offline-2"), loaded.pendingTransactions.map { it.eventId })
        assertTrue(loaded.pendingTransactions.all { it.syncState == PendingTransactionSyncState.NEVER_SENT })
        assertEquals("2026-09-03T19:59:00Z", loaded.lastSuccessfulSync)

        val dataStoreFile = File(context.filesDir, "datastore/finance_offline_v1.preferences_pb")
        assertTrue(dataStoreFile.exists())
        assertFalse(dataStoreFile.readBytes().decodeToString().contains(secretMarker))
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
                pendingTransactions = emptyList(),
                lastSuccessfulSync = null,
            ),
        )

        assertNull(EncryptedFinanceLocalStore(context).load("different-user"))
        assertEquals("owner-user", EncryptedFinanceLocalStore(context).load("owner-user")?.userId)
    }

    private fun pending(id: String, note: String): PendingTransactionIntent = PendingTransactionIntent(
        event = JsonObject(
            mapOf(
                "id" to JsonPrimitive(id),
                "date" to JsonPrimitive("2026-09-03"),
                "kind" to JsonPrimitive("expense"),
                "amount" to JsonPrimitive(5.0),
                "note" to JsonPrimitive(note),
            ),
        ),
        nowIso = "2026-09-03T20:00:00Z",
        syncState = PendingTransactionSyncState.NEVER_SENT,
    )
}
