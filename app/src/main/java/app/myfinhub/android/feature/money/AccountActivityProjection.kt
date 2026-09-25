package app.myfinhub.android.feature.money

import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import kotlin.math.abs

internal fun accountActivityItems(
    accountId: String,
    items: List<ActivityItem>,
): List<ActivityItem> {
    val id = accountId.trim()
    if (id.isBlank()) return emptyList()
    return items.mapNotNull { item ->
        val affectsAccount = item.accountId == id || item.fromAccountId == id || item.toAccountId == id
        if (!affectsAccount) return@mapNotNull null
        if (item.kind != ActivityKind.TRANSFER) return@mapNotNull item

        val directionalAmount = when (id) {
            item.fromAccountId -> -abs(item.amount)
            item.toAccountId -> abs(item.amount)
            else -> item.amount
        }
        item.copy(amount = directionalAmount)
    }
}
