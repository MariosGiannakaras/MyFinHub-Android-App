package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web edit of full seeded override and existing custom, with no event creation. */
class CanonicalRecurringEditMutationTest {
    private val before = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"accounts":[{"id":"cash","name":"Cash","kind":"cash"},
        {"id":"credit","name":"Credit","kind":"credit"}],
        "recurring":[{"id":"seed","name":"Electricity","amount":60,
          "accountId":"cash","category":"Utilities","active":true,
          "logoAssetKey":"logo-certified","source":"bank","customMetadata":"keep"}]},
       "state":{"events":[],"recurringOverrides":{},
         "recurringCustom":[{"id":"custom","name":"TV","amount":20,"accountId":"cash",
           "category":"Services","active":false,"status":"paused","source":"user",
           "note":"retained","logoAssetKey":"icon"}],"unknownState":"stay"}}
    """.trimIndent()).jsonObject)
    private val updated = CanonicalRecurringCreateDraft(
        name="Electricity",amount=80.0,accountId="cash",category="Utilities",
        amountMode="variable",estimateMin=60.0,estimateMax=100.0,
        recurrenceUnit="year",firstExpectedDate="2026-12-23",day=23,
    )
    private val now="2026-10-23T09:00:00Z"
    private fun edit(document: CanonicalFinanceDocument, id: String,
        draft: CanonicalRecurringCreateDraft = updated
    ) = EditCanonicalRecurring(id,document.canonicalRecurringEditSource(id)!!.row,draft,now)

    @Test fun seedEditCreatesOverrideWithoutRewritingSeedOrOriginalLogo() {
        val mutation=edit(before,"seed")
        val after=mutation.apply(before)
        assertEquals("logo-certified",
            (after.state.obj("recurringOverrides")["seed"] as JsonObject).string("logoAssetKey"))
        assertEquals("bank",(after.state.obj("recurringOverrides")["seed"] as JsonObject).string("source"))
        assertEquals("keep",(after.state.obj("recurringOverrides")["seed"] as JsonObject).string("customMetadata"))
        assertEquals(60.0,(after.seed.array("recurring").single() as JsonObject).number("amount")!!,0.001)
        assertEquals(80.0,after.allCanonicalRecurringItems().first().amount,0.001)
        assertEquals(0,after.state.array("events").size)
        assertEquals(after.raw,mutation.apply(after).raw)
        val intent=PendingCanonicalMutationIntent.fromMutation(mutation,"update-seed")
        assertEquals(PendingMutationKind.EDIT_RECURRING,intent.kind)
        assertFalse(intent.isSatisfiedBy(before))
        assertEquals(after.raw,intent.asMutation().apply(before).raw)
        assertTrue(intent.isSatisfiedBy(after))
    }

    @Test fun pausedCustomEditPreservesLifecycleAndUnrelatedMetadata() {
        val original=CanonicalRecurringCreateDraft(
            name="TV plus",amount=25.0,accountId="cash",category="Services",
        )
        val result=edit(before,"custom",original).apply(before)
        val row=result.state.array("recurringCustom").single() as JsonObject
        assertEquals("paused",row.string("status"))
        assertEquals(false,row.bool("active"))
        assertEquals("icon",row.string("logoAssetKey"))
        assertEquals("retained",row.string("note"))
        assertEquals("stay",result.state.string("unknownState"))
        assertEquals(25.0,row.number("amount")!!,0.001)
    }

    @Test fun rejectsRemoteChangeOrDuplicateHistoricalIdAndCannotOverwriteAnotherAccount() {
        val mutation=edit(before,"seed")
        val concurrent=before.state.updated("recurringOverrides",
            JsonObject(mapOf("seed" to JsonObject(
                (mutation.expected)+mapOf("name" to JsonPrimitive("Other user edit"))))))
        val remote=before.copy(raw=JsonObject(before.raw+mapOf("state" to concurrent)))
        assertFalse(PendingCanonicalMutationIntent.fromMutation(mutation,"conflict").isSatisfiedBy(remote))
        assertThrows(IllegalArgumentException::class.java) { mutation.apply(remote) }
        assertThrows(IllegalArgumentException::class.java) {
            edit(before,"seed",updated.copy(accountId="credit")).apply(before)
        }
        val duplicate=before.copy(raw=JsonObject(before.raw+mapOf(
            "state" to before.state.updated("recurringCustom",JsonArray(
                before.state.array("recurringCustom") +
                    JsonObject(mapOf("id" to JsonPrimitive("seed")))
            ))
        )))
        assertEquals(null,duplicate.canonicalRecurringEditSource("seed"))
    }

    @Test fun legacyMonthlyEditPreservesMissingScheduleAnchorAndHistory() {
        val monthly = CanonicalRecurringCreateDraft(
            name = "Electricity revised", amount = 61.0,
            accountId = "cash", category = "Utilities",
            recurrenceUnit = "month", recurrenceInterval = 1,
            firstExpectedDate = null,
        )
        val changed = edit(before, "seed", monthly).apply(before)
        val row = changed.state.obj("recurringOverrides")["seed"] as JsonObject
        assertFalse(row.containsKey("firstExpectedDate"))
        assertEquals("Electricity revised", row.string("name"))
        assertEquals("logo-certified", row.string("logoAssetKey"))
        assertEquals(0, changed.state.array("events").size)
    }

    @Test fun storedEditRoundTripsExactExpectedSource() {
        val original=edit(before,"custom",CanonicalRecurringCreateDraft(
            name="TV+",amount=22.0,accountId="cash",category="Subscriptions",
        ))
        val queue=PendingCanonicalMutationIntent.fromMutation(original,"edit-custom")
        assertEquals(original.editedRow(),
            (queue.asMutation() as EditCanonicalRecurring).editedRow())
        val applied=queue.asMutation().apply(before)
        assertTrue(queue.isSatisfiedBy(applied))
        assertEquals(0,applied.state.array("events").size)
    }
}
