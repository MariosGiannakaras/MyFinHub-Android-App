package app.myfinhub.android.feature.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityReducerTest {
    @Test
    fun categoryScope_isExactInclusiveAndIndependentOfGlobalFilters() {
        fun row(id: String, date: String, category: String, amount: Double, contribution: Double) =
            ActivityItem(id, date, ActivityKind.EXPENSE, "Τρόφιμα στο σημείωμα", "", amount, "bank", category,
                rawDate = date, categoryContributions = mapOf(category to contribution))
        val state = ActivityUiState(query = "global query", filter = ActivityFilter.TRANSFER,
            accountFilterId = "other", selectedId = "split", items = listOf(
                row("split", "2026-09-01", "Τρόφιμα", -100.0, 20.0),
                row("refund", "2026-09-10", "Τρόφιμα", 5.0, -5.0),
                row("substring", "2026-09-10", "Τρόφιμα εκτός", -10.0, 10.0),
                row("old", "2026-08-31", "Τρόφιμα", -10.0, 10.0),
                row("future", "2026-09-11", "Τρόφιμα", -10.0, 10.0),
            ))
        val scoped = state.forCategory("Τρόφιμα", "2026-09-01", "2026-09-10")
        assertEquals(listOf("split", "refund"), scoped.visibleItems.map { it.id })
        assertEquals(listOf(-20.0, 5.0), scoped.visibleItems.map { it.amount })
        assertEquals(-100.0, state.selectedItem!!.amount, 0.0)
        assertEquals("global query", state.query)
        assertEquals(ActivityFilter.TRANSFER, state.filter)
        assertEquals("other", state.accountFilterId)
        assertTrue(state.forCategory("Τρόφιμα", "2026-09-10", "2026-09-01").visibleItems.isEmpty())
    }

    @Test
    fun refundOnlyAndRefundExceedingExpenseKeepSignedWebOperationalTotals() {
        // Web flowImpactEvent(refund) returns expense=-amount, income=0.
        val refund = ActivityItem(
            "refund", "20 Οκτ", ActivityKind.INCOME, "Επιστροφή", "", 30.0,
            "Μετρητά", "Τρόφιμα", rawDate = "2026-10-20",
            canonicalKind = "refund",
            financeIncomeContribution = 0.0,
            financeExpenseContribution = -30.0,
        )
        val refundOnly = ActivityUiState(items = listOf(refund))
        assertEquals(0.0, refundOnly.visibleIncome, 0.001)
        assertEquals(-30.0, refundOnly.visibleExpense, 0.001)
        assertEquals(30.0, refundOnly.visibleNet, 0.001)

        val expense = ActivityItem(
            "expense", "20 Οκτ", ActivityKind.EXPENSE, "Αγορά", "", -12.0,
            "Μετρητά", "Τρόφιμα", rawDate = "2026-10-20",
            financeIncomeContribution = 0.0,
            financeExpenseContribution = 12.0,
        )
        val combined = refundOnly.copy(items = listOf(expense, refund))
        assertEquals(-18.0, combined.visibleExpense, 0.001)
        assertEquals(18.0, combined.visibleNet, 0.001)
        assertEquals(listOf("expense", "refund"), combined.visibleItems.map { it.id })
    }

    @Test
    fun fallbackFlowUsesExactWebKindsWithoutTreatingRepaymentsAsExpensesOrRefundsAsIncome() {
        fun item(id: String, kind: ActivityKind, canonical: String, amount: Double) =
            ActivityItem(
                id, "20 Οκτ", kind, canonical, "", amount, "Μετρητά", null,
                rawDate = "2026-10-20", canonicalKind = canonical,
            )
        val state = ActivityUiState(items = listOf(
            item("expense", ActivityKind.EXPENSE, "expense", -12.0),
            item("purchase", ActivityKind.EXPENSE, "card_purchase", -8.0),
            item("split", ActivityKind.EXPENSE, "split", -5.0),
            item("refund", ActivityKind.INCOME, "refund", 30.0),
            item("card-payment", ActivityKind.CARD_PAYMENT, "card_payment", -10.0),
            item("transfer", ActivityKind.TRANSFER, "transfer", 50.0),
            item("lending", ActivityKind.EXPENSE, "lending", -20.0),
            item("repayment", ActivityKind.INCOME, "repayment", 10.0),
            item("income", ActivityKind.INCOME, "income", 100.0),
        ))
        assertEquals(100.0, state.visibleIncome, 0.001)
        assertEquals(-5.0, state.visibleExpense, 0.001)
        assertEquals(105.0, state.visibleNet, 0.001)
    }

    @Test
    fun groupingManySameDayItemsRetainsRowOrderAndDoesNotChangeCanonicalItems() {
        val originals = (0 until 1000).map { index ->
            ActivityItem(
                id = "event-$index", dateLabel = "20 Οκτ",
                rawDate = "2026-10-20", kind = ActivityKind.EXPENSE,
                title = "Κίνηση $index", subtitle = "δοκιμή",
                amount = -5.0, accountLabel = "Μετρητά", category = "Άλλο",
            )
        }
        val scope = ActivityUiState(items = originals)
        assertEquals(1, scope.visibleSections.size)
        assertEquals("2026-10-20", scope.visibleSections.single().date)
        assertEquals(originals.map { it.id },
            scope.visibleSections.single().items.map { it.id })
        assertEquals(originals, scope.items)
    }

    @Test
    fun groupingNeverMergesNoncontiguousDatesAndPreservesLegacyDateLabelFallback() {
        val originals = listOf(
            ActivityItem(
                "first", "Σήμερα", ActivityKind.EXPENSE,
                "Πρώτη", "", -1.0, "Μετρητά", null,
                rawDate = "2026-10-20",
            ),
            ActivityItem(
                "middle", "Χθες", ActivityKind.EXPENSE,
                "Δεύτερη", "", -2.0, "Μετρητά", null,
                rawDate = "",
            ),
            ActivityItem(
                "last", "Σήμερα", ActivityKind.EXPENSE,
                "Τρίτη", "", -3.0, "Μετρητά", null,
                rawDate = "2026-10-20",
            ),
        )
        val scope = ActivityUiState(items = originals)
        assertEquals(listOf("2026-10-20", "Χθες", "2026-10-20"),
            scope.visibleSections.map { it.date })
        assertEquals(listOf("first", "middle", "last"),
            scope.visibleSections.flatMap { it.items }.map { it.id })
    }

    @Test
    fun analyticsRemainderOnlyShowsNonzeroNetContributionAndEmptyMembershipMatchesNothing() {
        fun row(id: String, category: String, signed: Map<String, Double>) = ActivityItem(
            id, "20 Οκτ", ActivityKind.EXPENSE, id, "", -50.0, "Μετρητά", category,
            rawDate = "2026-10-20", categoryContributions = signed,
        )
        val state = ActivityUiState(items = listOf(
            row("cancelled", "Α", mapOf("Α" to 12.0, "Β" to -12.0)),
            row("real", "Α", mapOf("Α" to 20.0, "Β" to -5.0)),
            row("unrelated", "Α", emptyMap()),
        ))
        val scope = state.forCategories(listOf("Α", "Β"), "Λοιπά", "2026-10-01", "2026-10-31")
        assertEquals(listOf("real"), scope.visibleItems.map { it.id })
        assertEquals(-15.0, scope.visibleItems.single().amount, 0.001)
        assertEquals(15.0, scope.visibleExpense, 0.001)
        assertEquals(listOf("cancelled", "real", "unrelated"), state.items.map { it.id })

        val noCategories = state.forCategories(emptyList(), "Λοιπά", "2026-10-01", "2026-10-31")
        assertTrue(noCategories.visibleItems.isEmpty())
        assertEquals(0.0, noCategories.visibleNet, 0.001)
    }

    @Test
    fun accountFilterIncludesAllCanonicalLedgerLegAccountsAndKeepsExplicitEndpoints() {
        val state = ActivityUiState(items = listOf(
            ActivityItem(
                "leg-only", "20 Οκτ", ActivityKind.TRANSFER, "Μεταφορά", "", 50.0,
                "Μετρητά → Μισθοδοσία", null, rawDate = "2026-10-20",
                legAccountIds = linkedSetOf("cash", "payroll"),
            ),
            ActivityItem(
                "source", "20 Οκτ", ActivityKind.EXPENSE, "Καφές", "", -5.0,
                "Μετρητά", "Έξοδα", rawDate = "2026-10-20", accountId = "cash",
            ),
        ))
        assertEquals(listOf("leg-only", "source"),
            state.copy(accountFilterId = "cash").visibleItems.map { it.id })
        assertEquals(listOf("leg-only"),
            state.copy(accountFilterId = "payroll").visibleItems.map { it.id })
        assertTrue(state.copy(accountFilterId = "credit-card").visibleItems.isEmpty())
    }

    @Test
    fun transferFilter_keepsOnlyTransfers() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.FilterChanged(ActivityFilter.TRANSFER))

        assertTrue(state.visibleItems.isNotEmpty())
        assertTrue(state.visibleItems.all { it.kind == ActivityKind.TRANSFER })
    }

    @Test
    fun search_matchesTitleSubtitleOrCategory() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.QueryChanged("τρόφιμα"))

        assertEquals(listOf("evt-1"), state.visibleItems.map { it.id })
    }

    @Test
    fun explicitSaveEdit_updatesOnlySelectedEvent() {
        val initial = ActivityUiState()
        val updated = reduceActivity(
            initial,
            ActivityAction.SaveEdit("evt-1", note = "Νέα σημείωση", category = "Νέα κατηγορία"),
        )

        val edited = updated.items.first { it.id == "evt-1" }
        assertEquals("Νέα σημείωση", edited.subtitle)
        assertEquals("Νέα κατηγορία", edited.category)
        assertEquals(initial.items.first { it.id == "evt-2" }, updated.items.first { it.id == "evt-2" })
    }

    @Test
    fun accountFilter_keepsDirectAndTransferAccountActivity() {
        val items = listOf(
            ActivityItem(
                id = "direct",
                dateLabel = "Σήμερα",
                kind = ActivityKind.EXPENSE,
                title = "Άμεση",
                subtitle = "",
                amount = -10.0,
                accountLabel = "Κύριος",
                category = null,
                rawDate = "2026-09-07",
                accountId = "main",
            ),
            ActivityItem(
                id = "transfer",
                dateLabel = "Σήμερα",
                kind = ActivityKind.TRANSFER,
                title = "Μεταφορά",
                subtitle = "",
                amount = 20.0,
                accountLabel = "Κύριος → Αποταμίευση",
                category = null,
                rawDate = "2026-09-07",
                fromAccountId = "main",
                toAccountId = "savings",
            ),
            ActivityItem(
                id = "other",
                dateLabel = "Σήμερα",
                kind = ActivityKind.EXPENSE,
                title = "Άλλος",
                subtitle = "",
                amount = -5.0,
                accountLabel = "Μετρητά",
                category = null,
                rawDate = "2026-09-07",
                accountId = "cash",
            ),
        )
        val initial = ActivityUiState(
            items = items,
            accountOptions = listOf(ActivityAccountOption("main", "Κύριος")),
        )

        val filtered = reduceActivity(initial, ActivityAction.AccountFilterChanged("main"))

        assertEquals(listOf("direct", "transfer"), filtered.visibleItems.map { it.id })
    }

    @Test
    fun transferRoute_usesCanonicalAccountOptionsWhenAvailable() {
        val item = ActivityItem(
            id = "transfer",
            dateLabel = "Σήμερα",
            kind = ActivityKind.TRANSFER,
            title = "Μεταφορά",
            subtitle = "Εσωτερική μεταφορά",
            amount = 120.0,
            accountLabel = "Fallback",
            category = null,
            rawDate = "2026-09-09",
            fromAccountId = "main",
            toAccountId = "savings",
        )

        val label = activityTransferRouteLabel(
            item,
            listOf(
                ActivityAccountOption("main", "Κύριος"),
                ActivityAccountOption("savings", "Αποταμίευση"),
            ),
        )

        assertEquals("Από Κύριος → Προς Αποταμίευση", label)
    }

    @Test
    fun transferRoute_fallsBackToRealRenderedAccountLabelsNotLiteralPlaceholders() {
        val original = ActivityItem(
            id = "historical-route", dateLabel = "Σήμερα",
            kind = ActivityKind.TRANSFER, title = "Μεταφορά",
            subtitle = "Η αρχική περιγραφή", amount = 120.0,
            accountLabel = "Μετρητά → Μισθοδοσία", category = null,
            rawDate = "2026-10-20",
            fromAccountId = "archived-cash", toAccountId = "archived-payroll",
        )
        assertEquals(
            "Από Μετρητά → Προς Μισθοδοσία",
            activityTransferRouteLabel(original, emptyList()),
        )
        assertEquals(
            "Από Αποταμίευση → Προς Ταμείο",
            activityTransferRouteLabel(
                original.copy(accountLabel = "  Αποταμίευση  ->  Ταμείο  "),
                emptyList(),
            ),
        )
        // A partially missing canonical account must not replace the real
        // historical route with two fake one-letter account names.
        assertEquals(
            "Από Μετρητά → Προς Μισθοδοσία",
            activityTransferRouteLabel(
                original,
                listOf(ActivityAccountOption("archived-cash", "Μετρητά")),
            ),
        )
        assertEquals(
            "Η αρχική περιγραφή",
            activityTransferRouteLabel(
                original.copy(accountLabel = "Μετρητά →   "), emptyList(),
            ),
        )
    }

    @Test
    fun signedAmount_keepsExplicitPositiveAndNegativeSigns() {
        assertTrue(formatSignedEuro(12.5).startsWith("+"))
        assertTrue(formatSignedEuro(-12.5).startsWith("−"))
    }

    @Test
    fun ledgerFilters_areExactInclusiveAndIndependentlyRemovable() {
        val items = listOf(
            ActivityItem("food-main", "10 Σεπ", ActivityKind.EXPENSE, "Αγορά", "", -10.0, "Κύριος", "Τρόφιμα",
                rawDate = "2026-09-10", accountId = "main", categoryContributions = mapOf("Τρόφιμα" to 10.0)),
            ActivityItem("food-old", "31 Αυγ", ActivityKind.EXPENSE, "Αγορά", "", -8.0, "Κύριος", "Τρόφιμα",
                rawDate = "2026-08-31", accountId = "main", categoryContributions = mapOf("Τρόφιμα" to 8.0)),
            ActivityItem("food-cash", "10 Σεπ", ActivityKind.EXPENSE, "Αγορά", "", -4.0, "Μετρητά", "Τρόφιμα",
                rawDate = "2026-09-10", accountId = "cash", categoryContributions = mapOf("Τρόφιμα" to 4.0)),
            ActivityItem("transport-main", "10 Σεπ", ActivityKind.EXPENSE, "Εισιτήριο", "", -3.0, "Κύριος", "Μεταφορές",
                rawDate = "2026-09-10", accountId = "main", categoryContributions = mapOf("Μεταφορές" to 3.0)),
        )
        val initial = ActivityUiState(
            items = items,
            accountOptions = listOf(ActivityAccountOption("main", "Κύριος"), ActivityAccountOption("cash", "Μετρητά")),
        )

        val filtered = reduceActivity(
            initial,
            ActivityAction.ApplyFilters(
                type = ActivityFilter.EXPENSE,
                accountId = "main",
                category = "Τρόφιμα",
                dateFrom = "2026-09-01",
                dateTo = "2026-09-10",
            ),
        )

        assertEquals(listOf("food-main"), filtered.visibleItems.map { it.id })
        assertEquals(4, filtered.activeFilterCount)

        val withoutDate = reduceActivity(filtered, ActivityAction.RemoveFilter(ActivityFilterField.DATE))
        assertEquals(listOf("food-main", "food-old"), withoutDate.visibleItems.map { it.id })

        val cleared = reduceActivity(filtered.copy(query = "καφ"), ActivityAction.ClearFilters)
        assertEquals("καφ", cleared.query)
        assertEquals(0, cleared.activeFilterCount)
    }

    @Test
    fun canonicalTypeFilter_distinguishesCardPurchaseFromCardPayment() {
        val state = ActivityUiState(
            items = listOf(
                ActivityItem(
                    "purchase", "10 Σεπ", ActivityKind.EXPENSE, "Αγορά", "", -25.0, "Κάρτα", "Τρόφιμα",
                    canonicalKind = "card_purchase", typeLabel = "Αγορά με κάρτα",
                ),
                ActivityItem(
                    "payment", "10 Σεπ", ActivityKind.CARD_PAYMENT, "Εξόφληση", "", -25.0, "Κύριος", "Κάρτες",
                    canonicalKind = "card_payment", typeLabel = "Πληρωμή κάρτας",
                ),
            ),
        )

        val filtered = reduceActivity(
            state,
            ActivityAction.ApplyFilters(
                type = ActivityFilter.ALL,
                accountId = null,
                category = null,
                dateFrom = null,
                dateTo = null,
                typeId = "card_payment",
            ),
        )

        assertEquals(listOf("payment"), filtered.visibleItems.map { it.id })
        assertEquals("Πληρωμή κάρτας", filtered.typeOptions.first { it.id == "card_payment" }.label)
    }

    @Test
    fun search_acceptsGreekDecimalSeparator() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.QueryChanged("63,48"))

        assertEquals(listOf("evt-1"), state.visibleItems.map { it.id })
    }

    @Test
    fun categoryEditing_isLimitedToKindsWithCompatibleTaxonomy() {
        val expense = ActivityItem("expense", "", ActivityKind.EXPENSE, "", "", -1.0, "", null)
        val refund = ActivityItem(
            "refund", "", ActivityKind.INCOME, "", "", 1.0, "", null,
            canonicalKind = "refund", typeLabel = "Επιστροφή",
        )
        val transfer = ActivityItem("transfer", "", ActivityKind.TRANSFER, "", "", 1.0, "", null)
        val split = ActivityItem(
            "split", "", ActivityKind.EXPENSE, "", "", -1.0, "", null,
            canonicalKind = "split", typeLabel = "Σύνθετη αγορά",
        )

        assertTrue(expense.supportsCategoryEdit())
        assertTrue(refund.supportsCategoryEdit())
        assertFalse(transfer.supportsCategoryEdit())
        assertFalse(split.supportsCategoryEdit())
    }

}
