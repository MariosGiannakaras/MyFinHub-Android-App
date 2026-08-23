package app.myfinhub.android.core.security

import androidx.test.core.app.ApplicationProvider
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSessionAndPinTest {
    @Test
    fun sessionRoundTrip_usesKeystoreCipherAndClearsPersistedEnvelope() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val alias = "myfinhub.test.session.${UUID.randomUUID()}"
        val store = DataStoreEncryptedSessionStore(
            context = context,
            cipher = AndroidKeystoreCipher(alias),
        )
        val session = AuthSession(
            accessToken = "synthetic-access-token",
            refreshToken = "synthetic-refresh-token",
            expiresAtEpochSeconds = 99_999,
            userId = "synthetic-owner",
            assuranceLevel = AssuranceLevel.AAL2,
        )

        try {
            store.clear()
            store.save(session)
            assertEquals(session, store.load())
            store.clear()
            assertNull(store.load())
        } finally {
            deleteAlias(alias)
        }
    }

    @Test
    fun localPinVerifier_acceptsOnlyEnrolledPinWithoutPersistingPlaintextPin() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val alias = "myfinhub.test.pin.${UUID.randomUUID()}"
        val verifier = AndroidKeystorePinVerifier(context, alias)
        val enrolledPin = charArrayOf('4', '2', '6', '8')
        val wrongPin = charArrayOf('4', '2', '6', '9')

        try {
            verifier.clear()
            assertFalse(verifier.isEnrolled())
            verifier.enroll(enrolledPin)
            assertTrue(verifier.isEnrolled())
            assertTrue(verifier.verify(enrolledPin))
            assertFalse(verifier.verify(wrongPin))
            verifier.clear()
            assertFalse(verifier.isEnrolled())
        } finally {
            enrolledPin.fill('\u0000')
            wrongPin.fill('\u0000')
            deleteAlias(alias)
        }
    }

    private fun deleteAlias(alias: String) {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
    }
}
