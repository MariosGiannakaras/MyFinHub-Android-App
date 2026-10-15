package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.plan.PlannedKind
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Plan UI uses all canonical recurring identities and paid-month cadence. */
class WebRecurringPlanIntegrationParityTest {
    @Test fun planIncludesCustomRecurringButOmitsPausedAndMovesPaidCycle() {
        val data=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[{"id":"bank","kind":"bank","name":"Μισθοδοσία"}],
            "snapshots":[{"date":"2026-10-01","balances":{"bank":400}}],
            "transactions":[],"recurring":[
              {"id":"rent","name":"Ενοίκιο","amount":100,"day":20,"accountId":"bank","category":"Σπίτι","active":true},
              {"id":"paid","name":"Συνδρομή","amount":35,"day":20,"accountId":"bank","category":"Πάγια","active":true},
              {"id":"old","name":"Παλιό","amount":200,"day":25,"accountId":"bank","category":"Σπίτι","active":true}
            ]},"state":{"settings":{},"recurringOverrides":{
              "old":{"id":"old","name":"Σε παύση","amount":200,"day":25,"accountId":"bank","category":"Σπίτι","active":false,"status":"paused"}
            },"recurringCustom":[
              {"id":"custom","name":"Προσαρμοσμένο","amount":60,"day":16,"accountId":"bank","category":"Άλλο","active":true}
            ],"events":[
              {"id":"early","kind":"expense","recurringId":"paid","date":"2026-10-05","amount":35}
            ],"scheduled":[]}}
        """.trimIndent()).jsonObject)
        val result=projectCanonicalPlanState(data,LocalDate.parse("2026-10-10"),null)
        val recurring=result.items.filter { it.kind==PlannedKind.RECURRING }
        assertEquals(listOf("custom","rent","paid"),recurring.map { it.id })
        assertEquals(listOf("2026-10-16","2026-10-20","2026-11-05"),recurring.map { it.dueDateIso })
        assertEquals(195.0,result.forecastObligations,0.001)
        assertEquals(205.0,result.forecastEndBalance,0.001)
        assertTrue(recurring.first().title.contains("Προσαρμοσμένο"))
        assertFalse(recurring.any { it.id=="old" })
    }
}
