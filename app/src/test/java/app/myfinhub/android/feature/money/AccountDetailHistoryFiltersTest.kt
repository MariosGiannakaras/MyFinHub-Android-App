package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDetailHistoryFiltersTest {
    private val records = listOf(
        ActivityItem(
            id = "groceries", dateLabel = "1/10", kind = ActivityKind.EXPENSE,
            title = "Ιστορικό", subtitle = "Καθημερινό", amount = -20.0,
            accountLabel = "Μισθοδοσία", category = "Τρόφιμα", rawDate = "2026-10-01",
            canonicalSourceOrdinal = 0,
        ),
        ActivityItem(
            id = "income", dateLabel = "2/10", kind = ActivityKind.INCOME,
            title = "Μισθός", subtitle = "Μισθοδοσία", amount = 800.0,
            accountLabel = "Μισθοδοσία", category = "Εργασία", rawDate = "2026-10-02",
            canonicalSourceOrdinal = 1,
        ),
        ActivityItem(
            id = "transfer", dateLabel = "3/10", kind = ActivityKind.TRANSFER,
            title = "Μεταφορά", subtitle = "Εντολή μεταφοράς", amount = -40.0,
            accountLabel = "Μισθοδοσία", category = "Μεταφορά", rawDate = "2026-10-03",
            canonicalSourceOrdinal = 2,
        ),
        ActivityItem(
            id = "credit", dateLabel = "4/10", kind = ActivityKind.CARD_PAYMENT,
            title = "Πληρωμή", subtitle = "Πληρωμή πιστωτικής", amount = -60.0,
            accountLabel = "Μισθοδοσία", category = "Πιστωτική", rawDate = "2026-10-04",
            canonicalSourceOrdinal = 3,
        ),
    )

    @Test fun sourceOrderAndStableIdsRemainUnmodified() {
        assertEquals(records.map { it.id }, visibleAccountHistoryItems(records, "", null).map { it.id })
        assertEquals(records.map { it.id }, records.map { it.id })
    }

    @Test fun kindFilterComposesWithCanonicalLedgerSearch() {
        assertEquals(listOf("groceries"), visibleAccountHistoryItems(records, "Τρόφιμα", ActivityKind.EXPENSE).map { it.id })
        assertEquals(emptyList<String>(), visibleAccountHistoryItems(records, "Τρόφιμα", ActivityKind.INCOME).map { it.id })
        assertEquals(listOf("credit"), visibleAccountHistoryItems(records, "", ActivityKind.CARD_PAYMENT).map { it.id })
        assertEquals(listOf("transfer"), visibleAccountHistoryItems(records, "μεταφοράς", ActivityKind.TRANSFER).map { it.id })
    }

    @Test fun canonicalSearchDoesNotLeakViaAmountsAccountNamesOrDerivedTitles() {
        assertEquals(emptyList<String>(), visibleAccountHistoryItems(records, "800", null).map { it.id })
        assertEquals(emptyList<String>(), visibleAccountHistoryItems(records, "Ιστορικό", null).map { it.id })
        assertEquals(listOf("income"), visibleAccountHistoryItems(records, "Μισθοδοσία", null).map { it.id })
        assertEquals(listOf("income", "transfer"), visibleAccountHistoryItems(records, "", null)
            .filter { it.id == "income" || it.id == "transfer" }.map { it.id })
    }
}
