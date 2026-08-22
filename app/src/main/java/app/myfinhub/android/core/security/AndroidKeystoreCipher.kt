package app.myfinhub.android.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-GCM primitive backed by a non-exportable key in Android Keystore.
 *
 * Phase 1 does not persist any real secret with this class. Later CVV/session storage layers must
 * define their own aliases, lifetime, biometric policy and ciphertext persistence explicitly.
 */
class AndroidKeystoreCipher(
    private val alias: String,
) : SecureValueCipher {

    override fun encrypt(plaintext: ByteArray): EncryptedPayload {
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        return EncryptedPayload(
            initializationVector = cipher.iv.copyOf(),
            ciphertext = cipher.doFinal(plaintext),
        )
    }

    override fun decrypt(payload: EncryptedPayload): ByteArray {
        require(payload.initializationVector.isNotEmpty()) { "Initialization vector must not be empty." }
        require(payload.ciphertext.isNotEmpty()) { "Ciphertext must not be empty." }

        val cipher = Cipher.getInstance(TRANSFORMATION)
        val parameters = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.initializationVector)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), parameters)
        return cipher.doFinal(payload.ciphertext)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(alias, null) as? SecretKey
        if (existing != null) return existing

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
