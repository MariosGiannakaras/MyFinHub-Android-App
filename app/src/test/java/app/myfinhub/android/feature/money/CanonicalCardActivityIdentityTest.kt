package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalCardActivityIdentityTest {
    private fun event(id: String) = MoneyCardActivity(
        id = id, dateLabel = "2026-10-29", title = "Ιστορική κίνηση",
        amount = 12.0, kind = MoneyCardActivityKind.PURCHASE,
    )

    @Test fun onlyUniqueNonblankCanonicalEventIdsCanNavigate() {
        val counts = canonicalCardActivityIdCounts(
            listOf(event("valid"), event("duplicate"), event("duplicate"), event(""), event("   ")),
        )
        assertEquals("valid", canonicalCardActivityOpenId("valid", counts))
        assertNull(canonicalCardActivityOpenId("duplicate", counts))
        assertNull(canonicalCardActivityOpenId("", counts))
        assertNull(canonicalCardActivityOpenId("   ", counts))
        assertNull(canonicalCardActivityOpenId("unknown", counts))
    }

    @Test fun unknownAndDuplicatedHistoricalKeysHaveVisibleSourceWarnings() {
        val counts = canonicalCardActivityIdCounts(listOf(event("a"), event("a"), event("")))
        assertEquals(null, canonicalCardActivityIdentityWarning("unique", mapOf("unique" to 1)))
        assertEquals("Η κίνηση δεν διαθέτει έγκυρη ταυτότητα.",
            canonicalCardActivityIdentityWarning("", counts))
        assertEquals("Διπλή ταυτότητα ιστορικής κίνησης · απαιτείται έλεγχος.",
            canonicalCardActivityIdentityWarning("a", counts))
    }

    @Test fun removingDuplicateRestoresNavigationWithoutRewritingEventIdentity() {
        val stale = listOf(event("same"), event("same"))
        assertNull(canonicalCardActivityOpenId("same", canonicalCardActivityIdCounts(stale)))
        val corrected = stale.take(1)
        assertEquals("same", canonicalCardActivityOpenId("same", canonicalCardActivityIdCounts(corrected)))
    }
}
