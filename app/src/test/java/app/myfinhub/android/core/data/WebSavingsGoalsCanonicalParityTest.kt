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
        assertEquals(listOf("old","future","invalid"),rows.map { it.id })
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
    @Test fun historicalInvalidTargetRowsRemainVisibleWithZeroProgressAsWeb() {
        val old = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{"savingsGoals":[
            {"id":"negative","name":"Αρνητικός","targetAmount":-100},
            {"id":"zero","name":"Μηδέν","targetAmount":0},
            {"id":"missing","name":"Αρχείο χωρίς ποσό","targetDate":"2026-99-99"},
            {"id":"nonfinite-number","name":"Δεν επιτρέπεται τιμή","targetAmount":null},
            {"id":"valid","name":"Διατηρημένος","targetAmount":500,
              "targetDate":"2026-09-21"}
          ]}}
        """.trimIndent()).jsonObject)
        val before = old.raw
        val rows = old.canonicalSavingsGoals(LocalDate.parse("2026-10-24"))
        assertEquals(listOf("negative","zero","missing","nonfinite-number","valid"),
            rows.map { it.id })
        assertTrue(rows.take(4).all { it.progressPercent == 0.0 })
        assertEquals(-100.0, rows[0].targetAmount, 0.001)
        assertEquals(0.0, rows[1].targetAmount, 0.001)
        assertEquals(0.0, rows[2].targetAmount, 0.001)
        // Web retains the imported literal deadline; invalid date is not past.
        assertEquals("2026-99-99", rows[2].targetDate)
        assertFalse(rows[2].deadlinePast)
        assertEquals(0.0, rows[3].targetAmount, 0.001)
        assertTrue(rows.last().deadlinePast)
        assertEquals(before, old.raw)
    }

}
