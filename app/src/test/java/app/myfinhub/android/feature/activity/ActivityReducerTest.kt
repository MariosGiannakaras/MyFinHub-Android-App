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
        // Clearing the filter chips alone remains available to callers and
        // intentionally leaves the search query unchanged.
        assertEquals("Καφές", reduceActivity(filtered, ActivityAction.ClearFilters).query)
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
        assertEquals("καφ", cleared.query)
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
        assertEquals(listOf("purchase"), analytics.visibleItems.map { it.id })
        assertEquals(25.0, analytics.visibleExpense, 0.001)
        assertEquals(3, analytics.scopeSourceItems.size)
        // Card-specific read/detail routes still retain original identity.
        assertEquals(cardPurchase, state.uniqueItem("purchase"))
    }

    @Test
    fun amountSearchCannotInferPrivateBalancesThroughHiddenLedgerRows() {
        val state = ActivityUiState(items = listOf(
            ActivityItem("private", "10 Σεπ", ActivityKind.EXPENSE,
                "Βιβλίο", "Αγορά βιβλίου", -63.48, "Μετρητά", "Εκπαίδευση",
                rawDate = "2026-09-10", canonicalSourceOrdinal = 0),
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

}
