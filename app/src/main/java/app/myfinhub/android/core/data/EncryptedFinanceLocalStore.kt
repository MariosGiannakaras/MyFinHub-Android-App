package app.myfinhub.android.core.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.EncryptedPayload
import app.myfinhub.android.core.security.SecureValueCipher
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val Context.financeOfflineDataStore by preferencesDataStore(name = "finance_offline_v1")

data class FinanceLocalSnapshot(
    val userId: String,
    val serverDocument: CanonicalFinanceDocument,
    val pendingMutations: List<PendingCanonicalMutationIntent>,
    val lastSuccessfulSync: String?,
)

/**
 * Device-local encrypted cache used only after the owner has authenticated on this installation.
 *
 * The last server-accepted document remains separate from pending local mutation intents. This
 * allows reconnect to load the newest server revision before replaying work. Every intent switches
 * to NEEDS_REVIEW before a network write boundary so an ambiguous transport failure is never
 * automatically retried after reconnect or process death.
 *
 * The DataStore/cipher identity intentionally remains `finance_offline_v1` so currently installed
 * Phase 6 builds migrate their legacy append-only pending queue without losing cached data.
 */
class EncryptedFinanceLocalStore(
    private val context: Context,
    private val cipher: SecureValueCipher = AndroidKeystoreCipher("myfinhub.finance-offline.aes.v1"),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun load(userId: String): FinanceLocalSnapshot? {
        val preferences = context.financeOfflineDataStore.data.first()
        val iv = preferences[IV_KEY] ?: return null
        val ciphertext = preferences[CIPHERTEXT_KEY] ?: return null
        return runCatching {
            val encrypted = EncryptedPayload(
                initializationVector = Base64.decode(iv, Base64.NO_WRAP),
                ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
            )
            val plaintext = cipher.decrypt(encrypted)
            try {
                val stored = json.decodeFromString<StoredFinanceLocalSnapshot>(plaintext.decodeToString())
                if (stored.userId != userId) return null

                val serverDocument = CanonicalFinanceDocument(stored.serverDocument)
                check(CanonicalDataIntegrity.validateDocument(serverDocument) == null) {
                    "Encrypted finance cache contains an invalid server snapshot."
                }

                val generic = stored.pendingMutations.map { item ->
                    PendingCanonicalMutationIntent(
                        intentId = item.intentId,
                        kind = item.kind,
                        payload = item.payload,
                        syncState = item.syncState,
                    )
                }
                val migratedLegacy = stored.pendingTransactions.map { item ->
                    val eventId = item.event.string("id").orEmpty()
                    check(eventId.isNotBlank()) {
                        "Encrypted finance cache contains a legacy pending transaction without an id."
                    }
                    PendingCanonicalMutationIntent.fromMutation(
                        mutation = AppendCanonicalEvent(event = item.event, nowIso = item.nowIso),
                        intentId = "legacy-append-$eventId",
                        syncState = when (item.syncState) {
                            LegacyPendingTransactionSyncState.NEVER_SENT -> PendingMutationSyncState.NEVER_SENT
                            LegacyPendingTransactionSyncState.NEEDS_REVIEW -> PendingMutationSyncState.NEEDS_REVIEW
                        },
                    )
                }
                val pending = (generic + migratedLegacy)
                    .distinctBy(PendingCanonicalMutationIntent::intentId)

                check(pending.map(PendingCanonicalMutationIntent::intentId).all(String::isNotBlank)) {
                    "Encrypted finance cache contains a pending mutation without an id."
                }
                check(pending.map(PendingCanonicalMutationIntent::intentId).distinct().size == pending.size) {
                    "Encrypted finance cache contains duplicate pending mutation ids."
                }

                // Validate the complete optimistic local document before exposing it to projectors.
                // This also validates reconstruction of every serialized mutation kind.
                var projectedDocument = serverDocument
                pending.forEach { item ->
                    projectedDocument = item.asMutation().apply(projectedDocument)
                    check(CanonicalDataIntegrity.validateDocument(projectedDocument) == null) {
                        "Encrypted finance cache contains an invalid pending mutation."
                    }
                }

                FinanceLocalSnapshot(
                    userId = stored.userId,
                    serverDocument = serverDocument,
                    pendingMutations = pending,
                    lastSuccessfulSync = stored.lastSuccessfulSync,
                )
            } finally {
                plaintext.fill(0)
            }
        }.getOrElse {
            clear()
            null
        }
    }

    suspend fun save(snapshot: FinanceLocalSnapshot) {
        require(snapshot.userId.isNotBlank()) { "Finance cache requires an owner id." }
        require(CanonicalDataIntegrity.validateDocument(snapshot.serverDocument) == null) {
            "Finance cache cannot persist an invalid canonical document."
        }
        require(snapshot.pendingMutations.map(PendingCanonicalMutationIntent::intentId).all(String::isNotBlank)) {
            "Finance cache cannot persist a pending mutation without an id."
        }
        require(snapshot.pendingMutations.map(PendingCanonicalMutationIntent::intentId).distinct().size == snapshot.pendingMutations.size) {
            "Finance cache cannot persist duplicate pending mutation ids."
        }

        var projectedDocument = snapshot.serverDocument
        snapshot.pendingMutations.forEach { item ->
            projectedDocument = item.asMutation().apply(projectedDocument)
            require(CanonicalDataIntegrity.validateDocument(projectedDocument) == null) {
                "Finance cache cannot persist an invalid pending mutation."
            }
        }

        val stored = StoredFinanceLocalSnapshot(
            userId = snapshot.userId,
            serverDocument = snapshot.serverDocument.raw,
            pendingMutations = snapshot.pendingMutations.map { pending ->
                StoredPendingMutation(
                    intentId = pending.intentId,
                    kind = pending.kind,
                    payload = pending.payload,
                    syncState = pending.syncState,
                )
            },
            // Legacy field is intentionally written empty. It remains in the schema only so an
            // installed append-only v1 cache can be decoded and migrated in place.
            pendingTransactions = emptyList(),
            lastSuccessfulSync = snapshot.lastSuccessfulSync,
        )
        val plaintext = json.encodeToString(stored).encodeToByteArray()
        val encrypted = try {
            cipher.encrypt(plaintext)
        } finally {
            plaintext.fill(0)
        }
        context.financeOfflineDataStore.edit { preferences ->
            preferences[IV_KEY] = Base64.encodeToString(encrypted.initializationVector, Base64.NO_WRAP)
            preferences[CIPHERTEXT_KEY] = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
        }
    }

    suspend fun clear() {
        context.financeOfflineDataStore.edit { preferences ->
            preferences.remove(IV_KEY)
            preferences.remove(CIPHERTEXT_KEY)
        }
    }

    @Serializable
    private data class StoredFinanceLocalSnapshot(
        val userId: String,
        val serverDocument: JsonObject,
        val pendingMutations: List<StoredPendingMutation> = emptyList(),
        val pendingTransactions: List<StoredPendingTransaction> = emptyList(),
        val lastSuccessfulSync: String? = null,
    )

    @Serializable
    private data class StoredPendingMutation(
        val intentId: String,
        val kind: PendingMutationKind,
        val payload: JsonObject,
        val syncState: PendingMutationSyncState,
    )

    /** Legacy append-only DTO retained solely for migration from the installed Phase 6 cache. */
    @Serializable
    private data class StoredPendingTransaction(
        val event: JsonObject,
        val nowIso: String,
        val syncState: LegacyPendingTransactionSyncState,
    )

    @Serializable
    private enum class LegacyPendingTransactionSyncState {
        NEVER_SENT,
        NEEDS_REVIEW,
    }

    private companion object {
        val IV_KEY = stringPreferencesKey("finance_iv")
        val CIPHERTEXT_KEY = stringPreferencesKey("finance_ciphertext")
    }
}
