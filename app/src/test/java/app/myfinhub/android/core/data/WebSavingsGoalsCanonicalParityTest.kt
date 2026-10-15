package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web savingsGoals.ts: positive all-savings backing, multiple independent goals, old deadlines. */
class WebSavingsGoalsCanonicalParityTest {
    private val data=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"savings","name":"Αποταμίευση","kind":"savings"},
        {"id":"bank-saving","name":"Τράπεζα","kind":"bank","bankAccountCategory":"savings"},
        {"id":"negative","name":"Υπολογαριασμός","kind":"savings"},
        {"id":"payroll","name":"Μισθοδοσία","kind":"bank"}
       ],"snapshots":[{"date":"2026-10-15","balances":{
         "savings":200,"bank-saving":90,"negative":-50,"payroll":1500
       }}]},
       "state":{"settings":{},"events":[],"savingsGoals":[
         {"id":"old","name":"Ταμείο ασφαλείας","targetAmount":500,"targetDate":"2026-09-01"},
         {"id":"future","name":"Ταξίδι","targetAmount":100,"targetDate":"2027-01-10"},
         {"id":"invalid","name":"Skip","targetAmount":0}
       ]}}
    """.trimIndent()).jsonObject)

    @Test fun multipleGoalsShareOnlyEligiblePositiveSavingsAndDoNotDoubleAllocateMoney() {
        val rows=data.canonicalSavingsGoals(LocalDate.parse("2026-10-15"))
        assertEquals(listOf("old","future"),rows.map { it.id })
        assertTrue(rows.all { it.backingBalance == 290.0 })
        assertEquals(58.0,rows[0].progressPercent,0.001)
        assertEquals(100.0,rows[1].progressPercent,0.001)
        assertTrue(rows[0].deadlinePast)
        assertFalse(rows[1].deadlinePast)
    }

    @Test fun missedDeadlinePreservesHistoricalGoalInsteadOfDiscardingIt() {
        val row=data.canonicalSavingsGoals(LocalDate.parse("2026-10-15")).first()
        assertEquals("2026-09-01",row.targetDate)
        assertTrue(row.deadlinePast)
        assertEquals(500.0,row.targetAmount,0.001)
    }
}
