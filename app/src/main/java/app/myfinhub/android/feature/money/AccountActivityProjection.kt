package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem

internal fun accountActivityItems(
    accountId: String,
    items: List<ActivityItem>,
): List<ActivityItem> {
    val id = accountId.trim()
    if (id.isBlank()) return emptyList()
    return items.filter { item ->
        item.accountId == id || item.fromAccountId == id || item.toAccountId == id
    }
}
