package app.myfinhub.android.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.MessageDigest
import kotlinx.coroutines.flow.first

private val Context.cardDetailsVaultDataStore by preferencesDataStore(name = "card_details_vault")

data class LocalCardDetails(
    val pan: CharArray,
    val expiry: CharArray,
) {
    fun clear() {
        pan.fill('\u0000')
        expiry.fill('\u0000')
    }
}

interface CardDetailsVault {
    suspend fun load(cardId: String): LocalCardDetails?
    suspend fun save(cardId: String, pan: CharArray, expiry: CharArray)
    suspend fun delete(cardId: String)
}

/**
 * Device-local PAN/expiry storage backed by a non-exportable Android Keystore AES-GCM key.
 *
 * Full card values never enter the canonical finance document or Android backup. DataStore keeps
 * only ciphertext and IV; the production manifest already disables app backup.
 */
class DataStoreEncryptedCardDetailsVault(
    private val context: Context,
    private val cipher: SecureValueCipher,
) : CardDetailsVault {
    override suspend fun load(cardId: String): LocalCardDetails? {
        val normalizedCardId = normalizeCardId(cardId)
        val preferences = context.cardDetailsVaultDataStore.data.first()
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

        try {
            val separator = plaintext.indexOf(0)
            if (separator !in 12..19 || separator >= plaintext.lastIndex) {
                delete(normalizedCardId)
                return null
            }
            val pan = CharArray(separator) { index -> plaintext[index].toInt().toChar() }
            val expiryLength = plaintext.size - separator - 1
            val expiry = CharArray(expiryLength) { index -> plaintext[separator + 1 + index].toInt().toChar() }
            if (!validPan(pan) || !validExpiry(expiry)) {
                pan.fill('\u0000')
                expiry.fill('\u0000')
                delete(normalizedCardId)
                return null
            }
            return LocalCardDetails(pan, expiry)
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun save(cardId: String, pan: CharArray, expiry: CharArray) {
        val normalizedCardId = normalizeCardId(cardId)
        require(validPan(pan)) { "PAN must contain 12 to 19 digits." }
        require(validExpiry(expiry)) { "Expiry must be MM/YY or MM/YYYY." }

        val plaintext = ByteArray(pan.size + 1 + expiry.size)
        try {
            pan.forEachIndexed { index, char -> plaintext[index] = char.code.toByte() }
            plaintext[pan.size] = 0
            expiry.forEachIndexed { index, char -> plaintext[pan.size + 1 + index] = char.code.toByte() }
            val encrypted = cipher.encrypt(plaintext)
            context.cardDetailsVaultDataStore.edit { preferences ->
                preferences[ivKey(normalizedCardId)] = Base64.encodeToString(encrypted.initializationVector, Base64.NO_WRAP)
                preferences[ciphertextKey(normalizedCardId)] = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
            }
        } finally {
            plaintext.fill(0)
        }
    }

    override suspend fun delete(cardId: String) {
        val normalizedCardId = normalizeCardId(cardId)
        context.cardDetailsVaultDataStore.edit { preferences ->
            preferences.remove(ivKey(normalizedCardId))
            preferences.remove(ciphertextKey(normalizedCardId))
        }
    }

    private fun normalizeCardId(cardId: String): String = cardId.trim().also {
        require(CARD_ID_REGEX.matches(it)) { "Invalid card id." }
    }

    private fun validPan(value: CharArray): Boolean =
        value.size in 12..19 && value.all { it in '0'..'9' }

    private fun validExpiry(value: CharArray): Boolean {
        val text = value.concatToString()
        return Regex("^(0[1-9]|1[0-2])/(\\d{2}|\\d{4})$").matches(text)
    }

    private fun ivKey(cardId: String) = stringPreferencesKey("card_${keySuffix(cardId)}_iv")
    private fun ciphertextKey(cardId: String) = stringPreferencesKey("card_${keySuffix(cardId)}_ciphertext")

    private fun keySuffix(cardId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cardId.encodeToByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP or Base64.URL_SAFE).trimEnd('=')
    }

    private companion object {
        val CARD_ID_REGEX = Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$")
    }
}
