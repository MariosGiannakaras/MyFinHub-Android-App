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

enum class PendingTransactionSyncState {
    NEVER_SENT,
    NEEDS_REVIEW,
}

data class PendingTransactionIntent(
    val event: JsonObject,
    val nowIso: String,
    val syncState: PendingTransactionSyncState = PendingTransactionSyncState.NEVER_SENT,
) {
    val eventId: String
        get() = event.string("id").orEmpty()

    fun asMutation(): AppendCanonicalEvent = AppendCanonicalEvent(event = event, nowIso = nowIso)
}

data class FinanceLocalSnapshot(
    val userId: String,
    val serverDocument: CanonicalFinanceDocument,
    val pendingTransactions: List<PendingTransactionIntent>,
    val lastSuccessfulSync: String?,
)

/**
 * Device-local encrypted cache used only after the owner has authenticated on this installation.
 *
 * The server document remains canonical. The cached document is the last server-accepted snapshot;
 * offline-created transactions are stored separately with stable ids and replayed only after a
 * fresh server load. A transaction switches to NEEDS_REVIEW before any network write attempt so an
 * ambiguous transport failure can never be blindly retried on reconnect.
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
                FinanceLocalSnapshot(
                    userId = stored.userId,
                    serverDocument = CanonicalFinanceDocument(stored.serverDocument),
                    pendingTransactions = stored.pendingTransactions.mapNotNull { pending ->
                        val id = pending.event.string("id")
                        if (id.isNullOrBlank()) null else PendingTransactionIntent(
                            event = pending.event,
                            nowIso = pending.nowIso,
                            syncState = pending.syncState,
                        )
                    },
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
        val stored = StoredFinanceLocalSnapshot(
            userId = snapshot.userId,
            serverDocument = snapshot.serverDocument.raw,
            pendingTransactions = snapshot.pendingTransactions.map { pending ->
                StoredPendingTransaction(
                    event = pending.event,
                    nowIso = pending.nowIso,
                    syncState = pending.syncState,
                )
            },
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
        val pendingTransactions: List<StoredPendingTransaction> = emptyList(),
        val lastSuccessfulSync: String? = null,
    )

    @Serializable
    private data class StoredPendingTransaction(
        val event: JsonObject,
        val nowIso: String,
        val syncState: PendingTransactionSyncState,
    )

    private companion object {
        val IV_KEY = stringPreferencesKey("finance_iv")
        val CIPHERTEXT_KEY = stringPreferencesKey("finance_ciphertext")
    }
}
