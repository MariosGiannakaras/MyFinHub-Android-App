package app.myfinhub.android.feature.plan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MobileScheduledPresentationTest {
    private val due = ScheduledManageItem(
        "bill", "Ρεύμα", "expense", "2026-11-02", 45.0, "due",
    )

    @Test fun onlyKnownUniqueActionableScheduledRecordsAreWritable() {
        assertTrue(mobileScheduledActionable(due, 1))
        assertTrue(mobileScheduledActionable(due.copy(kind="income"), 1))
        assertTrue(mobileScheduledActionable(due.copy(kind="transfer",status="upcoming"), 1))
        assertFalse(mobileScheduledActionable(due, 2))
        assertFalse(mobileScheduledActionable(due.copy(id=""), 1))
        assertFalse(mobileScheduledActionable(due.copy(kind="unexpected"), 1))
        assertFalse(mobileScheduledActionable(due.copy(status="completed"), 1))
        assertFalse(mobileScheduledActionable(due.copy(status="unexpected"), 1))
    }

    @Test fun invalidMoneyAndRawSourceDatesFailClosed() {
        assertEquals("•••• €",mobileScheduledAmountLabel(Double.NaN,false))
        assertEquals("Μη έγκυρο προγραμματισμένο ποσό",mobileScheduledAmountLabel(Double.NaN,true))
        assertEquals("Μη έγκυρο προγραμματισμένο ποσό",mobileScheduledAmountLabel(Double.POSITIVE_INFINITY,true))
        assertEquals("Μη έγκυρο προγραμματισμένο ποσό",mobileScheduledAmountLabel(-1.0,true))
        assertTrue(mobileScheduledAmountLabel(45.0,true).contains("45"))
        assertEquals("02/11/2026",mobileScheduledDateLabel("2026-11-02"))
        assertEquals("invalid",mobileScheduledDateLabel("invalid"))
        assertEquals("Χωρίς ημερομηνία",mobileScheduledDateLabel(""))
        assertEquals("Άγνωστη κατάσταση",mobileScheduledStatusLabel("unexpected"))
    }
}
