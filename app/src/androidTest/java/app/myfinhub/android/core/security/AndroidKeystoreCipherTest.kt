package app.myfinhub.android.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCipherTest {
    private val alias = "myfinhub-test-${System.nanoTime()}"

    @After
    fun cleanup() {
        KeyStore.getInstance("AndroidKeyStore").apply {
            load(null)
            deleteEntry(alias)
        }
    }

    @Test
    fun encryptThenDecrypt_roundTripsWithoutStoringPlaintext() {
        val cipher = AndroidKeystoreCipher(alias)
        val plaintext = "synthetic-secret".encodeToByteArray()

        val encrypted = cipher.encrypt(plaintext)
        val decrypted = cipher.decrypt(encrypted)

        assertFalse(encrypted.ciphertext.contentEquals(plaintext))
        assertArrayEquals(plaintext, decrypted)
    }
}
