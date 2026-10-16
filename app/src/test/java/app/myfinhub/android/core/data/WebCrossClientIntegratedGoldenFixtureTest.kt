package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * One frozen, anonymized cross-client scenario exercised through the *same* canonical FinanceData:
 * payroll/savings ledger, Web category budgets, user rules, goals, as-of loans/lending
 * and 30/60/90 portfolio forecast. No backend/network state or personal finance records.
 */
class WebCrossClientIntegratedGoldenFixtureTest {
    private val original=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"app":"MyFinHub","schemaVersion":3,"updatedAt":"2026-10-01T00:00:00Z",
       "futureRoot":"must-survive",
       "seed":{
         "accounts":[
           {"id":"payroll","kind":"bank","name":"Μισθοδοσία","providerId":"piraeus"},
           {"id":"savings","kind":"savings","name":"Στόχος","excludeFromAvailable":true}
         ],
         "snapshots":[{"date":"2026-10-01","balances":{"payroll":500,"savings":100}}],
         "transactions":[],
         "loans":[{"id":"loan","name":"Δάνειο","total":200,"installment":100,
           "installments":2,"paidCount":0,"provider":"bank"}],
         "lending":[{"person":"Άννα","outstanding":100,"entries":[]}]
       },
       "state":{
         "settings":{"expenseCategories":["Άλλο"],"incomeCategories":["Μισθός"]},
         "events":[
           {"id":"food","kind":"expense","date":"2026-10-05","amount":80,
             "category":"Τρόφιμα","legs":[{"accountId":"payroll","amount":-80}]},
           {"id":"refund","kind":"refund","date":"2026-10-06","amount":20,
             "category":"Τρόφιμα","legs":[{"accountId":"payroll","amount":20}]},
           {"id":"saving","kind":"saving_cash_offset","date":"2026-10-07","amount":40,
             "savingAmount":40,"legs":[{"accountId":"payroll","amount":-40},
             {"accountId":"savings","amount":40}]},
           {"id":"transfer","kind":"transfer","date":"2026-10-08","amount":10,
             "legs":[{"accountId":"payroll","amount":-10},
             {"accountId":"savings","amount":10}]},
           {"id":"loan-paid","kind":"loan_payment","date":"2026-10-12",
             "loanId":"loan","amount":100,"installmentCount":1},
           {"id":"loan-future","kind":"loan_payment","date":"2026-10-25",
             "loanId":"loan","amount":100,"installmentCount":1},
           {"id":"lent","kind":"lending","date":"2026-10-13",
             "person":"Άννα","receivableDelta":30},
           {"id":"repaid-future","kind":"repayment","date":"2026-10-26",
             "person":"Άννα","receivableDelta":-20}
         ],
         "transactionRules":[{"id":"food-rule","priority":1,"enabled":true,
           "scopes":["manual"],"match":{"description":"market","mode":"contains"},
           "action":{"category":"Τρόφιμα"}}],
         "budgets":[
           {"id":"overall","month":"2026-10","scope":"overall","amount":100,"alertThreshold":0.8},
           {"id":"food-budget","month":"2026-10","scope":"category",
             "category":"Τρόφιμα","amount":100,"alertThreshold":0.8}
         ],
         "savingsGoals":[{"id":"travel","name":"Ταξίδι","targetAmount":300,
           "targetDate":"2027-01-01"}],
         "scheduled":[
           {"id":"scheduled-expense","status":"pending","kind":"expense",
             "date":"2026-10-18","dueDate":"2026-10-18","amount":10,"accountId":"payroll"},
           {"id":"scheduled-transfer","status":"pending","kind":"transfer",
             "date":"2026-10-20","dueDate":"2026-10-20","amount":25,
             "fromAccountId":"payroll","toAccountId":"savings"}
         ],
         "futureState":"must-survive"
       }}
    """.trimIndent()).jsonObject)

    private val date="2026-10-16"
    private val today=LocalDate.parse(date)

    @Test fun snapshotLedgerAndNetBudgetUseTheSameHistoricalFinanceState() {
        val balances=original.accountBalances(date)
        assertEquals(390.0,balances["payroll"]!!,0.001)
        assertEquals(150.0,balances["savings"]!!,0.001)
        assertEquals(390.0,original.availableMoney(date),0.001)
        assertEquals(60.0,original.categoryBudgetSpending("2026-10",date)["Τρόφιμα"]!!,0.001)
        val budget=original.webBudgetProgress("2026-10",date)
        assertEquals(2,budget.size)
        assertTrue(budget.all { it.used == 60.0 && it.status == "ok" })
    }

    @Test fun forecastDoesNotCountTransfersAsSpendingOrHideExcludedSavings() {
        for(days in listOf(30,60,90)) {
            val projection=original.cashFlowForecast(today,days)
            assertEquals(540.0,projection.currentPortfolio,0.001)
            assertEquals(530.0,projection.projectedPortfolio,0.001)
            assertEquals(0.0,projection.movements.first {
                it.id=="forecast-scheduled-scheduled-transfer"
            }.portfolioDelta,0.001)
            assertEquals(setOf("payroll","savings"),projection.accounts.map { it.accountId }.toSet())
            assertEquals(2,projection.sourceCounts["scheduled"])
        }
    }

    @Test fun goalsDebtAndReceivablesReadAsOfWithoutFutureSettlement() {
        val goals=original.canonicalSavingsGoals(today)
        assertEquals(1,goals.size)
        assertEquals(150.0,goals.single().backingBalance,0.001)
        assertEquals(50.0,goals.single().progressPercent,0.001)
        assertEquals(100.0,original.loanOutstanding(date),0.001)
        assertEquals(0.0,original.loanOutstanding("2026-10-30"),0.001)
        assertEquals(130.0,original.receivableOutstanding(date),0.001)
        assertEquals(110.0,original.receivableOutstanding("2026-10-30"),0.001)
    }

    @Test fun newCategorizedExpenseAndOfflineReplayPreserveUnknownServerFields() {
        val mutation=createCanonicalTransactionEntryMutation(
            original,
            CanonicalTransactionEntryDraft(
                kind="expense",date=date,amount=12.34,note="Corner Market",
                category="Άλλο",accountId="payroll",
            ),
            "stable-expense-id","2026-10-16T10:00:00Z",
        )
        val after=mutation.apply(original)
        assertEquals("Τρόφιμα",after.canonicalEvents().single {
            it.id=="stable-expense-id"
        }.category)
        assertEquals(72.34,after.webBudgetProgress("2026-10",date)
            .first { it.scope=="overall" }.used,0.001)
        assertEquals(377.66,after.availableMoney(date),0.001)
        assertEquals(after.raw,mutation.apply(after).raw)
        assertEquals(original.raw["futureRoot"],after.raw["futureRoot"])
        assertEquals(original.state["futureState"],after.state["futureState"])
    }
}
