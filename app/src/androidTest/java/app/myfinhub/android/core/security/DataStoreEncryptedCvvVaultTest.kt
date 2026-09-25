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
            assertFalse(dataStoreFile.readBytes().containsSubsequence(byteArrayOf('1'.code.toByte(), '2'.code.toByte(), '3'.code.toByte())))
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
