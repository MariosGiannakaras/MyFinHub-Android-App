package app.myfinhub.android.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacySafeNoticeHistoryTest {
    @Test
    fun recordAndCodec_neverPersistNoticeMessageOrDetails() {
        val notice = UserNotice(
            message = "4111111111111111",
            details = "CVV 123 token secret",
            diagnosticCode = "MFH-API-NETWORK-503",
        )

        val record = privacySafeNoticeRecord(notice, 1_000L)
        val encoded = encodePrivacySafeNoticeHistory(listOf(record))

        assertEquals("MFH-API-NETWORK-503", record.diagnosticCode)
        assertFalse(encoded.contains("4111111111111111"))
        assertFalse(encoded.contains("CVV"))
        assertFalse(encoded.contains("token"))
        assertEquals(listOf(record), decodePrivacySafeNoticeHistory(encoded))
    }

    @Test
    fun presentationPolicy_deduplicatesSameCodeOnlyInsideWindow() {
        val recent = mutableMapOf<String, Long>()
        val notice = UserNotice("safe", "safe", "MFH-NET-OFFLINE")
        val different = UserNotice("safe", "safe", "MFH-API-SERVER-500")

        assertTrue(shouldPresentNotice(notice, recent, 10_000L))
        assertFalse(shouldPresentNotice(notice, recent, 10_000L + NOTICE_DEDUPE_WINDOW_MILLIS - 1))
        assertTrue(shouldPresentNotice(different, recent, 10_001L))
        assertTrue(shouldPresentNotice(notice, recent, 10_000L + NOTICE_DEDUPE_WINDOW_MILLIS))
    }

    @Test
    fun decoder_ignoresMalformedEntriesAndBoundsUnsafeCodeCharacters() {
        val decoded = decodePrivacySafeNoticeHistory("bad\n123|mfh api network !!!\n-1|MFH-NEGATIVE")
        assertEquals(1, decoded.size)
        assertEquals("MFH_API_NETWORK", decoded.single().diagnosticCode)
    }
}
