package app.myfinhub.android.core.security

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.myfinhub.android.core.auth.AuthSession
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.authSessionDataStore by preferencesDataStore(name = "auth_session")

class DataStoreEncryptedSessionStore(
    private val context: Context,
    private val cipher: SecureValueCipher,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : SessionStore {

    override suspend fun load(): AuthSession? {
        val preferences = context.authSessionDataStore.data.first()
        val iv = preferences[IV_KEY] ?: return null
        val ciphertext = preferences[CIPHERTEXT_KEY] ?: return null

        return runCatching {
            val payload = EncryptedPayload(
                initializationVector = Base64.decode(iv, Base64.NO_WRAP),
                ciphertext = Base64.decode(ciphertext, Base64.NO_WRAP),
            )
            val plaintext = cipher.decrypt(payload)
            try {
                json.decodeFromString<AuthSession>(plaintext.decodeToString())
            } finally {
                plaintext.fill(0)
            }
        }.getOrElse {
            clear()
            null
        }
    }

    override suspend fun save(session: AuthSession) {
        val plaintext = json.encodeToString(session).encodeToByteArray()
        val encrypted = try {
            cipher.encrypt(plaintext)
        } finally {
            plaintext.fill(0)
        }

        context.authSessionDataStore.edit { preferences ->
            preferences[IV_KEY] = Base64.encodeToString(encrypted.initializationVector, Base64.NO_WRAP)
            preferences[CIPHERTEXT_KEY] = Base64.encodeToString(encrypted.ciphertext, Base64.NO_WRAP)
        }
    }

    override suspend fun clear() {
        context.authSessionDataStore.edit { preferences ->
            preferences.remove(IV_KEY)
            preferences.remove(CIPHERTEXT_KEY)
        }
    }

    private companion object {
        val IV_KEY = stringPreferencesKey("session_iv")
        val CIPHERTEXT_KEY = stringPreferencesKey("session_ciphertext")
    }
}
