package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalTransactionEntryMutationTest {
    private val document = transactionEntryFixture()
    private val now = "2026-09-02T10:00:00Z"

    @Test
    fun incomeAndRefund_creditAssetButHaveDifferentFlowSemantics() {
        val income = event(
            CanonicalTransactionEntryDraft(
                kind = "income",
                date = "2026-09-02",
                amount = 100.0,
                category = "Μισθός",
                accountId = "acc-main",
            ),
            "evt-income",
        )
        val refund = event(
            CanonicalTransactionEntryDraft(
                kind = "refund",
                date = "2026-09-02",
                amount = 20.0,
                category = "Τρόφιμα",
                accountId = "acc-main",
            ),
            "evt-refund",
        )

        assertEquals(100.0, income.legs.single().amount, 0.001)
        assertEquals(20.0, refund.legs.single().amount, 0.001)
        val withIncome = createCanonicalTransactionEntryMutation(document, draftFor(income), "stable-income", now).apply(document)
        val withBoth = createCanonicalTransactionEntryMutation(withIncome, draftFor(refund), "stable-refund", now).apply(withIncome)
        val flow = withBoth.monthlyFlow("2026-09")
        assertEquals(100.0, flow.income, 0.001)
        assertEquals(0.0, flow.expense, 0.001)
        assertEquals(20.0, flow.refunds, 0.001)
    }

    @Test
    fun withdrawalAndSaving_moveAssetsWithoutCreatingExpense() {
        val withdrawalMutation = createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "withdrawal",
                date = "2026-09-02",
                amount = 50.0,
                fromAccountId = "acc-main",
                toAccountId = "acc-cash",
            ),
            "evt-withdrawal",
            now,
        )
        val afterWithdrawal = withdrawalMutation.apply(document)
        val savingMutation = createCanonicalTransactionEntryMutation(
            afterWithdrawal,
            CanonicalTransactionEntryDraft(
                kind = "saving_cash_offset",
                date = "2026-09-02",
                amount = 75.0,
                fromAccountId = "acc-main",
                toAccountId = "acc-save",
            ),
            "evt-saving",
            now,
        )
        val result = savingMutation.apply(afterWithdrawal)
        val withdrawal = result.canonicalEvents().first { it.id == "evt-withdrawal" }
        val saving = result.canonicalEvents().first { it.id == "evt-saving" }

        assertEquals(listOf(-50.0, 50.0), withdrawal.legs.map { it.amount })
        assertEquals(listOf(-75.0, 75.0), saving.legs.map { it.amount })
        assertEquals(75.0, saving.savingAmount, 0.001)
        val flow = result.monthlyFlow("2026-09")
        assertEquals(0.0, flow.expense, 0.001)
        assertEquals(75.0, flow.saving, 0.001)
    }

    @Test
    fun lendingAndRepayment_adjustReceivableInOppositeDirections() {
        val lending = event(
            CanonicalTransactionEntryDraft(
                kind = "lending",
                date = "2026-09-02",
                amount = 80.0,
                accountId = "acc-main",
                person = "Άννα",
                expectedReturnDate = "2026-10-01",
            ),
            "evt-lend",
        )
        val repayment = event(
            CanonicalTransactionEntryDraft(
                kind = "repayment",
                date = "2026-09-02",
                amount = 30.0,
                accountId = "acc-main",
                person = "Άννα",
            ),
            "evt-repay",
        )

        assertEquals(-80.0, lending.legs.single().amount, 0.001)
        assertEquals(80.0, lending.receivableDelta, 0.001)
        assertEquals(30.0, repayment.legs.single().amount, 0.001)
        assertEquals(-30.0, repayment.receivableDelta, 0.001)
    }

    @Test
    fun cardPurchaseAndPayment_useExplicitCardAndCreditLiability() {
        val purchase = event(
            CanonicalTransactionEntryDraft(
                kind = "card_purchase",
                date = "2026-09-02",
                amount = 42.5,
                category = "Τρόφιμα",
                cardId = "card-credit",
            ),
            "evt-card-purchase",
        )
        val payment = event(
            CanonicalTransactionEntryDraft(
                kind = "card_payment",
                date = "2026-09-02",
                amount = 20.0,
                fromAccountId = "acc-main",
                cardId = "card-credit",
            ),
            "evt-card-payment",
        )

        assertEquals("card-credit", purchase.cardId)
        assertEquals(CREDIT_ACCOUNT_ID, purchase.legs.single().accountId)
        assertEquals(-42.5, purchase.creditDelta, 0.001)
        assertEquals("card-credit", payment.cardId)
        assertEquals(listOf("acc-main", CREDIT_ACCOUNT_ID), payment.legs.map { it.accountId })
        assertEquals(listOf(-20.0, 20.0), payment.legs.map { it.amount })
        assertEquals(20.0, payment.creditDelta, 0.001)
    }

    @Test
    fun reconciliation_recordsOnlyDifferenceFromCalculatedBalance() {
        val mutation = createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "reconciliation",
                date = "2026-09-02",
                actualBalance = 1_035.25,
                accountId = "acc-main",
            ),
            "evt-reconcile",
            now,
        )
        val result = mutation.apply(document)
        val event = result.canonicalEvents().first { it.id == "evt-reconcile" }

        assertEquals(35.25, event.amount, 0.001)
        assertEquals(35.25, event.legs.single().amount, 0.001)
        assertEquals(0.0, result.monthlyFlow("2026-09").income, 0.001)
        assertEquals(0.0, result.monthlyFlow("2026-09").expense, 0.001)
    }

    @Test
    fun split_derivesParentAmountFromPartsAndKeepsPartCategories() {
        val mutation = createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "split",
                date = "2026-09-02",
                accountId = "acc-main",
                parts = listOf(
                    CanonicalTransactionSplitDraft("part-1", "Market", "Τρόφιμα", "Σούπερ μάρκετ", 10.10),
                    CanonicalTransactionSplitDraft("part-2", "Parking", "Μετακίνηση", amount = 4.90),
                ),
            ),
            "evt-split",
            now,
        )
        val once = mutation.apply(document)
        val twice = mutation.apply(once)
        val event = once.canonicalEvents().first { it.id == "evt-split" }

        assertEquals(15.0, event.amount, 0.001)
        assertEquals(-15.0, event.legs.single().amount, 0.001)
        assertEquals(listOf("Τρόφιμα", "Μετακίνηση"), event.parts.map { it.category })
        assertEquals("Σούπερ μάρκετ", event.parts.first().subcategory)
        assertEquals(1, twice.canonicalEvents().count { it.id == "evt-split" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun lending_rejectsExpectedReturnBeforeTransactionDate() {
        createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "lending",
                date = "2026-09-02",
                amount = 10.0,
                accountId = "acc-main",
                person = "Άννα",
                expectedReturnDate = "2026-09-01",
            ),
            "evt-invalid-date",
            now,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun cardPayment_rejectsUnknownCardInsteadOfChoosingAnotherOne() {
        createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "card_payment",
                date = "2026-09-02",
                amount = 10.0,
                fromAccountId = "acc-main",
                cardId = "missing-card",
            ),
            "evt-invalid-card",
            now,
        )
    }

    private fun event(draft: CanonicalTransactionEntryDraft, id: String): CanonicalEvent {
        val result = createCanonicalTransactionEntryMutation(document, draft, id, now).apply(document)
        return result.canonicalEvents().first { it.id == id }
    }

    private fun draftFor(event: CanonicalEvent): CanonicalTransactionEntryDraft = CanonicalTransactionEntryDraft(
        kind = event.kind,
        date = event.date,
        amount = event.amount,
        note = event.note,
        category = event.category,
        accountId = event.accountId,
        fromAccountId = event.fromAccountId,
        toAccountId = event.toAccountId,
        cardId = event.cardId,
    )
}

private fun transactionEntryFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "app":"MyFinHub",
          "schemaVersion":3,
          "updatedAt":"2026-09-02T00:00:00Z",
          "seed":{
            "accounts":[
              {"id":"acc-main","name":"Κύριος","kind":"bank"},
              {"id":"acc-save","name":"Αποταμίευση","kind":"savings"},
              {"id":"acc-cash","name":"Μετρητά","kind":"cash"}
            ],
            "snapshots":[{"date":"2026-09-01","balances":{"acc-main":1000.0,"acc-save":500.0,"acc-cash":100.0}}],
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
              "expenseCategories":["Τρόφιμα","Μετακίνηση","Άλλο"],
              "incomeCategories":["Μισθός","Άλλο"],
              "defaultExpenseAccount":"acc-main",
              "defaultIncomeAccount":"acc-main"
            },
            "events":[],
            "scheduled":[],
            "cards":[
              {"id":"card-credit","nickname":"Πιστωτική","kind":"credit","network":"visa","last4":"1881","active":true,"creditLimit":2000.0},
              {"id":"card-debit","nickname":"Χρεωστική","kind":"debit","network":"visa","last4":"4242","active":true}
            ],
            "budgets":[]
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
