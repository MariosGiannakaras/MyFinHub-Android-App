package app.myfinhub.android.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

private val Context.cvvVaultDataStore by preferencesDataStore(name = "cvv_vault")

interface CvvVault {
    suspend fun load(cardId: String): CharArray?
    suspend fun save(cardId: String, cvv: CharArray)
    suspend fun delete(cardId: String)
}

/**
 * Device-local CVV storage.
 *
 * Only AES-GCM ciphertext/IV are persisted. The Keystore key is non-exportable and uses a
 * dedicated alias supplied by the caller. CVV plaintext never goes to the server, canonical
 * finance state, logs or Android backup (the production manifest disables app backup).
 */
class DataStoreEncryptedCvvVault(
    private val context: Context,
    private val cipher: SecureValueCipher,
) : CvvVault {
    override suspend fun load(cardId: String): CharArray? {
        val normalizedCardId = normalizeCardId(cardId)
        val preferences = context.cvvVaultDataStore.data.first()
        val iv = preferences[ivKey(normalizedCardId)] ?: return null
        val ciphertext = preferences[ciphertextKey(normalizedCardId)] ?: return null

        val plaintext = runCatching {
            cipher.decrypt(
                EncryptedPayload(
                    initializationVector = Base64.decode(iv, Base64.NO_WRAP),
                    ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
                ),
            )
        }.getOrElse {
            delete(normalizedCardId)
            return null
        }

        return try {
            if (plaintext.size !in 3..4 || plaintext.any { it < DIGIT_ZERO || it > DIGIT_NINE }) {
                delete(normalizedCardId)
                null
            } else {
                CharArray(plaintext.size) { index -> plaintext[index].toInt().toChar() }
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun save(cardId: String, cvv: CharArray) {
        val normalizedCardId = normalizeCardId(cardId)
        require(cvv.size in 3..4 && cvv.all { it in '0'..'9' }) { "CVV must contain 3 or 4 digits." }

        val plaintext = ByteArray(cvv.size) { index -> cvv[index].code.toByte() }
        val encrypted = try {
            cipher.encrypt(plaintext)
        } finally {
            plaintext.fill(0)
        }

        context.cvvVaultDataStore.edit { preferences ->
            preferences[ivKey(normalizedCardId)] = Base64.encodeToString(
                encrypted.initializationVector,
                Base64.NO_WRAP,
            )
            preferences[ciphertextKey(normalizedCardId)] = Base64.encodeToString(
                encrypted.ciphertext,
                Base64.NO_WRAP,
            )
        }
    }

    override suspend fun delete(cardId: String) {
        val normalizedCardId = normalizeCardId(cardId)
        context.cvvVaultDataStore.edit { preferences ->
            preferences.remove(ivKey(normalizedCardId))
            preferences.remove(ciphertextKey(normalizedCardId))
        }
    }

    private fun normalizeCardId(cardId: String): String = cardId.trim().also {
        require(CARD_ID_REGEX.matches(it)) { "Invalid card id." }
    }

    private fun ivKey(cardId: String) = stringPreferencesKey("cvv_${keySuffix(cardId)}_iv")
    private fun ciphertextKey(cardId: String) = stringPreferencesKey("cvv_${keySuffix(cardId)}_ciphertext")

    private fun keySuffix(cardId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cardId.encodeToByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE).trimEnd('=')
    }

    private companion object {
        val CARD_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
        const val DIGIT_ZERO: Byte = 48
        const val DIGIT_NINE: Byte = 57
    }
}
