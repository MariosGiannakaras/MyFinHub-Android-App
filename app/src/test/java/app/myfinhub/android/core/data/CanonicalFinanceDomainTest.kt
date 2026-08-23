package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalFinanceDomainTest {
    private val document = canonicalFixture()

    @Test
    fun effectiveLegacyTransactions_appliesDeletedOverridesAndCustomWithoutLoss() {
        val transactions = document.effectiveLegacyTransactions()

        assertEquals(listOf("tx-exp", "tx-custom"), transactions.map { it.id })
        assertEquals(120.0, transactions.first { it.id == "tx-exp" }.amount, 0.001)
        assertEquals("Νεότερη σημείωση", transactions.first { it.id == "tx-exp" }.note)
        assertFalse(transactions.any { it.id == "tx-deleted" })
    }

    @Test
    fun accountBalances_usesSnapshotMutableLegacyDeltasAndEventLegs() {
        val balances = document.accountBalances("2026-08-31")

        assertEquals(1_155.0, balances.getValue("acc-main"), 0.001)
        assertEquals(540.0, balances.getValue("acc-save"), 0.001)
        assertEquals(0.0, balances.getValue(CREDIT_ACCOUNT_ID), 0.001)
        assertEquals(1_695.0, document.availableMoney("2026-08-31"), 0.001)
    }

    @Test
    fun monthlyFlowAndCategories_combineEffectiveLegacyAndCanonicalEvents() {
        val flow = document.monthlyFlow("2026-08")
        val categories = document.categoryTotals("2026-08")

        assertEquals(200.0, flow.income, 0.001)
        assertEquals(155.0, flow.expense, 0.001)
        assertEquals(5.0, flow.refunds, 0.001)
        assertEquals(145.0, categories.getValue("Τρόφιμα"), 0.001)
        assertEquals(10.0, categories.getValue("Έξοδος"), 0.001)
    }

    @Test
    fun expenseMutation_isCentNormalizedIdempotentAndPreservesUnknownJson() {
        val mutation = createCanonicalEventMutation(
            document = document,
            draft = CanonicalEventDraft(
                kind = "expense",
                date = "2026-08-23",
                amount = 12.345,
                note = "Καφές",
                category = "Έξοδος",
                accountId = "acc-main",
            ),
            eventId = "evt-android-stable",
            nowIso = "2026-08-23T12:00:00Z",
        )

        val first = mutation.apply(document)
        val second = mutation.apply(first)
        val added = first.canonicalEvents().first { it.id == "evt-android-stable" }

        assertEquals(12.35, added.amount, 0.001)
        assertEquals(-12.35, added.legs.single().amount, 0.001)
        assertEquals(1, second.canonicalEvents().count { it.id == "evt-android-stable" })
        assertEquals("keep-root", first.raw["unknownRoot"]?.toString()?.trim('"'))
        assertEquals("keep-state", first.state["unknownState"]?.toString()?.trim('"'))
    }

    @Test
    fun equalSplit_allocatesEveryCentExactly() {
        val parts = equalExpenseSplit(
            total = 10.0,
            parts = 3,
            category = "Τρόφιμα",
            idPrefix = "part",
        )

        assertEquals(listOf(3.34, 3.33, 3.33), parts.map { it.amount })
        assertEquals(1_000L, parts.sumOf { moneyToCents(it.amount) })
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferMutation_rejectsSameAccount() {
        createCanonicalEventMutation(
            document = document,
            draft = CanonicalEventDraft(
                kind = "transfer",
                date = "2026-08-23",
                amount = 10.0,
                note = "Μεταφορά",
                fromAccountId = "acc-main",
                toAccountId = "acc-main",
            ),
            eventId = "evt-invalid",
            nowIso = "2026-08-23T12:00:00Z",
        )
    }

    @Test
    fun editSeedTransaction_createsLosslessOverride() {
        val edited = EditCanonicalActivity(
            transactionId = "tx-exp",
            note = "Νέα περιγραφή",
            category = "Σπίτι",
            nowIso = "2026-08-23T12:00:00Z",
        ).apply(document)
        val override = edited.state.obj("overrides")["tx-exp"] as JsonObject

        assertEquals("Νέα περιγραφή", override.string("note"))
        assertEquals("Σπίτι", override.string("category"))
        assertEquals("keep-override", override.string("unknownTx"))
        assertEquals("keep-state", edited.state.string("unknownState"))
    }

    @Test
    fun budgetUpsert_preservesUnrelatedStateAndUsesStableId() {
        val mutation = UpsertOverallBudget(
            month = "2026-08",
            amount = 900.129,
            alertThreshold = 75,
            budgetId = "budget-android-stable",
            nowIso = "2026-08-23T12:00:00Z",
        )
        val once = mutation.apply(document)
        val twice = mutation.apply(once)
        val budgets = twice.state.array("budgets").mapNotNull { it as? JsonObject }
        val budget = budgets.single { it.string("month") == "2026-08" && it.string("scope") == "overall" }

        assertEquals("budget-android-stable", budget.string("id"))
        assertEquals(900.13, budget.number("amount") ?: 0.0, 0.001)
        assertEquals(75.0, budget.number("alertThreshold") ?: 0.0, 0.001)
        assertEquals(1, budgets.count { it.string("id") == "budget-android-stable" })
        assertEquals("keep-state", twice.state.string("unknownState"))
    }
}

internal fun canonicalFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "app":"RheomIQ",
          "schemaVersion":3,
          "updatedAt":"2026-08-22T00:00:00Z",
          "unknownRoot":"keep-root",
          "seed":{
            "accounts":[
              {"id":"acc-main","name":"Κύριος","kind":"bank"},
              {"id":"acc-save","name":"Αποταμίευση","kind":"savings"}
            ],
            "snapshots":[
              {"date":"2026-08-01","balances":{"acc-main":1000.0,"acc-save":500.0}}
            ],
            "transactions":[
              {"id":"tx-exp","date":"2026-08-10","type":"expense","accountId":"acc-main","amount":100.0,"note":"Παλιό","category":"Τρόφιμα","unknownTx":"seed-extra"},
              {"id":"tx-deleted","date":"2026-08-11","type":"expense","accountId":"acc-main","amount":50.0,"note":"Διαγραμμένο","category":"Τρόφιμα"}
            ],
            "recurring":[],
            "loans":[],
            "lending":[]
          },
          "state":{
            "unknownState":"keep-state",
            "deleted":["tx-deleted"],
            "overrides":{
              "tx-exp":{"id":"tx-exp","date":"2026-08-10","type":"expense","accountId":"acc-main","amount":120.0,"note":"Νεότερη σημείωση","category":"Τρόφιμα","unknownTx":"keep-override"}
            },
            "customTransactions":[
              {"id":"tx-custom","date":"2026-08-12","type":"income","accountId":"acc-main","amount":200.0,"note":"Έσοδο","category":"Μισθός"}
            ],
            "settings":{
              "excludedFromAvailable":[],
              "accountNames":{},
              "defaultExpenseAccount":"acc-main",
              "monthlyBudget":800.0
            },
            "events":[
              {"id":"evt-exp","date":"2026-08-15","kind":"expense","amount":30.0,"note":"Καφές","category":"Τρόφιμα","accountId":"acc-main","legs":[{"accountId":"acc-main","amount":-30.0}],"source":"user","createdAt":"2026-08-15T10:00:00Z","updatedAt":"2026-08-15T10:00:00Z"},
              {"id":"evt-transfer","date":"2026-08-16","kind":"transfer","amount":40.0,"note":"Μεταφορά","fromAccountId":"acc-main","toAccountId":"acc-save","legs":[{"accountId":"acc-main","amount":-40.0},{"accountId":"acc-save","amount":40.0}],"source":"user","createdAt":"2026-08-16T10:00:00Z","updatedAt":"2026-08-16T10:00:00Z"},
              {"id":"evt-split","date":"2026-08-17","kind":"split","amount":10.0,"note":"Μοίρασμα","accountId":"acc-main","legs":[{"accountId":"acc-main","amount":-10.0}],"parts":[{"id":"p1","label":"Μέρος","category":"Έξοδος","amount":10.0,"kind":"expense"}],"source":"user","createdAt":"2026-08-17T10:00:00Z","updatedAt":"2026-08-17T10:00:00Z"},
              {"id":"evt-refund","date":"2026-08-18","kind":"refund","amount":5.0,"note":"Επιστροφή","category":"Τρόφιμα","accountId":"acc-main","legs":[{"accountId":"acc-main","amount":5.0}],"source":"user","createdAt":"2026-08-18T10:00:00Z","updatedAt":"2026-08-18T10:00:00Z"}
            ],
            "scheduled":[],
            "cards":[],
            "budgets":[]
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
