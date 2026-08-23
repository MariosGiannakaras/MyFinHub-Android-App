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
    fun fifthFailureLocksPinFallback() = runBlocking {
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
    fun repeatedLockCyclesEscalateUntilSuccessfulUnlockResetsPenalty() = runBlocking {
        val firstCycle = 5_000L
        repeat(5) { limiter.recordFailure(firstCycle) }

        assertFalse(limiter.status(firstCycle + 29_999).allowed)
        assertTrue(limiter.status(firstCycle + 30_000).allowed)

        val secondCycle = firstCycle + 30_001
        repeat(4) { limiter.recordFailure(secondCycle) }
        val secondLock = limiter.recordFailure(secondCycle)
        assertFalse(secondLock.allowed)
        assertEquals(120_000L, secondLock.retryAfterMillis)

        limiter.recordSuccess()
        val resetAt = secondCycle + 120_001
        val reset = limiter.status(resetAt)
        assertTrue(reset.allowed)
        assertEquals(5, reset.attemptsRemaining)

        repeat(4) { limiter.recordFailure(resetAt) }
        val lockAfterReset = limiter.recordFailure(resetAt)
        assertEquals(30_000L, lockAfterReset.retryAfterMillis)
    }
}
