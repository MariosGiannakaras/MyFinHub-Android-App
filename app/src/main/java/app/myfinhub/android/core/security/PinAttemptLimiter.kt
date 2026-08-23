package app.myfinhub.android.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.pinThrottleDataStore by preferencesDataStore(name = "local_pin_throttle")

data class PinAttemptStatus(
    val allowed: Boolean,
    val attemptsRemaining: Int,
    val retryAfterMillis: Long = 0,
)

interface PinAttemptLimiter {
    suspend fun status(nowMillis: Long): PinAttemptStatus
    suspend fun recordFailure(nowMillis: Long): PinAttemptStatus
    suspend fun recordSuccess()
}

/**
 * Persistent local-PIN throttling with escalating lock periods.
 *
 * Only counters/timestamps are stored. PIN material and verification digests remain outside this
 * store. A successful local unlock resets the penalty level.
 */
class DataStorePinAttemptLimiter(
    private val context: Context,
    private val maxAttempts: Int = 5,
    private val lockDurationsMillis: List<Long> = DEFAULT_LOCK_DURATIONS_MILLIS,
) : PinAttemptLimiter {
    init {
        require(maxAttempts > 0)
        require(lockDurationsMillis.isNotEmpty() && lockDurationsMillis.all { it > 0 })
    }

    override suspend fun status(nowMillis: Long): PinAttemptStatus {
        val preferences = context.pinThrottleDataStore.data.first()
        val lockedUntil = preferences[LOCKED_UNTIL_KEY] ?: 0L
        if (lockedUntil > nowMillis) {
            return PinAttemptStatus(
                allowed = false,
                attemptsRemaining = 0,
                retryAfterMillis = lockedUntil - nowMillis,
            )
        }

        if (lockedUntil != 0L) {
            context.pinThrottleDataStore.edit { prefs ->
                prefs.remove(LOCKED_UNTIL_KEY)
                prefs[FAILURE_COUNT_KEY] = 0
            }
            return PinAttemptStatus(true, maxAttempts)
        }

        val failures = preferences[FAILURE_COUNT_KEY] ?: 0
        return PinAttemptStatus(
            allowed = true,
            attemptsRemaining = (maxAttempts - failures).coerceAtLeast(0),
        )
    }

    override suspend fun recordFailure(nowMillis: Long): PinAttemptStatus {
        var nextStatus = PinAttemptStatus(true, maxAttempts - 1)
        context.pinThrottleDataStore.edit { preferences ->
            val lockedUntil = preferences[LOCKED_UNTIL_KEY] ?: 0L
            if (lockedUntil > nowMillis) {
                nextStatus = PinAttemptStatus(false, 0, lockedUntil - nowMillis)
                return@edit
            }

            val failures = (preferences[FAILURE_COUNT_KEY] ?: 0) + 1
            if (failures >= maxAttempts) {
                val penaltyLevel = (preferences[PENALTY_LEVEL_KEY] ?: 0)
                    .coerceIn(0, lockDurationsMillis.lastIndex)
                val lockDuration = lockDurationsMillis[penaltyLevel]
                val nextPenaltyLevel = (penaltyLevel + 1).coerceAtMost(lockDurationsMillis.lastIndex)
                val nextLockedUntil = nowMillis + lockDuration
                preferences[FAILURE_COUNT_KEY] = 0
                preferences[LOCKED_UNTIL_KEY] = nextLockedUntil
                preferences[PENALTY_LEVEL_KEY] = nextPenaltyLevel
                nextStatus = PinAttemptStatus(false, 0, lockDuration)
            } else {
                preferences[FAILURE_COUNT_KEY] = failures
                preferences.remove(LOCKED_UNTIL_KEY)
                nextStatus = PinAttemptStatus(true, maxAttempts - failures)
            }
        }
        return nextStatus
    }

    override suspend fun recordSuccess() {
        context.pinThrottleDataStore.edit { preferences ->
            preferences.remove(FAILURE_COUNT_KEY)
            preferences.remove(LOCKED_UNTIL_KEY)
            preferences.remove(PENALTY_LEVEL_KEY)
        }
    }

    private companion object {
        val DEFAULT_LOCK_DURATIONS_MILLIS = listOf(30_000L, 120_000L, 600_000L, 3_600_000L)
        val FAILURE_COUNT_KEY = intPreferencesKey("failure_count")
        val LOCKED_UNTIL_KEY = longPreferencesKey("locked_until")
        val PENALTY_LEVEL_KEY = intPreferencesKey("penalty_level")
    }
}
