package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.monthlyFlow
import app.myfinhub.android.core.data.array
import app.myfinhub.android.core.data.string
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.canSubmitDraft
import app.myfinhub.android.feature.quickentry.reduceQuickEntry
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
        assertEquals("Mastercard", projected.creditCards.single().provider)
        val selectedCardPayment = projectQuickEntryState(
            projectionFixture(), LocalDate.parse("2026-09-02"),
            projected.copy(kind = QuickEntryKind.CARD_PAYMENT),
        )
        assertEquals(listOf("stmt-one"), selectedCardPayment.creditStatements.map { it.id })
        assertEquals(40.0, selectedCardPayment.creditStatements.single().remaining, 0.001)
        assertEquals(listOf("Τρόφιμα", "Μετακίνηση"), projected.expenseCategories.map { it.name })
        assertEquals(listOf("Σούπερ μάρκετ", "Καφές"), projected.expenseCategories.first().subcategories)
        assertEquals(listOf("Μισθός"), projected.incomeCategories.map { it.name })
    }


    @Test
    fun interactivePaymentDraft_refreshesPayableStatementsWhenCardDateKindAndResetChange() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
            """
            {"seed":{"accounts":[{"id":"payroll","kind":"bank","name":"Μισθοδοσία"}]},
             "state":{"cards":[
                {"id":"c1","kind":"credit","active":true,"network":"visa"},
                {"id":"c2","kind":"credit","active":true,"network":"mastercard"}
             ],"creditStatements":[
                {"id":"stmt-c1","cardId":"c1","openDate":"2026-09-26",
                 "closeDate":"2026-10-25","dueDate":"2026-11-05"},
                {"id":"stmt-c2","cardId":"c2","openDate":"2026-09-26",
                 "closeDate":"2026-10-25","dueDate":"2026-11-05"}
             ],"events":[
                {"id":"purchase-c1","kind":"card_purchase","date":"2026-10-01",
                 "amount":20,"cardId":"c1","statementId":"stmt-c1"},
                {"id":"purchase-c2","kind":"card_purchase","date":"2026-10-10",
                 "amount":30,"cardId":"c2","statementId":"stmt-c2"}
             ],"settings":{}}}
            """.trimIndent(),
        ).jsonObject)
        val today = LocalDate.parse("2026-10-15")
        val initial = projectQuickEntryState(doc, today, QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT,
            cardId = "c1",
            fromAccountId = "payroll",
            dateText = today.toString(),
            statementId = "stmt-c1",
            amountText = "10",
            note = "Εξόφληση",
            dirty = true,
        ))
        assertEquals(listOf("stmt-c1"), initial.creditStatements.map { it.id })
        assertEquals("stmt-c1", initial.statementId)

        val cardSwitched = reduceQuickEntryWithCanonicalStatements(
            doc, initial,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.CardChanged("c2"),
            today,
        )
        assertEquals("c2", cardSwitched.cardId)
        assertEquals("", cardSwitched.statementId)
        assertEquals(listOf("stmt-c2"), cardSwitched.creditStatements.map { it.id })
        assertEquals("10", cardSwitched.amountText)
        assertEquals("Εξόφληση", cardSwitched.note)
        assertTrue(cardSwitched.dirty)

        val earlierDate = reduceQuickEntryWithCanonicalStatements(
            doc, cardSwitched,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.DateChanged("2026-10-05"),
            today,
        )
        assertTrue(earlierDate.creditStatements.isEmpty())
        assertEquals("", earlierDate.statementId)
        val laterDate = reduceQuickEntryWithCanonicalStatements(
            doc, earlierDate,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.DateChanged("2026-10-15"),
            today,
        )
        assertEquals(listOf("stmt-c2"), laterDate.creditStatements.map { it.id })

        val selected = reduceQuickEntryWithCanonicalStatements(
            doc, laterDate,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.StatementChanged("stmt-c2"),
            today,
        )
        assertEquals("stmt-c2", selected.statementId)

        val otherKind = reduceQuickEntryWithCanonicalStatements(
            doc, selected,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE),
            today,
        )
        val backToPayment = reduceQuickEntryWithCanonicalStatements(
            doc, otherKind,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT),
            today,
        )
        assertEquals("c1", backToPayment.cardId)
        assertEquals(listOf("stmt-c1"), backToPayment.creditStatements.map { it.id })
        assertEquals("", backToPayment.statementId)

        val reset = reduceQuickEntryWithCanonicalStatements(
            doc, selected,
            app.myfinhub.android.feature.quickentry.QuickEntryAction.Reset,
            today,
        )
        assertEquals("", reset.statementId)
        assertEquals(
            projectQuickEntryState(doc, LocalDate.parse(reset.dateText), reset).creditStatements,
            reset.creditStatements,
        )
        assertFalse(reset.dirty)
    }

    @Test
    fun creditPayment_repaymentSourceFollowsCanonicalBankNotDefaultOrPreviousCard() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
            """
            {"seed":{"accounts":[
                {"id":"alpha-account","kind":"bank","name":"Alpha","providerId":"alpha"},
                {"id":"piraeus-account","kind":"bank","name":"Πειραιώς","providerId":"piraeus"},
                {"id":"cash-account","kind":"cash","name":"Μετρητά"}
             ]},
             "state":{"cards":[
                {"id":"piraeus-card","kind":"credit","active":true,"bankId":"piraeus","network":"visa"},
                {"id":"alpha-card","kind":"credit","active":true,"bankId":"alpha","network":"mastercard"},
                {"id":"unlinked-card","kind":"credit","active":true,"bankId":"","network":"visa"}
             ],"events":[
                {"id":"piraeus-charge","kind":"card_purchase","date":"2026-11-01","amount":20,"cardId":"piraeus-card"},
                {"id":"alpha-charge","kind":"card_purchase","date":"2026-11-02","amount":30,"cardId":"alpha-card"}
             ],"settings":{"defaultExpenseAccount":"alpha-account"}}}
            """.trimIndent(),
        ).jsonObject)
        val today = LocalDate.parse("2026-11-18")
        val original = projectQuickEntryState(doc, today, QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT,
            cardId = "piraeus-card",
            fromAccountId = "alpha-account",
            dateText = today.toString(),
            amountText = "10",
            note = "Μερική εξόφληση",
            dirty = true,
        ))

        assertEquals(setOf("piraeus-account"), original.cardPaymentSourceAccountIds)
        assertEquals("piraeus-account", original.fromAccountId)
        assertTrue(original.canSubmitDraft())

        val changedCard = reduceQuickEntryWithCanonicalStatements(
            doc, original, QuickEntryAction.CardChanged("alpha-card"), today,
        )
        assertEquals(setOf("alpha-account"), changedCard.cardPaymentSourceAccountIds)
        assertEquals("alpha-account", changedCard.fromAccountId)
        assertEquals("10", changedCard.amountText)
        assertEquals("Μερική εξόφληση", changedCard.note)
        assertTrue(changedCard.dirty)
        assertTrue(changedCard.canSubmitDraft())

        val wrongSource = reduceQuickEntry(
            changedCard, QuickEntryAction.FromAccountChanged("piraeus-account"),
        )
        assertFalse(wrongSource.canSubmitDraft())
        assertTrue(reduceQuickEntry(wrongSource, QuickEntryAction.Save)
            .validationMessage.orEmpty().contains("ίδιας τράπεζας"))

        val noBank = reduceQuickEntryWithCanonicalStatements(
            doc, changedCard, QuickEntryAction.CardChanged("unlinked-card"), today,
        )
        assertEquals(emptySet<String>(), noBank.cardPaymentSourceAccountIds)
        assertEquals("", noBank.fromAccountId)
        assertFalse(noBank.canSubmitDraft())
        assertTrue(reduceQuickEntry(noBank, QuickEntryAction.Save)
            .validationMessage.orEmpty().contains("ίδιας τράπεζας"))

        val restored = reduceQuickEntryWithCanonicalStatements(
            doc, noBank, QuickEntryAction.CardChanged("piraeus-card"), today,
        )
        assertEquals("piraeus-account", restored.fromAccountId)
        assertTrue(restored.canSubmitDraft())
    }


    @Test
    fun creditPaymentDebt_matchesCanonicalAsOfAndBlocksZeroOrOverpaymentBeforeSubmit() {
        val doc = projectionFixture()
        val today = LocalDate.parse("2026-09-02")
        val payment = projectQuickEntryState(doc, today, QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT,
            cardId = "credit-one",
            fromAccountId = "bank-main",
            dateText = today.toString(),
            amountText = "25",
            dirty = true,
        ))
        assertEquals(40.0, payment.cardPaymentDebt ?: Double.NaN, 0.001)
        assertTrue(payment.canSubmitDraft())

        val excessive = reduceQuickEntry(payment, QuickEntryAction.AmountChanged("40,02"))
        assertFalse(excessive.canSubmitDraft())
        assertTrue(reduceQuickEntry(excessive, QuickEntryAction.Save)
            .validationMessage.orEmpty().contains("ξεπερνά την οφειλή"))
        // Exactly the recorded debt is permitted by the canonical write boundary.
        val exact = reduceQuickEntry(payment, QuickEntryAction.AmountChanged("40"))
        assertTrue(exact.canSubmitDraft())

        val beforePurchase = reduceQuickEntryWithCanonicalStatements(
            doc, payment, QuickEntryAction.DateChanged("2026-08-31"), today,
        )
        assertEquals(0.0, beforePurchase.cardPaymentDebt ?: Double.NaN, 0.001)
        assertEquals("25", beforePurchase.amountText)
        assertFalse(beforePurchase.canSubmitDraft())
        assertTrue(reduceQuickEntry(beforePurchase, QuickEntryAction.Save)
            .validationMessage.orEmpty().contains("δεν έχει τρέχουσα οφειλή"))

        val restoredDate = reduceQuickEntryWithCanonicalStatements(
            doc, beforePurchase, QuickEntryAction.DateChanged("2026-09-02"), today,
        )
        assertEquals(40.0, restoredDate.cardPaymentDebt ?: Double.NaN, 0.001)
        assertTrue(restoredDate.canSubmitDraft())

        val asPurchase = reduceQuickEntryWithCanonicalStatements(
            doc, beforePurchase, QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE), today,
        )
        assertEquals(QuickEntryKind.CARD_PURCHASE, asPurchase.kind)
        assertTrue(asPurchase.canSubmitDraft())
        val backToPayment = reduceQuickEntryWithCanonicalStatements(
            doc, asPurchase, QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT), today,
        )
        assertEquals(0.0, backToPayment.cardPaymentDebt ?: Double.NaN, 0.001)
        assertFalse(backToPayment.canSubmitDraft())
    }

    @Test
    fun creditPurchaseQuickEntry_isNotSilentlyConvertedToPayrollExpense() {
        val document = projectionFixture()
        val date = LocalDate.parse("2026-09-02")
        val projected = projectQuickEntryState(document, date, null)
        // Reproduce card-detail route: Reset, SelectKind, CardChanged, enter amount, Save.
        val reset = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            projected, app.myfinhub.android.feature.quickentry.QuickEntryAction.Reset,
        )
        val selected = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            reset, app.myfinhub.android.feature.quickentry.QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE),
        )
        val cardSelected = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            selected, app.myfinhub.android.feature.quickentry.QuickEntryAction.CardChanged("credit-one"),
        )
        // Reset deliberately sets the actual device date; pin the deterministic fixture date
        // before asserting September flow. The production route keeps today's date.
        val dated = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            cardSelected, app.myfinhub.android.feature.quickentry.QuickEntryAction.DateChanged(date.toString()),
        )
        val filled = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            dated, app.myfinhub.android.feature.quickentry.QuickEntryAction.AmountChanged("12,50"),
        )
        val saved = app.myfinhub.android.feature.quickentry.reduceQuickEntry(
            filled, app.myfinhub.android.feature.quickentry.QuickEntryAction.Save,
        )
        assertEquals(QuickEntryKind.CARD_PURCHASE, saved.kind)
        assertEquals("credit-one", saved.cardId)
        assertEquals(null, saved.validationMessage)

        val next = createQuickEntryCanonicalMutation(
            document = document,
            state = saved,
            eventId = "evt-card-quickentry",
            nowIso = "2026-09-02T10:00:00Z",
        ).apply(document)
        val event = next.state.array("events").last() as kotlinx.serialization.json.JsonObject
        assertEquals("card_purchase", event.string("kind"))
        assertEquals("credit-one", event.string("cardId"))
        assertEquals(null, event.string("accountId"))
        val leg = event.array("legs").single() as kotlinx.serialization.json.JsonObject
        assertEquals("credit-card", leg.string("accountId"))
        assertEquals(-12.50, leg["amount"]?.toString()?.toDouble() ?: Double.NaN, 0.001)
        assertEquals(12.50, next.monthlyFlow("2026-09").expense - document.monthlyFlow("2026-09").expense, 0.001)
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
            statementId = "stale-statement",
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
        assertEquals("", projected.statementId)
        assertTrue(projected.dirty)
        assertFalse(projected.accounts.any { it.id == "missing-account" })
    }
    @Test
    fun paymentSelectorOrdersDueThenClosedThenOpenAndOmitsPaidAndOtherCards() {
        val doc = CanonicalFinanceDocument(Json.parseToJsonElement(
            """
            {"seed":{"accounts":[{"id":"payroll","kind":"bank","name":"Μισθοδοσία"}]},
             "state":{"cards":[
                 {"id":"c1","kind":"credit","active":true,"network":"visa"},
                 {"id":"c2","kind":"credit","active":true,"network":"mastercard"}
               ],"creditStatements":[
                 {"id":"open","cardId":"c1","openDate":"2026-09-26","closeDate":"2026-10-25","dueDate":"2026-11-05"},
                 {"id":"other","cardId":"c2","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-05"},
                 {"id":"closed","cardId":"c1","openDate":"2026-09-06","closeDate":"2026-10-05","dueDate":"2026-10-25"},
                 {"id":"paid","cardId":"c1","openDate":"2026-07-26","closeDate":"2026-08-25","dueDate":"2026-09-05"},
                 {"id":"due","cardId":"c1","openDate":"2026-08-26","closeDate":"2026-09-25","dueDate":"2026-10-05"}
               ],"events":[
                 {"id":"p-due","kind":"card_purchase","date":"2026-09-15","amount":30,"cardId":"c1","statementId":"due"},
                 {"id":"p-closed","kind":"card_purchase","date":"2026-10-01","amount":25,"cardId":"c1","statementId":"closed"},
                 {"id":"p-open","kind":"card_purchase","date":"2026-10-14","amount":50,"cardId":"c1","statementId":"open"},
                 {"id":"p-paid","kind":"card_purchase","date":"2026-08-15","amount":12,"cardId":"c1","statementId":"paid"},
                 {"id":"pay-paid","kind":"card_payment","date":"2026-09-01","amount":12,"cardId":"c1","statementId":"paid"},
                 {"id":"p-other","kind":"card_purchase","date":"2026-09-15","amount":90,"cardId":"c2","statementId":"other"}
               ]}}
            """.trimIndent()).jsonObject)
        val base = QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT, cardId = "c1",
            dateText = "2026-10-15", statementId = "closed",
        )
        val projected = projectQuickEntryState(doc, LocalDate.parse("2026-10-15"), base)
        assertEquals(listOf("due", "closed", "open"), projected.creditStatements.map { it.id })
        assertEquals("closed", projected.statementId)
        assertTrue(projected.creditStatements[0].label.contains("Ληξιπρόθεσμη"))
        assertTrue(projected.creditStatements[0].label.contains("30,00 €"))
        assertTrue(projected.creditStatements[1].label.contains("Κλειστή"))
        assertTrue(projected.creditStatements[2].label.contains("Ανοιχτή"))
        assertEquals(30.0, projected.creditStatements[0].remaining, 0.001)
        val wrongCard = projectQuickEntryState(doc, LocalDate.parse("2026-10-15"),
            base.copy(cardId = "c2", statementId = "due"))
        assertEquals(listOf("other"), wrongCard.creditStatements.map { it.id })
        assertEquals("", wrongCard.statementId)
        val paidAttempt = projectQuickEntryState(doc, LocalDate.parse("2026-10-15"),
            base.copy(statementId = "paid"))
        assertEquals("", paidAttempt.statementId)
    }

    @Test
    fun fullCanonicalStatementSourceRejectsCrossCardAndMalformedDuplicates() {
        val doc = duplicateSourceFixture()
        val date = LocalDate.parse("2026-10-15")
        val attempt = QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT,
            cardId = "c1", dateText = date.toString(),
            statementId = "shared", fromAccountId = "payroll",
            amountText = "10", dirty = true,
        )
        val projected = projectQuickEntryState(doc, date, attempt)
        // A foreign source with the same ID is invisible in selected-card
        // creditStatementViews but must still invalidate this option.
        assertEquals(listOf("unique"), projected.creditStatements.map { it.id })
        assertEquals("", projected.statementId)
        assertEquals("10", projected.amountText)
        assertTrue(projected.dirty)
        val malformed = projectQuickEntryState(doc, date,
            attempt.copy(statementId = "malformed"))
        assertEquals("", malformed.statementId)
        assertFalse(projected.creditStatements.any { it.id == "shared" || it.id == "malformed" })

        // Defend the canonical mutation constructor against stale or manually
        // injected UI choices, even if the UI no longer offers those IDs.
        for (id in listOf("shared", "malformed")) {
            val unsafe = runCatching {
                createQuickEntryCanonicalMutation(
                    doc, attempt.copy(statementId = id),
                    "unsafe-$id", "2026-10-15T10:00:00Z",
                )
            }
            assertTrue("Untrusted statement id $id was accepted", unsafe.isFailure)
        }
        val safe = createQuickEntryCanonicalMutation(
            doc, attempt.copy(statementId = "unique"),
            "valid-payment", "2026-10-15T10:00:00Z",
        ).apply(doc)
        val newEvent = safe.state.array("events").last() as kotlinx.serialization.json.JsonObject
        assertEquals("unique", newEvent.string("statementId"))
        assertEquals("c1", newEvent.string("cardId"))
    }

    @Test
    fun changingCanonicalStatementSourceRemovesPreviouslyValidSelection() {
        val doc = duplicateSourceFixture()
        val date = LocalDate.parse("2026-10-15")
        val previous = projectQuickEntryState(doc, date, QuickEntryUiState(
            kind = QuickEntryKind.CARD_PAYMENT, cardId = "c1",
            statementId = "unique", dateText = date.toString(), amountText = "10",
        ))
        assertEquals("unique", previous.statementId)
        val original = doc.state.array("creditStatements")
        val collided = doc.copy(raw = kotlinx.serialization.json.JsonObject(
            doc.raw + ("state" to kotlinx.serialization.json.JsonObject(
                doc.state + ("creditStatements" to kotlinx.serialization.json.JsonArray(
                    original + kotlinx.serialization.json.Json.parseToJsonElement(
                        """{"id":"unique","cardId":"c2"}"""
                    )
                ))
            ))
        ))
        val refreshed = projectQuickEntryState(collided, date, previous)
        assertEquals("", refreshed.statementId)
        assertFalse(refreshed.creditStatements.any { it.id == "unique" })
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
              {"id":"bank-main","name":"Μισθοδοσία","kind":"bank","providerId":"piraeus"},
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
            "events":[
              {"id":"fixture-purchase","kind":"card_purchase","date":"2026-09-01",
               "cardId":"credit-one","statementId":"stmt-one","amount":40}
            ],
            "creditStatements":[
              {"id":"stmt-one","cardId":"credit-one","openDate":"2026-08-26",
               "closeDate":"2026-09-25","dueDate":"2026-10-05","boundaryRule":"next-cycle"}
            ],
            "scheduled":[],
            "cards":[
              {"id":"credit-one","nickname":"Ταξίδια","kind":"credit","bankId":"piraeus","network":"mastercard","last4":"9012","active":true,"creditLimit":2500.0},
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

private fun duplicateSourceFixture(): CanonicalFinanceDocument = CanonicalFinanceDocument(
    Json.parseToJsonElement(
        """
        {"seed":{"accounts":[{"id":"payroll","kind":"bank","name":"Μισθοδοσία",
                                "providerId":"piraeus"}],
                 "transactions":[]},
         "state":{"cards":[
            {"id":"c1","kind":"credit","active":true,"network":"visa","bankId":"piraeus"},
            {"id":"c2","kind":"credit","active":true,"network":"visa","bankId":"piraeus"}
         ],"creditStatements":[
            {"id":"shared","cardId":"c1","openDate":"2026-08-26",
             "closeDate":"2026-09-25","dueDate":"2026-10-05"},
            {"id":"shared","cardId":"c2","openDate":"2026-08-26",
             "closeDate":"2026-09-25","dueDate":"2026-10-05"},
            {"id":"malformed","cardId":"c1","openDate":"2026-08-26",
             "closeDate":"2026-09-25","dueDate":"2026-10-05"},
            {"id":"malformed","cardId":"c2"},
            {"id":"unique","cardId":"c1","openDate":"2026-08-26",
             "closeDate":"2026-09-25","dueDate":"2026-10-05"}
         ],"events":[
            {"id":"purchase-shared-c1","kind":"card_purchase","date":"2026-09-20",
             "amount":25,"cardId":"c1","statementId":"shared"},
            {"id":"purchase-shared-c2","kind":"card_purchase","date":"2026-09-20",
             "amount":20,"cardId":"c2","statementId":"shared"},
            {"id":"purchase-malformed","kind":"card_purchase","date":"2026-09-20",
             "amount":10,"cardId":"c1","statementId":"malformed"},
            {"id":"purchase-unique","kind":"card_purchase","date":"2026-09-20",
             "amount":30,"cardId":"c1","statementId":"unique"}
         ],"settings":{}}}
        """.trimIndent(),
    ).jsonObject,
)
