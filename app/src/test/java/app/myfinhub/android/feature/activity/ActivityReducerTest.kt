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
    fun splitDetailsRetainCanonicallyNamedPartsAndWebCategoryPaths() {
        val parts = listOf(
            ActivitySplitPart("Πρωινός καφές", "Τρόφιμα", "Καφές", 4.80),
            ActivitySplitPart("  ", "Μεταφορές", null, 15.20),
            ActivitySplitPart("", "Άλλο", "Διάφορα", 2.0),
        )
        assertEquals(listOf("Πρωινός καφές", "Μεταφορές", "Άλλο › Διάφορα"),
            parts.map { it.displayLabel })
        assertEquals(listOf("Τρόφιμα › Καφές", "Μεταφορές", "Άλλο › Διάφορα"),
            parts.map { it.categoryPath })
        val state = ActivityUiState(items = listOf(
            ActivityItem("split", "21 Οκτ", ActivityKind.EXPENSE, "Διαχωρισμός",
                "", -22.0, "Μετρητά", "Διαχωρισμός",
                canonicalKind = "split", splitParts = parts),
        ))
        assertEquals(parts, state.visibleItems.single().splitParts)
        assertTrue(state.copy(ledgerCategoryFilter = "Επιμέρους κατηγορίες").visibleItems.single().splitParts == parts)
    }

    @Test
    fun onlyCanonicalTransfersUseInternalTransferRouteSemantics() {
        fun item(type: String) = ActivityItem(
            "row-$type", "21 Οκτ", ActivityKind.TRANSFER, type,
            "Ενέργεια", 25.0, "Μετρητά", null, canonicalKind = type)
        assertTrue(item("transfer").isInternalTransfer())
        listOf("withdrawal", "reconciliation", "saving_cash_offset").forEach {
            assertFalse(item(it).isInternalTransfer())
        }
        assertFalse(ActivityItem("payment", "", ActivityKind.CARD_PAYMENT,
            "Κάρτα", "", -10.0, "Μισθοδοσία", null).isInternalTransfer())
    }

    @Test
    fun searchIncludesSplitPartLabelsCategoriesAndSubcategories() {
        val split = ActivityItem(
            "split", "21 Οκτ", ActivityKind.EXPENSE, "Σύνθετη κίνηση", "",
            -30.0, "Μετρητά", "Διαχωρισμός", rawDate = "2026-10-21",
            canonicalKind = "split", splitParts = listOf(
                ActivitySplitPart("Πρωινό σνακ", "Τρόφιμα", "Καφές", 8.0),
                ActivitySplitPart("Εισιτήριο", "Μεταφορές", "Λεωφορείο", 22.0),
            ),
        )
        val normal = ActivityItem("normal", "21 Οκτ", ActivityKind.EXPENSE,
            "Βιβλίο", "", -12.0, "Μετρητά", "Εκπαίδευση", rawDate = "2026-10-21")
        val state = ActivityUiState(items = listOf(split, normal))
        listOf("πρωινό", "ΚΑΦΈΣ", "Εισιτήριο", "ΜΕΤΑΦΟΡΈΣ", "λεωφορείο").forEach { value ->
            assertEquals("query=$value", listOf("split"),
                state.copy(query = value).visibleItems.map { it.id })
        }
        assertEquals(listOf("normal"), state.copy(query = "Εκπαίδευση").visibleItems.map { it.id })
        assertTrue(state.copy(query = "Εκδρομή").visibleItems.isEmpty())
        assertEquals(2, state.items.size)
    }

    @Test
    fun webFlowDrivenDisplayRefundExpenseNeutralAndTransferPreservesUnderlyingAmount() {
        fun row(id: String, canonical: String, amount: Double, income: Double, expense: Double) =
            ActivityItem(id, "21 Οκτ", ActivityKind.EXPENSE, id, "", amount,
                "Μετρητά", null, canonicalKind = canonical,
                financeIncomeContribution = income, financeExpenseContribution = expense)
        val refund = row("refund", "expense", -35.0, 0.0, -35.0)
        val spend = row("spend", "expense", -15.0, 0.0, 15.0)
        val neutral = row("neutral", "expense", -20.0, 0.0, 0.0)
        val transfer = row("transfer", "transfer", 40.0, 0.0, 0.0)
        assertEquals(formatSignedEuro(35.0), formatActivityFlowEuro(refund))
        assertEquals(formatSignedEuro(-15.0), formatActivityFlowEuro(spend))
        assertEquals(formatSignedEuro(20.0).removePrefix("+"), formatActivityFlowEuro(neutral))
        assertTrue(formatActivityFlowEuro(transfer).startsWith("↔ "))
        assertEquals(-35.0, refund.amount, 0.001)
        assertEquals(-35.0, refund.financeExpenseContribution!!, 0.001)
        assertEquals(formatSignedEuro(-8.0), formatActivityFlowEuro(
            ActivityItem("fixture", "", ActivityKind.EXPENSE, "", "", -8.0, "", null)))
    }

    @Test
    fun webNotePartsPreserveDescriptionAndMultilineCommentWithoutMutatingRawNote() {
        val original = "  Πρωινός καφές  \\r\\n\\r\\n  Δουλειά  \\n  Πριν το ταξίδι  "
            .replace("\\r", "\r").replace("\\n", "\n")
        val item = ActivityItem(
            "note", "21 Οκτ", ActivityKind.EXPENSE, "Τρόφιμα", original,
            -3.0, "Μετρητά", "Τρόφιμα", note = original,
        )
        val parts = item.webNoteParts()
        assertEquals("Πρωινός καφές", parts.title)
        assertEquals("Δουλειά\\nΠριν το ταξίδι".replace("\\n", "\n"), parts.comment)
        assertEquals(original, item.note)
        val noNote = item.copy(note = "  \\n  ".replace("\\n", "\n"))
        assertEquals("Χωρίς περιγραφή", noNote.webNoteParts().title)
        assertEquals("", noNote.webNoteParts().comment)
    }

    @Test
    fun historicalOverrideLabelsAndRealSplitPartsNeverAlterLedgerAmountsOrSelection() {
        val historical = ActivityItem(
            "history", "21 Οκτ", ActivityKind.EXPENSE, "Καφές", "",
            -5.0, "Μετρητά", "Τρόφιμα",
            isLegacyTransaction = true, isLegacyOverridden = true,
        )
        assertEquals("Ιστορικό · override", historical.legacyHistoryLabel())
        assertEquals("Ιστορικό", historical.copy(isLegacyOverridden = false).legacyHistoryLabel())
        assertEquals(null, historical.copy(isLegacyTransaction = false).legacyHistoryLabel())
        val split = historical.copy(
            id = "split", canonicalKind = "split", isLegacyTransaction = false,
            splitParts = listOf(
                ActivitySplitPart("Καφές", "Τρόφιμα", null, 3.0),
                ActivitySplitPart("Εισιτήριο", "Μεταφορές", null, 2.0),
            ),
        )
        val state = ActivityUiState(items = listOf(historical, split))
        assertEquals(listOf("history", "split"), state.visibleItems.map { it.id })
        assertEquals(listOf(-5.0, -5.0), state.visibleItems.map { it.amount })
        assertEquals(2, split.splitParts.size)
    }

    @Test
    fun uniqueMutationTargetFailsClosedForRepeatedAndMissingIdsAcrossStateAndReducer() {
        val duplicate = ActivityItem("same", "21 Οκτ", ActivityKind.EXPENSE,
            "Διπλό", "", -4.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-21")
        val safe = duplicate.copy(id = "unique", title = "Μοναδικό")
        val state = ActivityUiState(items = listOf(duplicate, duplicate.copy(amount = -9.0), safe))
        assertEquals(setOf("same"), state.ambiguousIds)
        assertFalse(state.isUniqueMutationTarget("same"))
        assertFalse(state.isUniqueMutationTarget("missing"))
        assertFalse(state.isUniqueMutationTarget(""))
        assertTrue(state.isUniqueMutationTarget("unique"))
        assertEquals(null, state.uniqueItem("same"))
        assertEquals(null, state.uniqueItem("missing"))
        assertEquals(null, state.uniqueItem(null))
        assertEquals(safe, state.uniqueItem("unique"))
        assertEquals(state, reduceActivity(state, ActivityAction.SaveEdit("same", "x", "y")))
        assertEquals(state, reduceActivity(state, ActivityAction.Delete("same")))
        assertEquals(state, reduceActivity(state, ActivityAction.Delete("missing")))
        assertEquals(1, reduceActivity(state, ActivityAction.Delete("unique")).ambiguousIds.size)
    }

    @Test
    fun clearAllSearchAndFiltersMatchesWebResetWithoutDiscardingOriginalItemsOrSort() {
        val source = ActivityUiState(items = listOf(
            ActivityItem("a", "21 Οκτ", ActivityKind.EXPENSE, "Καφές", "", -10.0,
                "Μετρητά", "Τρόφιμα", rawDate = "2026-10-21",
                financeIncomeContribution = 0.0, financeExpenseContribution = 10.0),
            ActivityItem("b", "20 Οκτ", ActivityKind.INCOME, "Μισθός", "", 120.0,
                "Μισθοδοσία", "Εργασία", rawDate = "2026-10-20",
                financeIncomeContribution = 120.0, financeExpenseContribution = 0.0),
        ), sortOldestFirst = true)
        val filtered = source.copy(
            query = "Καφές", filter = ActivityFilter.EXPENSE,
            ledgerCategoryFilter = "Τρόφιμα", ledgerDateFrom = "2026-10-21",
            ledgerDateTo = "2026-10-21", accountFilterId = "cash",
        )
        assertTrue(filtered.hasActiveListScope)
        assertTrue(filtered.visibleItems.isEmpty())
        val reset = reduceActivity(filtered, ActivityAction.ClearSearchAndFilters)
        assertEquals("", reset.query)
        assertEquals(0, reset.activeFilterCount)
        assertFalse(reset.hasActiveListScope)
        assertEquals(listOf("b", "a"), reset.visibleItems.map { it.id })
        assertEquals(source.items, reset.items)
        assertTrue(reset.sortOldestFirst)
        assertEquals(120.0, reset.visibleIncome, 0.001)
        assertEquals(10.0, reset.visibleExpense, 0.001)
        assertEquals(110.0, reset.visibleNet, 0.001)
        // Web resetFilters always clears search, even when the user only opened
        // the filter sheet. The native all-history exit is a separate action.
        assertEquals("", reduceActivity(filtered, ActivityAction.ClearFilters).query)
    }

    @Test
    fun longLedgerOldestFirstUsesWebDateAndIdTieBreakWithoutMutatingCanonicalOrder() {
        val history = listOf(
            ActivityItem("z", "21 Οκτ", ActivityKind.EXPENSE, "Newest", "", -9.0, "Cash", null,
                rawDate = "2026-10-21"),
            ActivityItem("b", "20 Οκτ", ActivityKind.EXPENSE, "Middle B", "", -3.0, "Cash", null,
                rawDate = "2026-10-20"),
            ActivityItem("a", "20 Οκτ", ActivityKind.EXPENSE, "Middle A", "", -4.0, "Cash", null,
                rawDate = "2026-10-20"),
            ActivityItem("old", "19 Οκτ", ActivityKind.EXPENSE, "Oldest", "", -2.0, "Cash", null,
                rawDate = "2026-10-19"),
        )
        val original = ActivityUiState(items = history)
        assertEquals(listOf("z", "b", "a", "old"), original.visibleItems.map { it.id })
        val ascending = reduceActivity(original, ActivityAction.ToggleSortDirection)
        assertTrue(ascending.sortOldestFirst)
        assertEquals(listOf("old", "a", "b", "z"), ascending.visibleItems.map { it.id })
        assertEquals(history, ascending.items)
        assertEquals(listOf("2026-10-19", "2026-10-20", "2026-10-21"),
            ascending.visibleSections.map { it.date })
        assertEquals(listOf("z", "b", "a", "old"),
            reduceActivity(ascending, ActivityAction.ToggleSortDirection).visibleItems.map { it.id })
        assertEquals("old", reduceActivity(ascending, ActivityAction.QueryChanged("Oldest"))
            .visibleItems.single().id)
        val drilldown = ascending.forCategory("Food", "2026-10-01", "2026-10-31")
        assertFalse(drilldown.sortOldestFirst)
        assertEquals(history, original.items)
    }

    @Test
    fun collisionsCannotBulkEditDeleteOrSelectTwoImportedTransactionsSharingSameId() {
        val duplicates = listOf(
            ActivityItem("dup", "21 Οκτ", ActivityKind.EXPENSE, "Original", "", -4.0,
                "Cash", "Food", rawDate = "2026-10-21", isLegacyTransaction = true),
            ActivityItem("dup", "21 Οκτ", ActivityKind.EXPENSE, "Other record", "", -7.0,
                "Cash", "Food", rawDate = "2026-10-21", isLegacyTransaction = true),
            ActivityItem("unique", "20 Οκτ", ActivityKind.EXPENSE, "Unique", "", -6.0,
                "Cash", "Food", rawDate = "2026-10-20"),
        )
        val state = ActivityUiState(items = duplicates, selectedId = "dup")
        assertEquals(setOf("dup"), state.ambiguousIds)
        assertEquals(null, state.selectedItem)
        assertEquals(null, reduceActivity(state, ActivityAction.Select("dup")).selectedId)
        assertEquals(state, reduceActivity(state, ActivityAction.SaveEdit("dup", "changed", "New")))
        assertEquals(state, reduceActivity(state, ActivityAction.Delete("dup")))
        assertEquals(17.0, state.visibleExpense, 0.001)
        val safe = reduceActivity(state, ActivityAction.Select("unique"))
        assertEquals("unique", safe.selectedItem?.id)
        assertEquals(listOf("dup", "dup"),
            reduceActivity(safe, ActivityAction.Delete("unique")).items.map { it.id })
        assertEquals(setOf("dup"), reduceActivity(state,
            ActivityAction.ToggleSortDirection).ambiguousIds)
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
    fun canonicalLedgerAccountFilterDoesNotTrustStaleEventEndpointHints() {
        val source = ActivityItem(
            "real", "21 Οκτ", ActivityKind.TRANSFER, "Μεταφορά", "",
            12.0, "Εμφάνιση", null, rawDate = "2026-10-21",
            accountId = "savings", fromAccountId = "savings",
            toAccountId = "savings", legAccountIds = linkedSetOf("cash", "payroll"),
            canonicalSourceOrdinal = 0,
        )
        val missingLegs = source.copy(
            id = "no-legs", legAccountIds = emptySet(), canonicalSourceOrdinal = 1,
        )
        val imported = source.copy(
            id = "legacy", isLegacyTransaction = true, legAccountIds = emptySet(),
            canonicalSourceOrdinal = 2,
        )
        val preview = source.copy(
            id = "preview", canonicalSourceOrdinal = -1, legAccountIds = emptySet(),
        )
        val state = ActivityUiState(items = listOf(source, missingLegs, imported, preview))
        assertEquals(listOf("real"),
            state.copy(accountFilterId = "cash").visibleItems.map { it.id })
        assertEquals(listOf("real"),
            state.copy(accountFilterId = "payroll").visibleItems.map { it.id })
        assertEquals(listOf("legacy", "preview"),
            state.copy(accountFilterId = "savings").visibleItems.map { it.id })
        assertEquals(listOf("real", "no-legs", "legacy", "preview"),
            state.visibleItems.map { it.id })
    }


    @Test
    fun webMonthlyTrendUsesAbsPreviousAndNearZeroThreshold() {
        assertEquals(null, activityMonthPercentChange(100.0, 0.0))
        assertEquals(null, activityMonthPercentChange(100.0, -0.005))
        assertEquals(50, activityMonthPercentChange(150.0, 100.0))
        assertEquals(-50, activityMonthPercentChange(50.0, 100.0))
        assertEquals(200, activityMonthPercentChange(100.0, -100.0))
        assertEquals(0, activityMonthPercentChange(100.0, 100.0))
    }


    @Test
    fun monthSummaryDrillDownLimitsOnlyLedgerAndRestoresAllHistory() {
        val overview = ActivityMonthlyOverview(
            month = "2026-10", income = 100.0, expense = 40.0, net = 60.0,
            transactionCount = 2, previousIncome = 10.0, previousExpense = 5.0,
            previousNet = 5.0, previousTransactionCount = 1,
        )
        fun item(id: String, day: String, kind: String = "expense") = ActivityItem(
            id = id, dateLabel = day, kind = ActivityKind.EXPENSE,
            title = id, subtitle = id, amount = -1.0, accountLabel = "Μετρητά",
            category = "Άλλο", rawDate = day, canonicalKind = kind,
            canonicalSourceOrdinal = 0,
        )
        val original = ActivityUiState(
            monthOverview = overview, query = "unmatched",
            accountFilterId = "missing", ledgerCategoryFilter = "Άλλο",
            ledgerDateFrom = "2026-09-10", ledgerDateTo = "2026-09-10",
            sortOldestFirst = true,
            selectedId = "september",
            items = listOf(
                item("november", "2026-11-01"),
                item("october", "2026-10-29"),
                item("october-card", "2026-10-20", "card_purchase"),
                item("october-early", "2026-10-01"),
                item("september", "2026-09-10"),
            ),
        )
        val scoped = reduceActivity(original, ActivityAction.ShowOverviewMonth)
        assertTrue(scoped.showingOverviewMonth)
        assertEquals("2026-10-01", scoped.ledgerDateFrom)
        assertEquals("2026-10-31", scoped.ledgerDateTo)
        assertEquals("", scoped.query)
        assertEquals(null, scoped.accountFilterId)
        assertEquals(null, scoped.ledgerCategoryFilter)
        assertEquals(null, scoped.selectedId)
        assertTrue(scoped.sortOldestFirst)
        assertEquals(listOf("october-early", "october"),
            scoped.visibleItems.map { it.id })
        assertEquals(overview.transactionCount, scoped.visibleItems.size)
        assertEquals(overview, scoped.monthOverview)
        assertEquals(original.items, scoped.items)
        val reset = reduceActivity(scoped, ActivityAction.ClearSearchAndFilters)
        assertFalse(reset.showingOverviewMonth)
        assertEquals(listOf("september", "october-early", "october", "november"),
            reset.visibleItems.map { it.id })
        assertEquals(overview, reset.monthOverview)
        assertTrue(reset.sortOldestFirst)
        assertEquals(original.items, reset.items)
    }

    @Test
    fun monthDrillDownFailsClosedOutsideMainLedgerOrWithoutValidMonth() {
        val source = ActivityUiState(
            items = listOf(ActivityItem(
                "r", "10 Οκτ", ActivityKind.EXPENSE, "Αγορά", "", -1.0,
                "Μετρητά", "Άλλο", rawDate = "2026-10-10",
            )),
        )
        assertEquals(source, reduceActivity(source, ActivityAction.ShowOverviewMonth))
        val invalid = source.copy(monthOverview = ActivityMonthlyOverview(
            "2026-99", 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0,
        ))
        assertEquals(invalid, reduceActivity(invalid, ActivityAction.ShowOverviewMonth))
        assertFalse(invalid.showingOverviewMonth)
        val valid = source.copy(monthOverview = invalid.monthOverview!!.copy(month = "2026-10"))
        val analytics = valid.forCategory("Άλλο", "2026-10-01", "2026-10-31")
        assertTrue(analytics.isAnalyticsScope)
        assertEquals(analytics, reduceActivity(analytics, ActivityAction.ShowOverviewMonth))
        assertFalse(analytics.showingOverviewMonth)
    }


    @Test
    fun monthOnlyFilterChoicesMirrorWebMonthSourceButRetainPersistedTaxonomy() {
        val overview = ActivityMonthlyOverview(
            "2026-10", 100.0, 20.0, 80.0, 3, 10.0, 10.0, 0.0, 2,
        )
        fun row(id: String, date: String, kind: String, category: String) = ActivityItem(
            id, date, ActivityKind.EXPENSE, id, id, -1.0,
            "Μετρητά", category, rawDate = date, canonicalKind = kind,
            canonicalSourceOrdinal = 0,
        )
        val original = ActivityUiState(
            monthOverview = overview,
            expenseCategories = listOf(ActivityCategoryOption("Πάγια", listOf("Ενέργεια"))),
            items = listOf(
                row("oct-food", "2026-10-10", "expense", "Τρόφιμα"),
                row("oct-refund", "2026-10-12", "refund", "Επιστροφή"),
                row("oct-card", "2026-10-13", "card_purchase", "Κάρτα"),
                row("sept-retired", "2026-09-10", "adjustment", "Ιστορική παλιά"),
                row("sept-income", "2026-09-12", "income", "Έσοδα"),
                row("nov-unknown", "2026-11-02", "transfer", "Μεταφορές"),
            ),
        )
        assertTrue("All-history exposes historical category", "Ιστορική παλιά" in original.availableCategories)
        assertTrue(original.typeOptions.any { it.id == "adjustment" })
        val month = reduceActivity(original, ActivityAction.ShowOverviewMonth)
        assertTrue(month.showingOverviewMonth)
        // Month-scoped selector membership must not reorder canonical source rows.
        assertEquals(listOf("oct-food", "oct-refund"), month.visibleItems.map { it.id })
        assertTrue("Current month's real category missing", "Τρόφιμα" in month.availableCategories)
        assertTrue("Saved taxonomy must survive even without source rows",
            "Πάγια › Ενέργεια" in month.availableCategories)
        assertFalse("Historic retired category leaked into month",
            "Ιστορική παλιά" in month.availableCategories)
        assertFalse("Other month's category leaked into month",
            "Μεταφορές" in month.availableCategories)
        assertEquals(setOf("expense", "refund"), month.typeOptions.map { it.id }.toSet())
        assertFalse(month.typeOptions.any { it.id == "card_purchase" })
        val combined = month.copy(query = "irrelevant", accountFilterId = "cash",
            ledgerCategoryFilter = "Τρόφιμα", typeFilterId = "expense")
        assertTrue("Month selector scope should survive other filters", combined.showingOverviewMonth)
        assertFalse("Historic category leaked after adding filters",
            "Ιστορική παλιά" in combined.availableCategories)
        assertEquals(setOf("expense", "refund"), combined.typeOptions.map { it.id }.toSet())
        val restored = reduceActivity(combined, ActivityAction.ClearSearchAndFilters)
        assertFalse(restored.showingOverviewMonth)
        assertTrue("Full history must still expose retired label",
            "Ιστορική παλιά" in restored.availableCategories)
        assertTrue(restored.typeOptions.any { it.id == "adjustment" })
        assertEquals(original.items, restored.items)
        assertEquals(overview, restored.monthOverview)
    }


    @Test
    fun explicitWebMonthSourceSurvivesCustomDateFiltersAndResetWithoutLeakingOtherMonths() {
        val overview = ActivityMonthlyOverview(
            "2026-10", 200.0, 20.0, 180.0, 3, 50.0, 10.0, 40.0, 1,
        )
        fun row(id: String, date: String, kind: String = "expense") = ActivityItem(
            id, date, ActivityKind.EXPENSE, id, "", -1.0,
            "cash", id, rawDate = date, canonicalKind = kind,
            canonicalSourceOrdinal = 0,
        )
        val state = ActivityUiState(monthOverview = overview, items = listOf(
            row("later", "2026-10-29"), row("other", "2026-11-01"),
            row("earlier", "2026-10-03"), row("prior", "2026-09-20"),
            row("card-only", "2026-10-10", "card_purchase"),
        ))
        val month = reduceActivity(state, ActivityAction.ShowOverviewMonth)
        assertTrue(month.monthSourceSelected)
        assertTrue(month.showingOverviewMonth)
        assertEquals(listOf("later", "earlier"), month.visibleItems.map { it.id })
        val narrower = reduceActivity(month, ActivityAction.ApplyFilters(
            type = ActivityFilter.ALL, accountId = null, category = null,
            dateFrom = "2026-10-10", dateTo = "2026-10-31",
        ))
        assertTrue(narrower.showingOverviewMonth)
        assertEquals(listOf("later"), narrower.visibleItems.map { it.id })
        assertFalse(narrower.typeOptions.any { it.id == "card_purchase" })
        val clearedDate = reduceActivity(narrower, ActivityAction.RemoveFilter(ActivityFilterField.DATE))
        assertEquals("2026-10-01", clearedDate.ledgerDateFrom)
        assertEquals("2026-10-31", clearedDate.ledgerDateTo)
        assertFalse(clearedDate.hasActiveDateFilter)
        assertEquals(0, clearedDate.activeFilterCount)
        assertTrue("Date clearing cannot leave the Web calendar month source",
            clearedDate.showingOverviewMonth)
        assertEquals(listOf("later", "earlier"), clearedDate.visibleItems.map { it.id })
        assertTrue(clearedDate.availableCategories.contains("earlier"))
        assertFalse(clearedDate.availableCategories.contains("prior"))
        val resetWithinMonth = reduceActivity(clearedDate, ActivityAction.ClearFilters)
        assertTrue(resetWithinMonth.showingOverviewMonth)
        assertEquals("2026-10-01", resetWithinMonth.ledgerDateFrom)
        assertEquals("2026-10-31", resetWithinMonth.ledgerDateTo)
        assertEquals(listOf("later", "earlier"), resetWithinMonth.visibleItems.map { it.id })
        val history = reduceActivity(resetWithinMonth, ActivityAction.ClearSearchAndFilters)
        assertFalse(history.monthSourceSelected)
        assertFalse(history.showingOverviewMonth)
        assertEquals(state.webLedgerItems, history.visibleItems)
        assertEquals(state.items, history.items)
    }


    @Test
    fun manualMonthSelectionIsPureAndPreservesAllOriginalSourceRows() {
        val october = ActivityMonthlyOverview(
            "2026-10", 30.0, 10.0, 20.0, 2, 10.0, 5.0, 5.0, 1,
        )
        val september = october.copy(
            month = "2026-09", transactionCount = 1,
        )
        fun row(id: String, date: String) = ActivityItem(
            id, date, ActivityKind.EXPENSE, id, "", -3.0, "Μετρητά",
            null, rawDate = date, canonicalSourceOrdinal = 0,
        )
        val source = ActivityUiState(
            monthOverview = october,
            items = listOf(row("oct", "2026-10-10"), row("sept", "2026-09-10")),
        )
        val selected = source.selectOverviewMonth(september)
        assertEquals("2026-09", selected.monthOverview!!.month)
        assertTrue(selected.overviewMonthManuallySelected)
        assertTrue(selected.monthSourceSelected)
        assertTrue(selected.showingOverviewMonth)
        assertEquals("2026-09-01", selected.ledgerDateFrom)
        assertEquals("2026-09-30", selected.ledgerDateTo)
        assertEquals(listOf("sept"), selected.visibleItems.map { it.id })
        assertEquals(source.items, selected.items)
        val history = reduceActivity(selected, ActivityAction.ClearSearchAndFilters)
        assertFalse(history.monthSourceSelected)
        assertTrue(history.overviewMonthManuallySelected)
        assertEquals("2026-09", history.monthOverview!!.month)
        assertEquals(listOf("oct", "sept"), history.visibleItems.map { it.id })
        assertEquals(source.items, history.items)
        val analytics = selected.forCategory("Άλλο", "2026-09-01", "2026-09-30")
        assertFalse(analytics.monthSourceSelected)
        assertTrue(analytics.isAnalyticsScope)
        assertFalse(analytics.showingOverviewMonth)
        assertEquals(analytics, analytics.selectOverviewMonth(october))
        val malformed = october.copy(month = "invalid")
        assertEquals(source, source.selectOverviewMonth(malformed))
        assertEquals(source, reduceActivity(source, ActivityAction.NavigateOverviewMonth(-1)))
    }


    @Test
    fun webMonthDefaultDateControlsAreNotAnExtraActiveFilter() {
        val overview = ActivityMonthlyOverview(
            "2026-10", 30.0, 10.0, 20.0, 2, 10.0, 10.0, 0.0, 1,
        )
        fun row(id: String, day: String) = ActivityItem(
            id = id, dateLabel = day, kind = ActivityKind.EXPENSE,
            title = id, subtitle = "", amount = -2.0, accountLabel = "cash",
            category = id, rawDate = day, canonicalSourceOrdinal = 0,
        )
        val source = ActivityUiState(
            monthOverview = overview,
            items = listOf(
                row("oct-one", "2026-10-12"),
                row("oct-two", "2026-10-29"),
                row("old", "2026-09-09"),
            ),
        )
        val month = reduceActivity(source, ActivityAction.ShowOverviewMonth)
        assertEquals(0, month.activeFilterCount)
        assertFalse(month.hasActiveDateFilter)
        assertFalse(month.hasActiveListScope)
        assertEquals(2, month.scopeSourceItems.size)
        assertEquals(3, month.webLedgerItems.size)
        // UI-state filtering preserves canonical source order in these fixtures.
        assertEquals(listOf("oct-one", "oct-two"),
            month.visibleItems.map { it.id })
        val filtered = reduceActivity(month, ActivityAction.ApplyFilters(
            type = ActivityFilter.ALL, accountId = null, category = null,
            dateFrom = "2026-10-15", dateTo = "2026-10-31",
        ))
        assertTrue(filtered.hasActiveDateFilter)
        assertTrue(filtered.hasActiveListScope)
        assertEquals(1, filtered.activeFilterCount)
        assertEquals(listOf("oct-two"), filtered.visibleItems.map { it.id })
        assertEquals(2, filtered.scopeSourceItems.size)
        val resetDates = reduceActivity(filtered, ActivityAction.RemoveFilter(ActivityFilterField.DATE))
        assertEquals("2026-10-01", resetDates.ledgerDateFrom)
        assertEquals("2026-10-31", resetDates.ledgerDateTo)
        assertFalse(resetDates.hasActiveDateFilter)
        assertEquals(0, resetDates.activeFilterCount)
        assertFalse(resetDates.hasActiveListScope)
        assertEquals(month.visibleItems, resetDates.visibleItems)
        assertEquals(source.items, resetDates.items)
        val accountFiltered = resetDates.copy(accountFilterId = "cash")
        assertEquals(1, accountFiltered.activeFilterCount)
        assertFalse(accountFiltered.hasActiveDateFilter)
        val allHistory = reduceActivity(resetDates, ActivityAction.ClearSearchAndFilters)
        assertFalse(allHistory.showingOverviewMonth)
        assertEquals(0, allHistory.activeFilterCount)
        assertEquals(3, allHistory.scopeSourceItems.size)
        assertEquals(3, allHistory.visibleItems.size)
    }

    @Test
    fun reportingMonthNavigationResetsDateFilterBaselineNotLedgerRows() {
        val october = ActivityMonthlyOverview(
            "2026-10", 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0,
        )
        val september = october.copy(month = "2026-09")
        val original = ActivityUiState(monthOverview = october)
        val chosen = original.selectOverviewMonth(september)
        assertEquals("2026-09-01", chosen.ledgerDateFrom)
        assertEquals("2026-09-30", chosen.ledgerDateTo)
        assertFalse(chosen.hasActiveDateFilter)
        assertEquals(0, chosen.activeFilterCount)
        val narrower = chosen.copy(
            query = "food", ledgerDateFrom = "2026-09-10",
            ledgerDateTo = "2026-09-20", ledgerCategoryFilter = "Τρόφιμα",
        )
        assertEquals(2, narrower.activeFilterCount)
        assertTrue(narrower.hasActiveListScope)
        val removed = reduceActivity(narrower, ActivityAction.RemoveFilter(ActivityFilterField.DATE))
        assertEquals("2026-09-01", removed.ledgerDateFrom)
        assertEquals("2026-09-30", removed.ledgerDateTo)
        assertEquals(1, removed.activeFilterCount)
        assertTrue(removed.showingOverviewMonth)
        val reset = reduceActivity(removed, ActivityAction.ClearFilters)
        assertEquals(0, reset.activeFilterCount)
        assertFalse(reset.hasActiveListScope) // Web resetFilters also clears search
        assertEquals("2026-09", reset.monthOverview!!.month)
        assertEquals(original.items, reset.items)
    }


    @Test
    fun webFilterResetRestoresSelectedHistoricalMonthEvenWhenNoRowsMatch() {
        val current = ActivityMonthlyOverview(
            "2026-10", 100.0, 25.0, 75.0, 1, 0.0, 0.0, 0.0, 0,
        )
        val september = current.copy(month = "2026-09")
        fun row(id: String, date: String) = ActivityItem(
            id, date, ActivityKind.EXPENSE, id, id,
            -2.0, "cash", "Τρόφιμα", rawDate = date,
            canonicalSourceOrdinal = 0,
        )
        val source = ActivityUiState(
            monthOverview = current,
            items = listOf(row("oct", "2026-10-11"), row("sept", "2026-09-12")),
            sortOldestFirst = true,
        )
        val selected = source.selectOverviewMonth(september)
        val unmatched = selected.copy(
            query = "κανένα-αποτέλεσμα",
            ledgerCategoryFilter = "Τρόφιμα",
            accountFilterId = "cash",
            typeFilterId = "expense",
            ledgerDateFrom = "2026-09-20",
            ledgerDateTo = "2026-09-30",
        )
        assertTrue(unmatched.hasActiveListScope)
        assertTrue(unmatched.visibleItems.isEmpty())
        val reset = reduceActivity(unmatched, ActivityAction.ClearFilters)
        assertEquals("", reset.query)
        assertEquals(0, reset.activeFilterCount)
        assertFalse(reset.hasActiveListScope)
        assertEquals("2026-09-01", reset.ledgerDateFrom)
        assertEquals("2026-09-30", reset.ledgerDateTo)
        assertTrue(reset.showingOverviewMonth)
        assertTrue(reset.overviewMonthManuallySelected)
        assertEquals("2026-09", reset.monthOverview!!.month)
        assertEquals(listOf("sept"), reset.visibleItems.map { it.id })
        assertTrue(reset.sortOldestFirst)
        assertEquals(source.items, reset.items)
        val exited = reduceActivity(reset, ActivityAction.ClearSearchAndFilters)
        assertFalse(exited.showingOverviewMonth)
        assertEquals(setOf("oct", "sept"), exited.visibleItems.map { it.id }.toSet())
        assertEquals("2026-09", exited.monthOverview!!.month)
    }

    @Test
    fun emptyReportingMonthResetCannotInventAnotherMonthSourceRow() {
        val overview = ActivityMonthlyOverview(
            "2026-08", 0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0, 0,
        )
        val source = ActivityUiState(
            monthOverview = overview,
            items = listOf(ActivityItem(
                "sept", "10 Σεπ", ActivityKind.EXPENSE,
                "Παλιά κίνηση", "", -3.0, "cash", "Τρόφιμα",
                rawDate = "2026-09-10", canonicalSourceOrdinal = 0,
            )),
        )
        val month = reduceActivity(source, ActivityAction.ShowOverviewMonth)
        assertTrue(month.scopeSourceItems.isEmpty())
        assertFalse(month.hasActiveListScope)
        val unmatched = month.copy(query = "καφές")
        assertTrue(unmatched.hasActiveListScope)
        assertTrue(unmatched.scopeSourceItems.isEmpty())
        val reset = reduceActivity(unmatched, ActivityAction.ClearFilters)
        assertEquals("", reset.query)
        assertTrue(reset.scopeSourceItems.isEmpty())
        assertTrue(reset.showingOverviewMonth)
        assertFalse(reset.hasActiveListScope)
        assertEquals(source.items, reset.items)
        assertEquals("2026-08-01", reset.ledgerDateFrom)
        assertEquals("2026-08-31", reset.ledgerDateTo)
        val history = reduceActivity(reset, ActivityAction.ClearSearchAndFilters)
        assertEquals(listOf("sept"), history.visibleItems.map { it.id })
    }


    @Test
    fun legacyTransferAndAdjustmentUseExpenseTaxonomyWhileEventsRemainSourceRestricted() {
        val state = ActivityUiState(
            expenseCategories = listOf(ActivityCategoryOption("Έξοδα")),
            incomeCategories = listOf(ActivityCategoryOption("Έσοδα")),
        )
        fun item(id: String, type: String, legacy: Boolean) = ActivityItem(
            id, "17 Σεπ", ActivityKind.TRANSFER, id, "",
            12.0, "cash", null, rawDate = "2026-09-17",
            canonicalKind = type, isLegacyTransaction = legacy,
            canonicalSourceOrdinal = 0,
        )
        assertEquals("Έξοδα", state.categoryOptionsFor(item("transfer", "transfer", true)).single().name)
        assertEquals("Έξοδα", state.categoryOptionsFor(item("adjust", "adjustment", true)).single().name)
        assertEquals("Έσοδα", state.categoryOptionsFor(item("income", "income", true)).single().name)
        assertTrue(state.categoryOptionsFor(item("event", "transfer", false)).isEmpty())
    }


    @Test
    fun persistedWebRowsUseFirstNoteLineRatherThanCategoryOrFallbackTitle() {
        val imported = ActivityItem(
            id = "imported", dateLabel = "21 Οκτ", kind = ActivityKind.EXPENSE,
            title = "Τρόφιμα", subtitle = "ΚΑΦΕΣ", amount = -5.0,
            accountLabel = "Μετρητά", category = "Τρόφιμα", note = "Καφές στο γραφείο",
            rawDate = "2026-10-21", subcategory = "Ροφήματα",
            isLegacyTransaction = true, canonicalSourceOrdinal = 0,
        )
        assertEquals("Καφές στο γραφείο", imported.webLedgerDisplayTitle())
        assertEquals("Τρόφιμα › Ροφήματα", imported.webLedgerCategoryLabel())
        assertEquals("Καφές στο γραφείο", imported.webNoteParts().title)
        assertEquals("", imported.webNoteParts().comment)
        val overridden = imported.copy(
            note = "Πρωινό\nΕπιβεβαιωμένο σχόλιο", isLegacyOverridden = true,
        )
        assertEquals("Πρωινό", overridden.webLedgerDisplayTitle())
        assertEquals("Επιβεβαιωμένο σχόλιο", overridden.webNoteParts().comment)
        assertEquals("Ιστορικό · override", overridden.legacyHistoryLabel())
        val event = imported.copy(
            isLegacyTransaction = false, canonicalSourceOrdinal = 3,
            title = "Μεταφορές", category = "Μεταφορές", note = "Εισιτήριο",
        )
        assertEquals("Εισιτήριο", event.webLedgerDisplayTitle())
        assertEquals(null, event.legacyHistoryLabel())
        assertEquals("Χωρίς περιγραφή", event.copy(note = "").webLedgerDisplayTitle())
        assertEquals("Χωρίς περιγραφή", imported.copy(note = "").webLedgerDisplayTitle())
        // Synthetic UI previews cannot be reinterpreted as persisted transactions.
        val demo = event.copy(
            canonicalSourceOrdinal = -1, title = "Demo title", note = "Demo note",
        )
        assertEquals("Demo title", demo.webLedgerDisplayTitle())
        assertEquals("Πρώτη γραμμή", demo.copy(
            note = "Πρώτη γραμμή\nΔεύτερη", title = "Demo title",
        ).webLedgerDisplayTitle())
        assertEquals("Πρώτη γραμμή", demo.copy(
            note = "  Πρώτη γραμμή\r\nΔεύτερη  ",
        ).webLedgerDisplayTitle())
        assertEquals("Δεύτερη", demo.copy(
            note = "  Πρώτη γραμμή\r\nΔεύτερη  ",
        ).webNoteParts().comment)
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
    fun canonicalTransferDetailPreservesActualWebAccountPathInsteadOfEndpointHints() {
        val seed = ActivityItem(
            "imported", "21 Οκτ", ActivityKind.TRANSFER, "Μεταφορά",
            "Σημείωση", 15.0, "Μετρητά → Μισθοδοσία → Αποταμίευση", null,
            fromAccountId = "payroll", toAccountId = "savings",
            canonicalKind = "transfer", isLegacyTransaction = true,
            canonicalSourceOrdinal = 0,
        )
        val options = listOf(
            ActivityAccountOption("cash", "Μετρητά"),
            ActivityAccountOption("payroll", "Μισθοδοσία"),
            ActivityAccountOption("savings", "Αποταμίευση"),
        )
        assertEquals("Μετρητά → Μισθοδοσία → Αποταμίευση",
            activityTransferRouteLabel(seed, options))
        // Real events may retain stale from/to hints that disagree with legs.
        val canonical = seed.copy(
            id = "event", isLegacyTransaction = false, canonicalSourceOrdinal = 1,
            accountLabel = "Μετρητά → Μισθοδοσία",
        )
        assertEquals("Μετρητά → Μισθοδοσία",
            activityTransferRouteLabel(canonical, options))
        assertEquals("—", activityTransferRouteLabel(
            canonical.copy(accountLabel = "—"), options))
        // The old helpful From → To form remains only for synthetic previews.
        assertEquals("Από Μισθοδοσία → Προς Αποταμίευση",
            activityTransferRouteLabel(
                seed.copy(isLegacyTransaction = false, canonicalSourceOrdinal = -1),
                options,
            ))
    }

    @Test
    fun persistedTransactionDetailCategoryMatchesWebLedgerFilters() {
        val import = ActivityItem(
            "legacy", "21 Οκτ", ActivityKind.EXPENSE, "Ιστορικό", "", -5.0,
            "Μετρητά", null, isLegacyTransaction = true, canonicalSourceOrdinal = 0,
        )
        val split = import.copy(
            id = "split", isLegacyTransaction = false, canonicalKind = "split",
            canonicalSourceOrdinal = 1, category = "Ignored",
        )
        val refund = import.copy(
            id = "refund", isLegacyTransaction = false, canonicalKind = "refund",
            canonicalSourceOrdinal = 2,
        )
        val hierarchy = import.copy(
            id = "hierarchy", category = "Τρόφιμα", subcategory = "Καφές",
            isLegacyTransaction = false, canonicalSourceOrdinal = 3,
        )
        listOf(import, split, refund, hierarchy).forEach { item ->
            assertEquals(item.webLedgerCategoryLabel(), activityDetailCategoryLabel(item))
        }
        assertEquals("Άλλο", activityDetailCategoryLabel(import))
        assertEquals("Επιμέρους κατηγορίες", activityDetailCategoryLabel(split))
        assertEquals("Επιστροφή χρημάτων", activityDetailCategoryLabel(refund))
        assertEquals("Τρόφιμα › Καφές", activityDetailCategoryLabel(hierarchy))
        val preview = import.copy(isLegacyTransaction = false, canonicalSourceOrdinal = -1)
        assertEquals("Χωρίς κατηγορία", activityDetailCategoryLabel(preview))
    }

    @Test
    fun signedAmount_keepsExplicitPositiveAndNegativeSigns() {
        assertTrue(formatSignedEuro(12.5).startsWith("+"))
        assertTrue(formatSignedEuro(-12.5).startsWith("−"))
    }

    @Test
    fun mainLedgerCategoryUsesWebDisplayPathsNotAnalyticsSplitContributions() {
        val state = ActivityUiState(
            expenseCategories = listOf(ActivityCategoryOption("Τρόφιμα", listOf("Καφές", "Γεύμα"))),
            items = listOf(
                ActivityItem("coffee", "20 Οκτ", ActivityKind.EXPENSE, "Καφές", "",
                    -3.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-20",
                    subcategory = "Καφές", categoryContributions = mapOf("Τρόφιμα" to 3.0)),
                ActivityItem("parent", "20 Οκτ", ActivityKind.EXPENSE, "Αγορά", "",
                    -10.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-20",
                    categoryContributions = mapOf("Τρόφιμα" to 10.0)),
                ActivityItem("split", "20 Οκτ", ActivityKind.EXPENSE, "Κατανομή", "",
                    -30.0, "Μετρητά", "Διαχωρισμός", rawDate = "2026-10-20",
                    canonicalKind = "split", categoryContributions = mapOf("Τρόφιμα" to 12.0, "Άλλο" to 18.0)),
                ActivityItem("refund", "20 Οκτ", ActivityKind.INCOME, "Επιστροφή", "",
                    4.0, "Μετρητά", null, rawDate = "2026-10-20",
                    canonicalKind = "refund", categoryContributions = mapOf("Τρόφιμα" to -4.0)),
                ActivityItem("legacy", "20 Οκτ", ActivityKind.EXPENSE, "Εισαγωγή", "",
                    -5.0, "Μετρητά", null, rawDate = "2026-10-20",
                    isLegacyTransaction = true, categoryContributions = emptyMap()),
            ),
        )
        assertTrue("Τρόφιμα › Καφές" in state.availableCategories)
        assertTrue("Τρόφιμα › Γεύμα" in state.availableCategories)
        assertTrue("Επιμέρους κατηγορίες" in state.availableCategories)
        assertTrue("Επιστροφή χρημάτων" in state.availableCategories)
        assertTrue("Άλλο" in state.availableCategories)
        assertFalse("Διαχωρισμός" in state.availableCategories)
        assertEquals(listOf("coffee"),
            state.copy(ledgerCategoryFilter = "Τρόφιμα › Καφές").visibleItems.map { it.id })
        assertEquals(listOf("parent"),
            state.copy(ledgerCategoryFilter = "Τρόφιμα").visibleItems.map { it.id })
        assertEquals(listOf("split"),
            state.copy(ledgerCategoryFilter = "Επιμέρους κατηγορίες").visibleItems.map { it.id })
        assertEquals(listOf("refund"),
            state.copy(ledgerCategoryFilter = "Επιστροφή χρημάτων").visibleItems.map { it.id })
        assertEquals(listOf("legacy"),
            state.copy(ledgerCategoryFilter = "Άλλο").visibleItems.map { it.id })
        // Analytics still drills into the exact part amounts rather than ledger labels.
        val analytics = state.forCategory("Τρόφιμα", "2026-10-01", "2026-10-31")
        assertEquals(listOf("coffee", "parent", "split", "refund"),
            analytics.visibleItems.map { it.id })
    }

    @Test
    fun analyticsNeverUsesDisplayCategoryWhenCanonicalContributionsAreExplicitlyEmpty() {
        val row = ActivityItem(
            "transfer", "20 Οκτ", ActivityKind.TRANSFER, "Μεταφορά", "",
            25.0, "Μετρητά", "Τρόφιμα", rawDate = "2026-10-20",
            categoryContributions = emptyMap(),
        )
        val ledger = ActivityUiState(items = listOf(row))
        assertEquals(listOf("transfer"),
            ledger.copy(ledgerCategoryFilter = "Τρόφιμα").visibleItems.map { it.id })
        assertTrue(ledger.forCategory("Τρόφιμα", "2026-10-01", "2026-10-31")
            .visibleItems.isEmpty())
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
        assertEquals("", cleared.query)
        assertEquals(0, cleared.activeFilterCount)
    }

    @Test
    fun actualCardEventsStayOutOfWebLedgerButRemainInSignedAnalytics() {
        val cardPurchase = ActivityItem("purchase", "10 Σεπ", ActivityKind.EXPENSE,
            "Αγορά", "", -25.0, "Κάρτα", "Τρόφιμα",
            rawDate = "2026-09-10", canonicalKind = "card_purchase",
            canonicalSourceOrdinal = 0, categoryContributions = mapOf("Τρόφιμα" to 25.0),
            financeIncomeContribution = 0.0, financeExpenseContribution = 25.0)
        val cardPayment = ActivityItem("payment", "10 Σεπ", ActivityKind.CARD_PAYMENT,
            "Πληρωμή πιστωτικής", "", -25.0, "Μισθοδοσία", "Κάρτες",
            rawDate = "2026-09-10", canonicalKind = "card_payment",
            canonicalSourceOrdinal = 1, financeIncomeContribution = 0.0,
            financeExpenseContribution = 0.0)
        val ordinary = ActivityItem("cash", "9 Σεπ", ActivityKind.EXPENSE,
            "Καφές", "", -5.0, "Μετρητά", "Τρόφιμα",
            rawDate = "2026-09-09", canonicalSourceOrdinal = 2,
            financeIncomeContribution = 0.0, financeExpenseContribution = 5.0)
        val state = ActivityUiState(items = listOf(cardPayment, cardPurchase, ordinary))
        assertEquals(3, state.items.size)
        assertEquals(listOf("cash"), state.webLedgerItems.map { it.id })
        assertEquals(listOf("cash"), state.visibleItems.map { it.id })
        assertEquals(5.0, state.visibleExpense, 0.001)
        assertEquals(1, state.scopeSourceItems.size)
        assertFalse(state.typeOptions.any { it.id in setOf("card_purchase", "card_payment") })
        assertFalse(state.availableCategories.contains("Κάρτες"))
        assertTrue(state.copy(query = "πιστωτικής").visibleItems.isEmpty())
        assertTrue(state.copy(typeFilterId = "card_payment").visibleItems.isEmpty())
        val analytics = state.forCategory("Τρόφιμα", "2026-09-01", "2026-09-30")
        assertEquals(listOf("purchase", "cash"), analytics.visibleItems.map { it.id })
        assertEquals(30.0, analytics.visibleExpense, 0.001)
        assertEquals(25.0, analytics.visibleItems.first().financeExpenseContribution!!, 0.001)
        assertEquals(3, analytics.scopeSourceItems.size)
        // Card-specific read/detail routes still retain original identity.
        assertEquals(cardPurchase, state.uniqueItem("purchase"))
    }

    @Test
    fun amountSearchCannotInferPrivateBalancesThroughHiddenLedgerRows() {
        val state = ActivityUiState(items = listOf(
            ActivityItem("private", "10 Σεπ", ActivityKind.EXPENSE,
                "Βιβλίο", "Αγορά βιβλίου", -63.48, "Μετρητά", "Εκπαίδευση",
                rawDate = "2026-09-10", note = "Αγορά βιβλίου",
                canonicalSourceOrdinal = 0),
        ))
        assertTrue(state.copy(query = "63,48").visibleItems.isEmpty())
        assertTrue(state.copy(query = "63.48").visibleItems.isEmpty())
        assertEquals(listOf("private"), state.copy(query = "βιβλίου").visibleItems.map { it.id })
        assertEquals(listOf("private"), state.copy(query = "Εκπαίδευση").visibleItems.map { it.id })
        assertEquals(-63.48, state.items.single().amount, 0.001)
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
    fun realCanonicalSearchMatchesOnlyWebIndexedFieldsAndNeverAccountDateOrDisplayFallback() {
        val row = ActivityItem(
            "real", "21 Οκτ", ActivityKind.EXPENSE,
            "Αποκλειστικός τίτλος προβολής", "Υπότιτλος παρουσίας",
            -63.48, "Μυστικός λογαριασμός", "Τρόφιμα",
            rawDate = "2026-10-21", note = "Καφές συνάντησης",
            subcategory = "Ροφήματα", canonicalKind = "expense",
            typeLabel = "Προβολή εξόδων", canonicalSourceOrdinal = 0,
        )
        val split = row.copy(id = "split", canonicalKind = "split",
            category = "Διαχωρισμός", subcategory = null, note = "Σύνθετη",
            splitParts = listOf(ActivitySplitPart("Λεωφορείο", "Μεταφορές", "Εισιτήρια", 8.0)),
            canonicalSourceOrdinal = 1)
        val state = ActivityUiState(items = listOf(row, split))
        listOf("Καφές", "ΣΥΝΆΝΤΗΣΗΣ", "Τρόφιμα", "Ροφήματα", "expense").forEach { q ->
            assertEquals("query=$q", listOf("real"), state.copy(query = q).visibleItems.map { it.id })
        }
        listOf("λεωφορείο", "Μεταφορές", "Εισιτήρια").forEach { q ->
            assertEquals("query=$q", listOf("split"), state.copy(query = q).visibleItems.map { it.id })
        }
        listOf("Μυστικός", "2026-10-21", "21 Οκτ", "Αποκλειστικός",
            "Υπότιτλος", "Προβολή εξόδων", "Έξοδα", "ΣΥΝΑΝΤΗΣΗΣ", "63.48", "63,48",
        ).forEach { q ->
            assertTrue("Not indexed on Web: $q", state.copy(query = q).visibleItems.isEmpty())
        }
        assertEquals(listOf("real", "split"), state.visibleItems.map { it.id })
        assertEquals(-63.48, row.amount, 0.001)
        // Illustrative previews are intentionally distinct from canonical rows.
        assertEquals(listOf("real"), state.copy(items = listOf(row.copy(
            canonicalSourceOrdinal = -1)), query = "Μυστικός").visibleItems.map { it.id })
    }

    @Test
    fun greekWebLocaleOrderSortsMainLedgerCategoriesAndTypeLabels() {
        val types = listOf("Ίριδα", "Ζήτα", "Βήτα", "Άλφα")
        val records = types.mapIndexed { index, name ->
            ActivityItem(
                "event-$index", "21 Οκτ", ActivityKind.EXPENSE, name, "",
                -1.0, "Μετρητά", name,
                canonicalKind = "test-$index", typeLabel = name,
                canonicalSourceOrdinal = index,
            )
        }
        val state = ActivityUiState(items = records)
        assertEquals(listOf("Άλφα", "Βήτα", "Ζήτα", "Ίριδα"),
            state.availableCategories)
        assertEquals(listOf("Άλφα", "Βήτα", "Ζήτα", "Ίριδα"),
            state.typeOptions.map { it.label })
        assertEquals(listOf("event-0", "event-1", "event-2", "event-3"),
            state.visibleItems.map { it.id })
        assertEquals(listOf("event-1"), state.copy(
            ledgerCategoryFilter = "Ζήτα").visibleItems.map { it.id })
        assertEquals(listOf("event-2"), state.copy(
            typeFilterId = "test-2").visibleItems.map { it.id })
    }

    @Test
    fun searchDoesNotExposeAmountsAbsentFromWebTextIndex() {
        val state = reduceActivity(ActivityUiState(), ActivityAction.QueryChanged("63,48"))
        // The preview includes a 63.48 transaction, but Web indexes only the
        // note, kind and taxonomy, not potentially hidden monetary values.
        assertTrue(state.visibleItems.isEmpty())
        assertEquals(listOf("evt-1"),
            reduceActivity(ActivityUiState(), ActivityAction.QueryChanged("τρόφιμα"))
                .visibleItems.map { it.id })
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

    @Test
    fun webSearchAllowsJoinedGreekSourceFieldsAndFallbackCategoryLabels() {
        val base = ActivityItem(
            "real", "21 Οκτ", ActivityKind.EXPENSE, "Display only", "Visible subtitle",
            -24.0, "Private bank", "Τρόφιμα", note = "Αγορά", subcategory = "Λαχανικά",
            canonicalKind = "expense", canonicalSourceOrdinal = 0,
        )
        val split = base.copy(
            id = "split", canonicalKind = "split", note = "Κίνηση",
            category = "Ignored", subcategory = null, canonicalSourceOrdinal = 1,
            splitParts = listOf(
                ActivitySplitPart("Μαύρος", "Καφές", "Πρωινός", 2.0),
                ActivitySplitPart("Ψωμί", "Αρτοποιείο", null, 3.0),
            ),
        )
        val legacy = base.copy(
            id = "legacy", isLegacyTransaction = true, category = null,
            subcategory = null, note = "Παλιό", canonicalSourceOrdinal = 2,
        )
        val fallback = base.copy(
            id = "event", note = "Νέο", category = null, subcategory = null,
            canonicalKind = "refund", canonicalSourceOrdinal = 3,
        )
        val state = ActivityUiState(items = listOf(base, split, legacy, fallback))
        assertEquals(listOf("real"), state.copy(query = "Αγορά Τρόφιμα").visibleItems.map { it.id })
        assertEquals(listOf("real"), state.copy(query = "Τρόφιμα Λαχανικά expense").visibleItems.map { it.id })
        assertEquals(listOf("split"), state.copy(query = "split Μαύρος Καφές").visibleItems.map { it.id })
        assertEquals(listOf("split"), state.copy(query = "Πρωινός Ψωμί").visibleItems.map { it.id })
        assertEquals(listOf("split"), state.copy(query = "Κίνηση Διαχωρισμός").visibleItems.map { it.id })
        assertEquals(listOf("legacy"), state.copy(query = "Παλιό Άλλο").visibleItems.map { it.id })
        assertEquals(listOf("event"), state.copy(query = "Νέο refund").visibleItems.map { it.id })
        assertEquals(listOf("real"), state.copy(query = "ΑΓΟΡΆ").visibleItems.map { it.id })
        assertTrue(state.copy(query = "ΑΓΟΡΑ").visibleItems.isEmpty())
        assertTrue(state.copy(query = "Private bank").visibleItems.isEmpty())
        assertTrue(state.copy(query = "24.0").visibleItems.isEmpty())
    }

    @Test
    fun legacyThreadedImportNoteUsesWebPresentationOnly() {
        val original = "[Threaded comment] Export metadata\nComment:\nΑγορά\nReply:\nΠρωινό\n"
        assertEquals("Αγορά\nΠρωινό", cleanLegacyActivityNote(original))
        assertEquals("Αγορά\nΠρωινό",
            cleanLegacyActivityNote("Export header\nComment:\nΑγορά\nReply:\nΠρωινό\n"))
        assertEquals("Ορατό σχόλιο", cleanLegacyActivityNote(
            "[Threaded comment] Export metadata\nComment:\nΟρατό σχόλιο"))
        assertEquals("Απλή σημείωση", cleanLegacyActivityNote("  Απλή σημείωση  "))
        assertTrue(original.startsWith("[Threaded comment]"))
    }

    @Test
    fun webEventKindLabelsMatchAllLedgerTypeSemantics() {
        val cases = mapOf(
            "expense" to "Έξοδο",
            "income" to "Έσοδο",
            "refund" to "Επιστροφή χρημάτων",
            "lending" to "Δανεικά προς άλλον",
            "repayment" to "Επιστροφή δανεικών",
            "card_purchase" to "Αγορά με πιστωτική",
            "card_payment" to "Εξόφληση πιστωτικής",
            "reconciliation" to "Διόρθωση υπολοίπου",
            "split" to "Σύνθετη κίνηση",
            "custom_kind" to "custom_kind",
        )
        cases.forEach { (kind, label) ->
            assertEquals("kind=$kind", label, activityTypeLabel(kind))
            val row = ActivityItem("row", "", ActivityKind.EXPENSE, "Τίτλος", "",
                5.0, "Μετρητά", null, canonicalKind = kind, canonicalSourceOrdinal = 0)
            val state = ActivityUiState(items = listOf(row))
            assertEquals(label, state.items.single().typeLabel)
            if (kind == "card_purchase" || kind == "card_payment") {
                // Web TransactionsPage excludes card-only events even from its
                // type options, while the retained canonical/card detail label
                // must continue to match the Web kind identity.
                assertTrue("Card-only kind leaked into main ledger: $kind",
                    state.webLedgerItems.isEmpty())
                assertTrue(state.typeOptions.isEmpty())
            } else {
                assertEquals(label, state.typeOptions.single().label)
            }
        }
    }

}
