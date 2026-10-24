package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.cashFlowForecast
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web forecast.ts sourceCounts and omitted[] never diverge in production UI. */
class WebForecastTransparencyProjectionParityTest {
    private val asOf = LocalDate.parse("2026-10-15")
    private val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"payroll","name":"Μισθοδοσία","kind":"bank"}],
        "snapshots":[{"date":"2026-10-15","balances":{"payroll":800}}],
        "transactions":[],
        "recurring":[
          {"id":"active","name":"Internet","amount":30,"active":true,
            "firstExpectedDate":"2026-10-20","accountId":"payroll"},
          {"id":"invalid","name":"Old subscription","amount":20,"active":true,
            "day":25,"accountId":"removed"}
        ],
        "loans":[]
      },"state":{
        "settings":{},
        "events":[],
        "scheduled":[
          {"id":"invoice","kind":"income","note":"Refund",
            "dueDate":"2026-10-22","amount":100,"accountId":"payroll",
            "status":"pending"},
          {"id":"missing","kind":"expense","note":"Unknown payer",
            "dueDate":"2026-10-25","amount":900,"accountId":"removed",
            "status":"pending"}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun forecastWindowsExposeCanonicalSourceCountsAndUnmodifiedOmissionReasons() {
        val raw=document.raw
        val ui=projectCanonicalPlanState(document,asOf,null)
        assertEquals(listOf(30,60,90),ui.forecastWindows.map { it.days })
        ui.forecastWindows.forEach { window ->
            val canonical=document.cashFlowForecast(asOf,window.days)
            assertEquals(canonical.sourceCounts,window.sourceCounts)
            assertEquals(canonical.omitted,window.omittedWarnings)
            assertEquals(canonical.omitted.size,window.omittedSourceCount)
            assertEquals(canonical.projectedPortfolio,window.projectedPortfolio,0.001)
        }
        val first=ui.forecastWindows.first()
        assertEquals(1,first.sourceCounts["scheduled"])
        assertEquals(1,first.sourceCounts["recurring"])
        assertEquals(0,first.sourceCounts["loan"])
        assertEquals(0,first.sourceCounts["dated_event"])
        assertEquals(2,first.omittedSourceCount)
        // Web-style diagnostics identify named records without exposing source IDs.
        assertEquals(listOf(
            "Η προγραμματισμένη κίνηση «Unknown payer» δεν προβλήθηκε επειδή ο λογαριασμός της δεν είναι διαθέσιμος.",
            "Το πάγιο «Old subscription» δεν προβλήθηκε επειδή ο λογαριασμός του δεν είναι διαθέσιμος.",
        ), first.omittedWarnings)
        assertEquals(raw,document.raw)
    }
}
