package app.myfinhub.android.feature.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class S9AuthPresentationTest {
    @Test
    fun retryTiming_roundsUpAndNeverClaimsZeroSeconds() {
        assertEquals("1 δευτερόλεπτο", formatRetrySeconds(1))
        assertEquals("1 δευτερόλεπτο", formatRetrySeconds(1_000))
        assertEquals("2 δευτερόλεπτα", formatRetrySeconds(1_001))
        assertEquals("30 δευτερόλεπτα", formatRetrySeconds(30_000))
    }
}
