package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalTransactionEntryBankParityTest {
    private val now = "2026-09-02T11:00:00Z"

    @Test
    fun cardPayment_acceptsAccountFromSameCanonicalBank() {
        val document = bankParityFixture()
        val withDebt = createCanonicalTransactionEntryMutation(
            document = document,
            draft = CanonicalTransactionEntryDraft(
                kind = "card_purchase",
                date = "2026-09-02",
                amount = 20.0,
                cardId = "card-piraeus",
            ),
            eventId = "evt-bank-purchase",
            nowIso = now,
        ).apply(document)

        val result = createCanonicalTransactionEntryMutation(
            document = withDebt,
            draft = CanonicalTransactionEntryDraft(
                kind = "card_payment",
                date = "2026-09-02",
                amount = 10.0,
                fromAccountId = "piraeus-main",
                cardId = "card-piraeus",
            ),
            eventId = "evt-bank-payment",
            nowIso = now,
        ).apply(withDebt)

        assertEquals(10.0, result.creditDebtForCardAt("card-piraeus", "2026-09-02"), 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cardPayment_rejectsAccountFromDifferentCanonicalBank() {
        val document = bankParityFixture()
        val withDebt = createCanonicalTransactionEntryMutation(
            document = document,
            draft = CanonicalTransactionEntryDraft(
                kind = "card_purchase",
                date = "2026-09-02",
                amount = 20.0,
                cardId = "card-piraeus",
            ),
            eventId = "evt-other-bank-purchase",
            nowIso = now,
        ).apply(document)

        createCanonicalTransactionEntryMutation(
            document = withDebt,
            draft = CanonicalTransactionEntryDraft(
                kind = "card_payment",
                date = "2026-09-02",
                amount = 10.0,
                fromAccountId = "eurobank-main",
                cardId = "card-piraeus",
            ),
            eventId = "evt-other-bank-payment",
            nowIso = now,
        )
    }
}

private fun bankParityFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "app":"MyFinHub",
          "schemaVersion":3,
          "updatedAt":"2026-09-02T00:00:00Z",
          "seed":{
            "accounts":[
              {"id":"piraeus-main","name":"Πειραιώς","kind":"bank"},
              {"id":"eurobank-main","name":"Eurobank","kind":"bank"}
            ],
            "snapshots":[
              {"date":"2026-09-01","balances":{"piraeus-main":1000.0,"eurobank-main":1000.0}}
            ],
            "transactions":[],
            "recurring":[],
            "loans":[],
            "lending":[]
          },
          "state":{
            "deleted":[],
            "overrides":{},
            "customTransactions":[],
            "settings":{
              "excludedFromAvailable":[],
              "accountNames":{},
              "defaultExpenseAccount":"piraeus-main",
              "defaultIncomeAccount":"piraeus-main"
            },
            "events":[],
            "scheduled":[],
            "cards":[
              {
                "id":"card-piraeus",
                "bankId":"piraeus",
                "nickname":"Piraeus Credit",
                "kind":"credit",
                "network":"visa",
                "last4":"1881",
                "active":true,
                "createdAt":"2026-01-01T00:00:00Z"
              }
            ],
            "budgets":[]
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
