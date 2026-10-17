package app.myfinhub.android.app

import java.time.LocalDate
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.canonicalEvents
import app.myfinhub.android.core.data.accountBalances
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** Real Plan → editable Quick Entry → canonical recurring-linked expense seam. */
class WebRecurringQuickEntryUiBridgeTest {
    private val day = LocalDate.parse("2026-10-17")
    private val source = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"salary","name":"Μισθοδοσία","kind":"bank",
          "bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"salary":900}}],
        "transactions":[],
        "recurring":[{"id":"legacy-cloud","name":"Cloud","amount":19.99,
          "day":18,"active":true,"accountId":"salary",
          "category":"Παλιά συνδρομή"}]
      },"state":{"events":[],
        "settings":{"expenseCategories":["Τρόφιμα","Άλλο"],
          "incomeCategories":["Μισθός"],"defaultExpenseAccount":"salary"}}}
    """.trimIndent()).jsonObject)

    @Test fun importedRecurringWithRetiredCategoryIsPreservedOnEditablePayment() {
        val initial = projectQuickEntryState(source,day,null)
        var state = reduceQuickEntry(initial,QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE))
        state = reduceQuickEntry(state,QuickEntryAction.RecurringChanged(
            "legacy-cloud","Παλιά συνδρομή",
        ))
        assertEquals("Παλιά συνδρομή",state.category)
        assertEquals("Παλιά συνδρομή",state.expenseCategories.last().name)
        state = reduceQuickEntry(state,QuickEntryAction.AmountChanged("19,99"))
        state = reduceQuickEntry(state,QuickEntryAction.NoteChanged("Πληρωμή: Cloud"))
        state = projectQuickEntryState(source,day,state)
        assertEquals("legacy-cloud",state.recurringId)
        assertEquals("Παλιά συνδρομή",state.expenseCategories.last().name)
        state = reduceQuickEntry(state,QuickEntryAction.CategoryChanged("Παλιά συνδρομή"))
        state = reduceQuickEntry(state,QuickEntryAction.AccountChanged("salary"))
        val validated=reduceQuickEntry(state,QuickEntryAction.Save)
        assertNull(validated.validationMessage)
        val intent=createQuickEntryCanonicalMutation(source,validated,
            "linked-recurring-evt","2026-10-17T12:00:00Z")
        val paid=intent.apply(source)
        val event=paid.canonicalEvents().single()
        assertEquals("expense",event.kind)
        assertEquals("Παλιά συνδρομή",event.category)
        assertEquals(19.99,event.amount,0.001)
        assertEquals("legacy-cloud",(paid.state.array("events").single() as JsonObject)
            .string("recurringId"))
        assertEquals(880.01,paid.accountBalances("2026-10-17")["salary"]!!,0.001)
        assertEquals(19.99,paid.monthlyFlow("2026-10","2026-10-17").expense,0.001)
        assertEquals(paid.raw,intent.apply(paid).raw)
    }

    @Test fun switchingKindsAndResetDiscardLinkSoNormalTransactionsCannotInheritIt() {
        val initial = projectQuickEntryState(source,day,null)
        val scoped = reduceQuickEntry(initial,QuickEntryAction.RecurringChanged("legacy-cloud"))
        assertEquals("legacy-cloud",scoped.recurringId)
        val changed = reduceQuickEntry(scoped,QuickEntryAction.SelectKind(QuickEntryKind.INCOME))
        assertEquals("",changed.recurringId)
        val ignored = reduceQuickEntry(changed,QuickEntryAction.RecurringChanged("legacy-cloud"))
        assertEquals("",ignored.recurringId)
        val restored=reduceQuickEntry(
            reduceQuickEntry(changed,QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE)),
            QuickEntryAction.RecurringChanged("legacy-cloud"),
        )
        assertEquals("",reduceQuickEntry(restored,QuickEntryAction.Reset).recurringId)
    }

    @Test fun deletedRecurringCannotBeReattachedByStaleQuickEntryId() {
        val sourceState = projectQuickEntryState(source,day,null)
        val stale = reduceQuickEntry(sourceState,QuickEntryAction.RecurringChanged("unknown"))
        val refreshed = projectQuickEntryState(source,day,stale)
        assertEquals("",refreshed.recurringId)
        assertFalse(refreshed.expenseCategories.any { it.name=="Παλιά συνδρομή" })
    }
}
