package app.myfinhub.android.feature.money

/**
 * An imported event ID is a navigation key, not a display fallback.
 * Duplicate or blank keys can resolve to a different physical finance event.
 * Keep source rows visible but fail closed on opening an ambiguous identity.
 */
internal fun canonicalCardActivityIdCounts(
    activities: List<MoneyCardActivity>,
): Map<String, Int> = activities.groupingBy(MoneyCardActivity::id).eachCount()

internal fun canonicalCardActivityOpenId(
    id: String,
    idCounts: Map<String, Int>,
): String? = id.takeIf { it.isNotBlank() && idCounts[it] == 1 }

internal fun canonicalCardActivityIdentityWarning(
    id: String,
    idCounts: Map<String, Int>,
): String? = when {
    id.isBlank() -> "Η κίνηση δεν διαθέτει έγκυρη ταυτότητα."
    idCounts[id] != 1 -> "Διπλή ταυτότητα ιστορικής κίνησης · απαιτείται έλεγχος."
    else -> null
}
