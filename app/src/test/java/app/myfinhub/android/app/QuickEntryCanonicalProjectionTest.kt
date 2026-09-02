package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickEntryCanonicalProjectionTest {
    @Test
    fun projection_usesCanonicalAccountsCardsDefaultsAndCategoryTrees() {
        val projected = projectQuickEntryState(
            document = projectionFixture(),
            today = LocalDate.parse("2026-09-02"),
            previous = null,
        )

        assertEquals(listOf("bank-main", "save-main", "cash-main"), projected.accounts.map { it.id })
        assertEquals("bank-main", projected.defaultExpenseAccountId)
        assertEquals("cash-main", projected.defaultIncomeAccountId)
        assertEquals("bank-main", projected.accountId)
        assertEquals("save-main", projected.toAccountId)
        assertEquals(listOf("credit-one"), projected.creditCards.map { it.id })
        assertTrue(projected.creditCards.single().label.contains("9012"))
        assertEquals(listOf("Τρόφιμα", "Μετακίνηση"), projected.expenseCategories.map { it.name })
        assertEquals(listOf("Σούπερ μάρκετ", "Καφές"), projected.expenseCategories.first().subcategories)
        assertEquals(listOf("Μισθός"), projected.incomeCategories.map { it.name })
    }

    @Test
    fun projection_preservesValidDraftButDropsStaleCanonicalSelections() {
        val previous = QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT,
            amountText = "40",
            dateText = "2026-09-01",
            note = "Εξόφληση",
            fromAccountId = "missing-account",
            cardId = "missing-card",
            dirty = true,
        )
        val projected = projectQuickEntryState(
            document = projectionFixture(),
            today = LocalDate.parse("2026-09-02"),
            previous = previous,
        )

        assertEquals(QuickEntryKind.CARD_PAYMENT, projected.kind)
        assertEquals("40", projected.amountText)
        assertEquals("2026-09-01", projected.dateText)
        assertEquals("Εξόφληση", projected.note)
        assertEquals("bank-main", projected.fromAccountId)
        assertEquals("credit-one", projected.cardId)
        assertTrue(projected.dirty)
        assertFalse(projected.accounts.any { it.id == "missing-account" })
    }
}

private fun projectionFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {
          "app":"MyFinHub",
          "schemaVersion":3,
          "updatedAt":"2026-09-02T00:00:00Z",
          "seed":{
            "accounts":[
              {"id":"bank-main","name":"Μισθοδοσία","kind":"bank"},
              {"id":"save-main","name":"Στόχος","kind":"savings"},
              {"id":"cash-main","name":"Πορτοφόλι","kind":"cash"}
            ],
            "snapshots":[],
            "transactions":[],
            "recurring":[],
            "loans":[],
            "lending":[]
          },
          "state":{
            "deleted":[],
            "overrides":{},
            "customTransactions":[],
            "events":[],
            "scheduled":[],
            "cards":[
              {"id":"credit-one","nickname":"Ταξίδια","kind":"credit","network":"mastercard","last4":"9012","active":true,"creditLimit":2500.0},
              {"id":"credit-archived","nickname":"Παλιή","kind":"credit","network":"visa","last4":"1111","active":false,"creditLimit":1000.0},
              {"id":"debit-one","nickname":"Χρεωστική","kind":"debit","network":"visa","last4":"4242","active":true}
            ],
            "budgets":[],
            "settings":{
              "accountNames":{},
              "excludedFromAvailable":[],
              "defaultExpenseAccount":"bank-main",
              "defaultIncomeAccount":"cash-main",
              "expenseCategoryTree":[
                {"name":"Τρόφιμα","subcategories":["Σούπερ μάρκετ","Καφές"]},
                {"name":"Μετακίνηση","subcategories":[]}
              ],
              "incomeCategoryTree":[
                {"name":"Μισθός","subcategories":[]}
              ],
              "expenseCategories":["Fallback έξοδο"],
              "incomeCategories":["Fallback έσοδο"]
            }
          }
        }
        """.trimIndent(),
    ).jsonObject,
)
