package app.myfinhub.android.core.security

/**
 * Narrow encryption primitive for device-local sensitive values.
 *
 * It does not decide what may be persisted. CVV policy, session-token policy and storage lifetime
 * remain separate concerns so callers cannot accidentally turn this into a general finance cache.
 */
interface SecureValueCipher {
    fun encrypt(plaintext: ByteArray): EncryptedPayload

    fun decrypt(payload: EncryptedPayload): ByteArray
}

data class EncryptedPayload(
    val initializationVector: ByteArray,
    val ciphertext: ByteArray,
)
