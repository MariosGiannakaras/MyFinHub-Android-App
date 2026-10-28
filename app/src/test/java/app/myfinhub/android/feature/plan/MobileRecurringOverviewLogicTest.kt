package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRecurringOverviewLogicTest {
    @Test fun onlyValidCanonicalIsoDatesAreLocalized() {
        assertEquals("28/10/2026", mobileRecurringDateLabel("2026-10-28"))
        assertEquals("29/02/2024", mobileRecurringDateLabel("2024-02-29"))
        assertEquals("2026-02-29", mobileRecurringDateLabel("2026-02-29"))
        assertEquals("legacy", mobileRecurringDateLabel("legacy"))
        assertEquals("", mobileRecurringDateLabel(""))
    }

    @Test fun statusesUsePhoneLabelsWithoutInventingUnknownState() {
        assertEquals("Ενεργή", mobileRecurringStatusLabel("active"))
        assertEquals("Σε παύση", mobileRecurringStatusLabel("paused"))
        assertEquals("Διακομμένη", mobileRecurringStatusLabel("stopped"))
        assertEquals("unknown-state", mobileRecurringStatusLabel("unknown-state"))
    }

    @Test fun paymentHistoryIsInitiallyBoundedAndEventuallyComplete() {
        assertEquals(0, mobileRecurringVisibleHistoryCount(0, 1))
        assertEquals(5, mobileRecurringVisibleHistoryCount(21, 1))
        assertEquals(10, mobileRecurringVisibleHistoryCount(21, 2))
        assertEquals(21, mobileRecurringVisibleHistoryCount(21, 5))
        assertEquals(0, mobileRecurringVisibleHistoryCount(-3, 1))
        assertEquals(5, mobileRecurringVisibleHistoryCount(12, 0))
        assertEquals(Int.MAX_VALUE,
            mobileRecurringVisibleHistoryCount(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
