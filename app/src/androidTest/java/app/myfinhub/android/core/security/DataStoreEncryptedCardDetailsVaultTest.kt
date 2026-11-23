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
class DataStoreEncryptedCardDetailsVaultTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val alias = "myfinhub-card-details-test-${System.nanoTime()}"
    private val cardId = "card-details-test-${System.nanoTime()}"
    private val vault = DataStoreEncryptedCardDetailsVault(context, AndroidKeystoreCipher(alias))

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
    fun unreadableLegacyCardDetailsKeepCiphertextForLaterValidRead() = runBlocking {
        val pan = "4242424242424242".toCharArray()
        val expiry = "12/30".toCharArray()
        vault.save(cardId, pan, expiry)
        pan.fill('\u0000')
        expiry.fill('\u0000')

        val unavailable = DataStoreEncryptedCardDetailsVault(context, object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                error("Unexpected encryption")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                error("Simulated transient Keystore failure")
        })
        assertNull(unavailable.load(cardId))

        val invalidPlaintext = DataStoreEncryptedCardDetailsVault(context, object : SecureValueCipher {
            override fun encrypt(plaintext: ByteArray): EncryptedPayload =
                error("Unexpected encryption")
            override fun decrypt(payload: EncryptedPayload): ByteArray =
                byteArrayOf(0, 1, 2)
        })
        assertNull(invalidPlaintext.load(cardId))

        // The actual encrypted record is still intact after both failures.
        val restored = vault.load(cardId)
        assertNotNull(restored)
        assertArrayEquals("4242424242424242".toCharArray(), restored!!.pan)
        assertArrayEquals("12/30".toCharArray(), restored.expiry)
        restored.clear()
    }

    @Test
    fun saveLoadDelete_roundTripsThroughKeystoreWithoutPlaintextAtRest() = runBlocking {
        val pan = "4242424242424242".toCharArray()
        val expiry = "12/30".toCharArray()

        vault.save(cardId, pan, expiry)
        pan.fill('\u0000')
        expiry.fill('\u0000')

        val loaded = vault.load(cardId)
        assertNotNull(loaded)
        assertArrayEquals("4242424242424242".toCharArray(), loaded!!.pan)
        assertArrayEquals("12/30".toCharArray(), loaded.expiry)
        loaded.clear()

        val dataStoreFile = context.filesDir.resolve("datastore/card_details_vault.preferences_pb")
        if (dataStoreFile.exists()) {
            val bytes = dataStoreFile.readBytes()
            assertFalse(bytes.containsSubsequence("4242424242424242".encodeToByteArray()))
            assertFalse(bytes.containsSubsequence("12/30".encodeToByteArray()))
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
