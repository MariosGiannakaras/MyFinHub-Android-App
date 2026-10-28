package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileRecurringOverviewTest {
    private fun item(
        id: String,
        status: String = "active",
        amount: Double = 19.5,
        mode: String = "fixed",
    ) = RecurringManageItem(
        id = id, title = "Subscription $id",
        amount = amount, status = status,
        cadenceLabel = "Κάθε μήνα",
    ).copy(amountMode = mode)

    @Test fun webOrderAndDuplicateHistoricalIdsRemainVisibleAcrossFilters() {
        val data = listOf(item("same"), item("same", "paused"), item("other", "stopped"))
        assertEquals(listOf(0, 1, 2), mobileRecurringRows(data, MobileRecurringFilter.ALL).map { it.index })
        assertEquals(listOf(0), mobileRecurringRows(data, MobileRecurringFilter.ACTIVE).map { it.index })
        assertEquals(listOf(1, 2), mobileRecurringRows(data, MobileRecurringFilter.INACTIVE).map { it.index })
        assertEquals("same", mobileRecurringRows(data, MobileRecurringFilter.INACTIVE).first().value.id)
    }

    @Test fun statusesAreLocalizedWithoutSilentlyInventingUnknownLifecycle() {
        assertEquals("Ενεργό", mobileRecurringStatusLabel("active"))
        assertEquals("Σε παύση", mobileRecurringStatusLabel("paused"))
        assertEquals("Ολοκληρωμένο / ανενεργό", mobileRecurringStatusLabel("stopped"))
        assertEquals("Άγνωστη κατάσταση", mobileRecurringStatusLabel("unexpected"))
        assertFalse(mobileRecurringIsActive(item("p", "paused")))
    }

    @Test fun variableAndInvalidEstimatesAreNeverShownAsFixedOrPaidMoney() {
        val range = item("variable", mode = "variable").copy(estimateMin = 12.0, estimateMax = 25.0)
        assertTrue(mobileRecurringExpectedAmount(range, true).startsWith("Εκτίμηση:"))
        assertTrue(mobileRecurringExpectedAmount(range, true).contains("25"))
        assertEquals("•••• €", mobileRecurringExpectedAmount(range, false))
        assertEquals("Μη έγκυρη εκτίμηση",
            mobileRecurringExpectedAmount(range.copy(amountValidationError = "invalid"), true))
        assertEquals("Μη έγκυρο ποσό", mobileRecurringExpectedAmount(item("bad", amount = Double.NaN), true))
        assertEquals("Μη αναγνωρίσιμος τύπος ποσού",
            mobileRecurringExpectedAmount(item("u", mode = "other"), true))
    }

    @Test fun dateDisplayRejectsMalformedDatesWithoutChangingSource() {
        assertNull(mobileRecurringDateLabel(""))
        assertEquals("Μη έγκυρη ημερομηνία", mobileRecurringDateLabel("2026-02-30"))
        assertTrue(mobileRecurringDateLabel("2026-11-01").orEmpty().contains("2026"))
    }
}
