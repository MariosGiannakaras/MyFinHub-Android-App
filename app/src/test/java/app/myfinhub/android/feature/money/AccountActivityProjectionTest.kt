package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountActivityProjectionTest {
    @Test
    fun filtersDirectFromAndToAccountWithoutChangingCanonicalOrder() {
        val items = listOf(
            ActivityItem("newest", "Σήμερα", ActivityKind.EXPENSE, "A", "n", -10.0, "Main", "Food", rawDate = "2026-09-04", accountId = "acc-main"),
            ActivityItem("transfer", "Σήμερα", ActivityKind.TRANSFER, "B", "n", 20.0, "Main → Save", null, rawDate = "2026-09-04", fromAccountId = "acc-main", toAccountId = "acc-save"),
            ActivityItem("other", "Χθες", ActivityKind.INCOME, "C", "n", 30.0, "Cash", null, rawDate = "2026-09-03", accountId = "acc-cash"),
            ActivityItem("older", "Χθες", ActivityKind.TRANSFER, "D", "n", 40.0, "Cash → Main", null, rawDate = "2026-09-03", fromAccountId = "acc-cash", toAccountId = "acc-main"),
        )

        assertEquals(listOf("newest", "transfer", "older"), accountActivityItems("acc-main", items).map { it.id })
    }
}
