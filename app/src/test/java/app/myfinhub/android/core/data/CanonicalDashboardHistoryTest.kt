package app.myfinhub.android.core.data

import java.time.LocalDate
import java.time.YearMonth
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class CanonicalDashboardHistoryTest {
    @Test
    fun firstSnapshotReconstructsOpeningAndLaterSnapshotsResetSeries() {
        val data = fixture("""
         {"seed":{"accounts":[{"id":"cash","kind":"cash","cashRole":"daily"}],
           "snapshots":[
             {"date":"2026-09-03","balances":{"cash":100}},
             {"date":"2026-09-06","balances":{"cash":120}}
           ],"transactions":[]},
          "state":{"events":[
             {"id":"before","date":"2026-09-02","kind":"income","amount":10,"legs":[{"accountId":"cash","amount":10}]},
             {"id":"after","date":"2026-09-04","kind":"expense","amount":5,"legs":[{"accountId":"cash","amount":-5}]},
             {"id":"after-reset","date":"2026-09-07","kind":"income","amount":7,"legs":[{"accountId":"cash","amount":7}]}
          ]}}
        """)
        val actual = data.dashboardAccountHistory(listOf("cash"), LocalDate.of(2026,9,1), LocalDate.of(2026,9,7)).getValue("cash")
        assertEquals(listOf(90.0,100.0,100.0,95.0,95.0,120.0,127.0), actual)
        assertEquals(127.0, data.accountBalances("2026-09-07")["cash"] ?: 0.0, 0.001)
    }

    @Test
    fun snapshotBeforeWindowPreservesOpeningAndCustomTransfer() {
        val data = fixture("""
          {"seed":{"accounts":[{"id":"cash","kind":"cash","cashRole":"daily"},{"id":"bank","kind":"bank"}],
           "snapshots":[{"date":"2026-09-01","balances":{"cash":100,"bank":250}}],
           "transactions":[]},
           "state":{"customTransactions":[
             {"id":"tr","type":"transfer","date":"2026-09-03","amount":20,"fromAccountId":"cash","toAccountId":"bank"}
           ]}}
        """)
        val result = data.dashboardAccountHistory(listOf("cash","bank"),LocalDate.of(2026,9,4),LocalDate.of(2026,9,5))
        assertEquals(listOf(80.0,80.0),result.getValue("cash"))
        assertEquals(listOf(270.0,270.0),result.getValue("bank"))
    }

    @Test
    fun historyDatesAndThirtyDayDeltaMatchWebPeriodRules() {
        assertEquals(LocalDate.of(2026,9,23), dashboardHistoryStart(YearMonth.of(2026,10)))
        val data = fixture("""
          {"seed":{"accounts":[{"id":"cash","kind":"cash","cashRole":"daily"}],
          "snapshots":[{"date":"2026-09-01","balances":{"cash":50}}]},
          "state":{"events":[{"id":"gain","date":"2026-10-02","kind":"income","amount":25,
            "legs":[{"accountId":"cash","amount":25}]}]}}
        """)
        assertEquals(25.0,data.dashboardBalanceChange("cash",LocalDate.of(2026,10,14)),0.001)
    }

    private fun fixture(raw: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement(raw.trimIndent()).jsonObject)
}
