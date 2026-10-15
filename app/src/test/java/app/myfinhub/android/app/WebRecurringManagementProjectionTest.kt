package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Production Plan control must include paused/custom records for reactivation. */
class WebRecurringManagementProjectionTest {
    @Test fun customPausedStoppedAndActiveRemainManageableWithoutInventedPayments() {
        val data=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"recurring":[
              {"id":"seed","name":"Internet","amount":30,"active":true,"day":25,"accountId":"bank"}
          ]},"state":{
              "recurringOverrides":{"seed":{"id":"seed","name":"Internet","amount":30,
                "status":"paused","active":false,"day":25,"accountId":"bank"}},
              "recurringCustom":[
                 {"id":"custom","name":"Πάγιο","amount":70,"active":true,"day":20,"accountId":"bank"},
                 {"id":"stopped","name":"Παλιό","amount":10,"active":false,"status":"stopped","accountId":"bank"}
              ],"settings":{},"events":[]
          }}
        """.trimIndent()).jsonObject)
        val state=projectCanonicalPlanState(data,LocalDate.parse("2026-10-15"),null)
        assertEquals(listOf("seed","custom","stopped"),state.recurringManageItems.map { it.id })
        assertEquals(listOf("paused","active","stopped"),state.recurringManageItems.map { it.status })
        assertEquals("",state.recurringManageItems.first().nextDueDateIso)
        assertEquals("2026-10-20",state.recurringManageItems[1].nextDueDateIso)
        assertEquals(listOf("custom"),state.items.filter {
            it.kind == app.myfinhub.android.feature.plan.PlannedKind.RECURRING
        }.map { it.id })
    }
}
