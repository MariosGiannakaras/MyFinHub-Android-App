package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web multi-goal records must reach the production Money screen rather than preview data. */
class WebSavingsGoalMoneyProductionParityTest {
    @Test fun distinctGoalsShareEligibleSavingsAssetsButNotPayrollOrCash() {
        val document=CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[
            {"id":"cash","name":"Μετρητά","kind":"cash","cashRole":"daily"},
            {"id":"payroll","name":"Μισθοδοσία","kind":"bank","bankAccountCategory":"payroll"},
            {"id":"saving-bank","name":"Αποταμίευση τράπεζας","kind":"bank","bankAccountCategory":"savings"},
            {"id":"saving-kind","name":"Στόχος","kind":"savings"}
           ],"snapshots":[{"date":"2026-10-15","balances":{
             "cash":200,"payroll":1100,"saving-bank":250,"saving-kind":75
           }}],"transactions":[]},
           "state":{"settings":{},"events":[],"savingsGoals":[
              {"id":"emergency","name":"Ταμείο ασφαλείας","targetAmount":1000,"targetDate":"2026-09-01"},
              {"id":"trip","name":"Ταξίδι","targetAmount":250,"targetDate":"2027-04-01"}
           ]}}
        """.trimIndent()).jsonObject)
        val original = document.raw
        val product=projectCanonicalProduct(document,LocalDate.parse("2026-10-15"))
        val goals=product.moneyState.savingsGoalItems
        assertEquals(listOf("emergency","trip"),goals.map { it.id })
        assertEquals(325.0,product.moneyState.savingsCurrent,0.001)
        assertTrue(goals.all { it.backingBalance == 325.0 })
        assertEquals(32.5,goals[0].progressPercent,0.001)
        assertEquals(100.0,goals[1].progressPercent,0.001)
        assertTrue(goals[0].deadlinePast)
        assertFalse(goals[1].deadlinePast)
        assertNull(product.moneyState.savingsGoal)
        val advisory = product.moneyState.savingsGoalAdvisories
        assertEquals(2, advisory.size)
        assertEquals("overdue", advisory[0].projection?.status)
        assertEquals("reached", advisory[1].projection?.status)
        assertTrue(advisory.all { it.error == null })
        assertEquals(original, document.raw)
    }
}
