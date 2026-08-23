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

class DataStorePinAttemptLimiter(
    private val context: Context,
    private val maxAttempts: Int = 5,
    private val lockDurationMillis: Long = 30_000,
) : PinAttemptLimiter {
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
                val nextLockedUntil = nowMillis + lockDurationMillis
                preferences[FAILURE_COUNT_KEY] = 0
                preferences[LOCKED_UNTIL_KEY] = nextLockedUntil
                nextStatus = PinAttemptStatus(false, 0, lockDurationMillis)
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
        }
    }

    private companion object {
        val FAILURE_COUNT_KEY = intPreferencesKey("failure_count")
        val LOCKED_UNTIL_KEY = longPreferencesKey("locked_until")
    }
}
