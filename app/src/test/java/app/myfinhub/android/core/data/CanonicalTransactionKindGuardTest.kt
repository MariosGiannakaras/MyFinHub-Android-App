package app.myfinhub.android.core.data

import org.junit.Test

class CanonicalTransactionKindGuardTest {
    private val document = canonicalFixture()

    @Test(expected = IllegalArgumentException::class)
    fun withdrawal_rejectsNonCashDestinationAtCanonicalBoundary() {
        createCanonicalTransactionEntryMutation(
            document = document,
            draft = CanonicalTransactionEntryDraft(
                kind = "withdrawal",
                date = "2026-08-23",
                amount = 20.0,
                fromAccountId = "acc-main",
                toAccountId = "acc-save",
            ),
            eventId = "evt-invalid-withdrawal",
            nowIso = "2026-08-23T12:00:00Z",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun saving_rejectsNonSavingsDestinationAtCanonicalBoundary() {
        createCanonicalTransactionEntryMutation(
            document = document,
            draft = CanonicalTransactionEntryDraft(
                kind = "saving_cash_offset",
                date = "2026-08-23",
                amount = 20.0,
                fromAccountId = "acc-save",
                toAccountId = "acc-main",
            ),
            eventId = "evt-invalid-saving",
            nowIso = "2026-08-23T12:00:00Z",
        )
    }
}
