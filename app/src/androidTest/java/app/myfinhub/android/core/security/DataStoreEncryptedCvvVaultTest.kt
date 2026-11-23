package app.myfinhub.android.core.security

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.security.KeyStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataStoreEncryptedCvvVaultTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "myfinhub-cvv-test-${System.nanoTime()}"
    private val cardId = "card-cvv-test-${System.nanoTime()}"
    private val vault = DataStoreEncryptedCvvVault(context, AndroidKeystoreCipher(alias))

    @After
    fun cleanup() {
        runBlocking {
            runCatching { vault.delete(cardId) }
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                deleteEntry(alias)
            }
        }
    }

    @Test
    fun unreadableLegacyCvvKeepsCiphertextForLaterValidRead() = runBlocking {
        val original = "321".toCharArray()
        vault.save(cardId, original)
        original.fill('\u0000')

        val unavailable = DataStoreEncryptedCvvVault(context, object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                error("Unexpected encryption")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                error("Simulated transient Keystore failure")
        })
        assertNull(unavailable.load(cardId))

        val invalidPlaintext = DataStoreEncryptedCvvVault(context, object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                error("Unexpected encryption")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                byteArrayOf(0, 1, 2)
        })
        assertNull(invalidPlaintext.load(cardId))

        val restored = vault.load(cardId)
        assertNotNull(restored)
        assertArrayEquals("321".toCharArray(), restored!!)
        restored.fill('\u0000')
    }

    @Test
    fun saveLoadDelete_roundTripsThroughKeystoreWithoutPlaintextAtRest() = runBlocking {
        val cvv = charArrayOf('1', '2', '3')

        vault.save(cardId, cvv)
        cvv.fill('\u0000')
        val loaded = vault.load(cardId)

        assertNotNull(loaded)
        assertArrayEquals(charArrayOf('1', '2', '3'), loaded!!)
        loaded.fill('\u0000')

        val dataStoreFile = context.filesDir.resolve("datastore/cvv_vault.preferences_pb")
        if (dataStoreFile.exists()) {
            // Preferences DataStore stores string values as protobuf field 1
            // (0x0A) with a length prefix. A bare "123" occurrence anywhere in
            // random Base64 ciphertext is NOT evidence that CVV was persisted
            // as a plaintext value; that probabilistic test was flaky on AVD.
            val encodedPlaintextValue = byteArrayOf(0x0A, 0x03, 0x31, 0x32, 0x33)
            assertFalse(
                "CVV must not be persisted as a plaintext protobuf string value",
                dataStoreFile.readBytes().containsSubsequence(encodedPlaintextValue),
            )
        }

        vault.delete(cardId)
        assertNull(vault.load(cardId))
    }
}

private fun ByteArray.containsSubsequence(needle: ByteArray): Boolean {
    if (needle.isEmpty() || needle.size > size) return false
    return indices.any { start ->
        start + needle.size <= size && needle.indices.all { offset -> this[start + offset] == needle[offset] }
    }
}
