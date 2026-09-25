package app.myfinhub.android.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import kotlinx.coroutines.flow.first

private val Context.localPinDataStore by preferencesDataStore(name = "local_app_unlock")

class AndroidKeystorePinVerifier(
    private val context: Context,
    private val alias: String = "myfinhub.local-pin.hmac.v1",
) : LocalPinVerifier {

    override suspend fun isEnrolled(): Boolean =
        context.localPinDataStore.data.first()[PIN_DIGEST_KEY] != null

    override suspend fun enroll(pin: CharArray) {
        validatePin(pin)
        val digest = hmac(pin)
        context.localPinDataStore.edit { preferences ->
            preferences[PIN_DIGEST_KEY] = Base64.encodeToString(digest, Base64.NO_WRAP)
        }
        digest.fill(0)
    }

    override suspend fun verify(pin: CharArray): Boolean {
        if (!isValidPin(pin)) return false
        val stored = context.localPinDataStore.data.first()[PIN_DIGEST_KEY] ?: return false
        val expected = runCatching { Base64.decode(stored, Base64.NO_WRAP) }.getOrNull() ?: return false
        val actual = hmac(pin)
        return try {
            MessageDigest.isEqual(expected, actual)
        } finally {
            expected.fill(0)
            actual.fill(0)
        }
    }

    override suspend fun clear() {
        context.localPinDataStore.edit { preferences -> preferences.remove(PIN_DIGEST_KEY) }
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }

    private fun hmac(pin: CharArray): ByteArray {
        val bytes = pin.concatToString().encodeToByteArray()
        return try {
            Mac.getInstance(HMAC_SHA256).run {
                init(getOrCreateKey())
                doFinal(bytes)
            }
        } finally {
            bytes.fill(0)
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            generateKey()
        }
    }

    private fun validatePin(pin: CharArray) {
        require(isValidPin(pin)) { "Local PIN must contain 4 to 12 digits." }
    }

    private fun isValidPin(pin: CharArray): Boolean =
        pin.size in MIN_PIN_LENGTH..MAX_PIN_LENGTH && pin.all(Char::isDigit)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val HMAC_SHA256 = "HmacSHA256"
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 12
        val PIN_DIGEST_KEY = stringPreferencesKey("pin_hmac")
    }
}
