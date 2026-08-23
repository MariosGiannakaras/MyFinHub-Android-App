package app.myfinhub.android.core.security

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinAttemptLimiterTest {
    private lateinit var limiter: DataStorePinAttemptLimiter

    @Before
    fun resetLimiter() = runBlocking {
        limiter = DataStorePinAttemptLimiter(ApplicationProvider.getApplicationContext())
        limiter.recordSuccess()
    }

    @Test
    fun fifthFailureLocksPinFallbackAndDoesNotExposePinMaterial() = runBlocking {
        val now = 1_000L

        repeat(4) { index ->
            val status = limiter.recordFailure(now + index)
            assertTrue(status.allowed)
            assertEquals(4 - index, status.attemptsRemaining)
        }

        val locked = limiter.recordFailure(now + 4)
        assertFalse(locked.allowed)
        assertEquals(0, locked.attemptsRemaining)
        assertEquals(30_000L, locked.retryAfterMillis)
    }

    @Test
    fun lockExpiresAndSuccessfulUnlockResetsFailureCount() = runBlocking {
        val now = 5_000L
        repeat(5) { limiter.recordFailure(now) }

        assertFalse(limiter.status(now + 29_999).allowed)
        assertTrue(limiter.status(now + 30_000).allowed)

        limiter.recordFailure(now + 30_001)
        limiter.recordSuccess()

        val reset = limiter.status(now + 30_002)
        assertTrue(reset.allowed)
        assertEquals(5, reset.attemptsRemaining)
    }
}
