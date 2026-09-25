package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CanonicalTransactionDeletionTest {
    @Test
    fun deleteCanonicalEvent_removesEventAndReversesLedgerImpact() {
        val original = canonicalFixture()
        val before = original.accountBalances("2026-08-31").getValue("acc-main")

        val deleted = DeleteCanonicalActivity(
            transactionId = "evt-exp",
            nowIso = "2026-09-03T20:00:00Z",
        ).apply(original)

        assertFalse(deleted.canonicalEvents().any { it.id == "evt-exp" })
        assertEquals(before + 30.0, deleted.accountBalances("2026-08-31").getValue("acc-main"), 0.001)
        assertEquals("keep-state", deleted.state.string("unknownState"))
    }

    @Test
    fun deleteCustomTransaction_removesCustomIncome() {
        val original = canonicalFixture()
        val before = original.accountBalances("2026-08-31").getValue("acc-main")

        val deleted = DeleteCanonicalActivity(
            transactionId = "tx-custom",
            nowIso = "2026-09-03T20:00:00Z",
        ).apply(original)

        assertFalse(deleted.effectiveLegacyTransactions().any { it.id == "tx-custom" })
        assertEquals(before - 200.0, deleted.accountBalances("2026-08-31").getValue("acc-main"), 0.001)
    }

    @Test
    fun deleteSeedTransaction_marksDeletedAndRemovesOverrideSoItCannotBeReapplied() {
        val original = canonicalFixture()
        val before = original.accountBalances("2026-08-31").getValue("acc-main")

        val deleted = DeleteCanonicalActivity(
            transactionId = "tx-exp",
            nowIso = "2026-09-03T20:00:00Z",
        ).apply(original)

        assertFalse(deleted.effectiveLegacyTransactions().any { it.id == "tx-exp" })
        assertEquals(before + 120.0, deleted.accountBalances("2026-08-31").getValue("acc-main"), 0.001)
        val overrides = deleted.state.obj("overrides")
        assertNull(overrides["tx-exp"])
        assertEquals("keep-state", deleted.state.string("unknownState"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun deleteUnknownTransaction_failsClosed() {
        DeleteCanonicalActivity(
            transactionId = "missing",
            nowIso = "2026-09-03T20:00:00Z",
        ).apply(canonicalFixture())
    }
}
