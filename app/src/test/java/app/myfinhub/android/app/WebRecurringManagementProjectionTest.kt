package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.canonicalRecurringMonthlyTotal
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Production Plan control must include paused/custom records for reactivation. */
class WebRecurringManagementProjectionTest {
    @Test fun webCadenceAndServiceMetadataRemainVisibleInManagementWithoutUnapprovedAssets() {
        val data = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[],"recurring":[
             {"id":"monthly","name":"Internet","active":true,"accountId":"bank",
              "amount":24,"day":17,"logoAssetKey":"internet-provider","source":"seed","endDate":"2027-12-31"},
             {"id":"half","name":"Insurance","active":true,"accountId":"bank",
              "amount":120,"day":20,"recurrenceUnit":"month","recurrenceInterval":6},
             {"id":"yearly","name":"Streaming","active":true,"accountId":"bank",
              "amount":120,"recurrenceUnit":"year","recurrenceInterval":1,
              "firstExpectedDate":"2027-05-15"},
             {"id":"invalid","name":"Legacy","active":true,"accountId":"bank",
              "amount":30,"recurrenceInterval":0,"recurrenceUnit":"unexpected"}
          ]},"state":{"events":[]}}
        """.trimIndent()).jsonObject)
        val plan=projectCanonicalPlanState(data,LocalDate.parse("2026-10-17"),null)
        assertEquals(listOf("Κάθε μήνα","Κάθε 6 μήνες","Κάθε χρόνο","Κάθε μήνα"),
            plan.recurringManageItems.map { it.cadenceLabel })
        assertEquals("internet-provider",plan.recurringManageItems.first().logoAssetKey)
        assertEquals("seed",plan.recurringManageItems.first().source)
        assertEquals("2027-12-31",plan.recurringManageItems.first().endDateIso)
        assertEquals(84.0,data.canonicalRecurringMonthlyTotal(),0.001)
    }

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
