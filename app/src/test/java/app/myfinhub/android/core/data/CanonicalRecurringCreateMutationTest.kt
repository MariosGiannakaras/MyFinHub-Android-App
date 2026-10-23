package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Real FinanceData shape / Web recurring cadence and variable validation goldens. */
class CanonicalRecurringCreateMutationTest {
    private val before = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[
        {"id":"cash","name":"Μετρητά","kind":"cash"},
        {"id":"credit","name":"Κάρτα","kind":"credit"}],
        "recurring":[{"id":"seed","name":"Ενοίκιο","amount":350,"accountId":"cash","active":true}]},
       "state":{"events":[],"recurringCustom":[],"recurringOverrides":{},"unrelated":{"keep":"yes"}}}
    """.trimIndent()).jsonObject)
    private val now = "2026-10-23T07:00:00Z"
    private fun candidate(draft: CanonicalRecurringCreateDraft = CanonicalRecurringCreateDraft(
        name="Ρεύμα",amount=80.0,accountId="cash",category="Πάγια",
        amountMode="variable",estimateMin=60.0,estimateMax=100.0,
        recurrenceUnit="year",recurrenceInterval=1,day=23,
        firstExpectedDate="2026-11-23",endDate="2027-11-23",
    ), id: String = "rec-android-1") = CreateCanonicalRecurring(id,now,draft)

    @Test fun seededAndCustomArePreservedAndNoPaymentIsInvented() {
        val mutation=candidate()
        val added=mutation.apply(before)
        assertEquals(2,added.allCanonicalRecurringItems().size)
        assertEquals("seed", (added.seed.array("recurring").single() as JsonObject).string("id"))
        assertEquals(0,added.state.array("events").size)
        assertEquals("yes",added.state.obj("unrelated").string("keep"))
        val row=added.allCanonicalRecurringItems().last()
        assertEquals("variable",row.amountMode)
        assertEquals(60.0,row.estimateMin!!,0.0001)
        assertEquals("2027-11-23",row.endDate)
        assertEquals(12,row.recurrenceMonths)
        assertEquals(added.raw,mutation.apply(added).raw)
        val queued=PendingCanonicalMutationIntent.fromMutation(mutation,"intent-1")
        assertEquals(PendingMutationKind.CREATE_RECURRING,queued.kind)
        assertFalse(queued.isSatisfiedBy(before))
        assertEquals(added.raw,queued.asMutation().apply(before).raw)
        assertTrue(queued.isSatisfiedBy(added))
        assertEquals(emptyList<PendingCanonicalMutationIntent>(),
            reconcileSatisfiedPendingMutations(added,listOf(queued)))
    }

    @Test fun badRangeCadenceDateMoneyAndAccountAreRejected() {
        val original=candidate().draft
        for (d in listOf(
            original.copy(estimateMax=null),
            original.copy(estimateMin=90.0,estimateMax=89.0),
            original.copy(amount=80.001),
            original.copy(estimateMin=60.001),
            original.copy(amountMode="fixed"),
            original.copy(recurrenceInterval=121),
            original.copy(day=32),
            original.copy(firstExpectedDate="2026-02-30"),
            original.copy(recurrenceUnit="fortnight"),
            original.copy(firstExpectedDate=null),
            original.copy(accountId="credit"),
            original.copy(accountId="gone"),
        )) assertThrows(IllegalArgumentException::class.java) { candidate(d).apply(before) }
        assertEquals(0,before.state.array("recurringCustom").size)
    }

    @Test fun staleOrHistoricalIdCollisionCannotOverwriteSeedOrAnotherCustom() {
        val added=candidate().apply(before)
        assertThrows(IllegalArgumentException::class.java) {
            candidate(id="seed").apply(before)
        }
        assertThrows(IllegalArgumentException::class.java) {
            candidate(candidate().draft.copy(amount=95.0)).apply(added)
        }
        assertEquals(80.0,added.allCanonicalRecurringItems().last().amount,0.001)
    }

    @Test fun fixedMonthlyDefaultRequiresNoRangeOrAnchor() {
        val fixed=candidate(CanonicalRecurringCreateDraft(
            name="Μίσθωμα",amount=300.00,accountId="cash",category="Σπίτι",
            recurrenceUnit="month",recurrenceInterval=1,
        )).apply(before)
        assertEquals("fixed",fixed.allCanonicalRecurringItems().last().amountMode)
        assertEquals(null,fixed.allCanonicalRecurringItems().last().firstExpectedDate)
    }
}
