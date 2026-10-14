package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPermanentCardDeletionPolicyTest {
    private fun document(extra: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement("""
            {"seed":{"accounts":[]}, "state": $extra}
        """.trimIndent()).jsonObject)

    private fun cards(creditActive: Boolean, events: String): CanonicalFinanceDocument =
        document("""
        {"cards":[
           {"id":"card-credit","kind":"credit","active":$creditActive,"nickname":"Visa","bankId":"alpha","network":"visa","last4":"1234"},
           {"id":"card-debit","kind":"debit","active":true,"nickname":"Debit","bankId":"alpha","network":"visa","last4":"5678"}
         ],"events":$events}
        """.trimIndent())

    @Test fun activeCreditCannotBePermanentlyDeletedEvenWithZeroDebt() {
        val doc = cards(true,"[]")
        assertEquals(PermanentCardDeletionBlock.ACTIVE_CREDIT_CARD,
            doc.assessPermanentCardDeletion("card-credit","2026-10-14").blocker)
    }

    @Test fun archivedCreditWithOutstandingLinkedDebtCannotBeDeleted() {
        val doc = cards(false, """[
          {"id":"a","kind":"card_purchase","cardId":"card-credit","date":"2026-10-01","amount":120,"legs":[]},
          {"id":"b","kind":"card_payment","cardId":"card-credit","date":"2026-10-02","amount":30,"legs":[]}
        ]""")
        assertEquals(PermanentCardDeletionBlock.CREDIT_OUTSTANDING,
            doc.assessPermanentCardDeletion("card-credit","2026-10-14").blocker)
    }

    @Test fun paidArchivedCreditCanBeDeletedOnlyAfterRepaymentDate() {
        val doc = cards(false, """[
          {"id":"a","kind":"card_purchase","cardId":"card-credit","date":"2026-10-01","amount":120,"legs":[]},
          {"id":"b","kind":"card_payment","cardId":"card-credit","date":"2026-10-20","amount":120,"legs":[]}
        ]""")
        assertFalse(doc.assessPermanentCardDeletion("card-credit","2026-10-14").allowed)
        assertTrue(doc.assessPermanentCardDeletion("card-credit","2026-10-20").allowed)
    }

    @Test fun nonCreditWithFinanceHistoryIsProtectedAndUnknownIdFailsClosed() {
        val doc = cards(false, """[
          {"id":"a","kind":"card_purchase","cardId":"card-debit","date":"2026-10-01","amount":17,"legs":[]}
        ]""")
        assertEquals(PermanentCardDeletionBlock.NON_CREDIT_FINANCE_HISTORY,
            doc.assessPermanentCardDeletion("card-debit","2026-10-14").blocker)
        assertEquals(PermanentCardDeletionBlock.CARD_NOT_FOUND,
            doc.assessPermanentCardDeletion("deleted-id","2026-10-14").blocker)
        assertFalse(doc.assessPermanentCardDeletion("deleted-id","2026-10-14").allowed)
    }

    @Test fun nonCreditWithoutLinkedFinancialEventsMatchesWebEligibility() {
        val doc = cards(false,"[]")
        assertTrue(doc.assessPermanentCardDeletion("card-debit","2026-10-14").allowed)
    }
}
