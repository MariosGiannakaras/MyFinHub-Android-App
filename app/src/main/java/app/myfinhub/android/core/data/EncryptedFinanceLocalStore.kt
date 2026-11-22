package app.myfinhub.android.core.data

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import app.myfinhub.android.core.security.EncryptedPayload
import app.myfinhub.android.core.security.SecureValueCipher
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

internal val Context.financeOfflineDataStore by preferencesDataStore(name = "finance_offline_v1")

/** Do not expose raw user IDs as DataStore preference keys. Fixed across versions/installs. */
internal fun financeCacheOwnerSlotHash(userId: String): String {
    require(userId.isNotBlank()) { "Owner-scoped finance cache requires a stable user id." }
    return MessageDigest.getInstance("SHA-256")
        .digest(userId.toByteArray(Charsets.UTF_8))
        .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}

private data class FinanceCacheSlot(
    val iv: Preferences.Key<String>,
    val ciphertext: Preferences.Key<String>,
    val hold: Preferences.Key<Boolean>,
)

private fun ownerFinanceCacheSlot(userId: String): FinanceCacheSlot {
    val ownerHash = financeCacheOwnerSlotHash(userId)
    return FinanceCacheSlot(
        iv = stringPreferencesKey("finance_iv_owner_$ownerHash"),
        ciphertext = stringPreferencesKey("finance_ciphertext_owner_$ownerHash"),
        hold = booleanPreferencesKey("finance_recovery_hold_owner_$ownerHash"),
    )
}

data class FinanceLocalSnapshot(
    val userId: String,
    val serverDocument: CanonicalFinanceDocument,
    val pendingMutations: List<PendingCanonicalMutationIntent>,
    val lastSuccessfulSync: String?,
)

/** A durable encrypted snapshot could not be reconstructed. Never clear or overwrite it implicitly. */
class FinanceLocalRestoreException : IllegalStateException(
    "Encrypted offline finance snapshot needs explicit recovery; original bytes are retained.",
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
    private val preferencesStore: DataStore<Preferences> = context.financeOfflineDataStore,
) {
    suspend fun load(userId: String): FinanceLocalSnapshot? {
        // The DataStore read itself may throw (I/O or unreadable preference file),
        // before any encrypted IV/ciphertext is available. That is NOT an empty
        // local queue: fail closed so the ViewModel cannot GET/replay/overwrite it.
        val preferences = try {
            preferencesStore.data.first()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            throw FinanceLocalRestoreException()
        }
        val ownerSlot = ownerFinanceCacheSlot(userId)
        // A new owner must never replace another owner's encrypted pending
        // changes in the historical single-slot v1 store. Prefer an existing
        // scoped copy; otherwise read v1 as a non-destructive fallback.
        val hasOwnedSlot = preferences[ownerSlot.iv] != null ||
            preferences[ownerSlot.ciphertext] != null ||
            preferences[ownerSlot.hold] == true
        val slot = if (hasOwnedSlot) ownerSlot else LEGACY_SLOT
        if (preferences[RECOVERY_HOLD_KEY] == true || preferences[slot.hold] == true) {
            throw FinanceLocalRestoreException()
        }
        val iv = preferences[slot.iv]
        val ciphertext = preferences[slot.ciphertext]
        if (iv == null && ciphertext == null) return null
        if (iv == null || ciphertext == null) {
            holdForRecovery(slot.hold)
            throw FinanceLocalRestoreException()
        }
        return try {
            val encrypted = EncryptedPayload(
                initializationVector = Base64.decode(iv, Base64.NO_WRAP),
                ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
            )
            val plaintext = cipher.decrypt(encrypted)
            try {
                val stored = json.decodeFromString<StoredFinanceLocalSnapshot>(plaintext.decodeToString())
                if (stored.userId != userId) {
                    // Only the old unscoped v1 cache may belong to another
                    // user. A mismatch inside a scoped slot is corruption.
                    if (hasOwnedSlot) error("Scoped finance cache owner mismatch.")
                    return null
                }

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
                // Never use distinctBy here: it silently loses user-approved work when
                // two different stored intents happen to share an ID. Preserve only
                // exact v1/generic migration duplicates; conflicting IDs fail closed.
                val pending = mergeRestoredPendingMutations(generic, migratedLegacy)

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
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // A bad queue, decrypt failure or unsupported payload must not be
            // mistaken for an empty cache. Keep the encrypted evidence intact.
            holdForRecovery(slot.hold)
            throw FinanceLocalRestoreException()
        }
    }

    private suspend fun holdForRecovery(key: Preferences.Key<Boolean>) {
        try {
            preferencesStore.edit { it[key] = true }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Even if a broken preferences file prevents persisting the hold,
            // never let this load attempt become an empty-cache success.
            throw FinanceLocalRestoreException()
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
        val ownerSlot = ownerFinanceCacheSlot(snapshot.userId)
        preferencesStore.edit { preferences ->
            if (preferences[RECOVERY_HOLD_KEY] == true ||
                preferences[ownerSlot.hold] == true
            ) throw FinanceLocalRestoreException()
            // Installed v1 owner bytes deliberately remain in place. The
            // source is superseded only for its own owner after a valid save.
            preferences[ownerSlot.iv] = Base64.encodeToString(encrypted.initializationVector, Base64.NO_WRAP)
            preferences[ownerSlot.ciphertext] = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
        }
    }

    /** Explicit destructive reset of ALL owners. Never called automatically by load/session changes. */
    suspend fun clear() {
        preferencesStore.edit { preferences -> preferences.clear() }
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
        val RECOVERY_HOLD_KEY = booleanPreferencesKey("finance_recovery_hold")
        val IV_KEY = stringPreferencesKey("finance_iv")
        val CIPHERTEXT_KEY = stringPreferencesKey("finance_ciphertext")
        val LEGACY_SLOT = FinanceCacheSlot(IV_KEY, CIPHERTEXT_KEY, RECOVERY_HOLD_KEY)
    }
}
