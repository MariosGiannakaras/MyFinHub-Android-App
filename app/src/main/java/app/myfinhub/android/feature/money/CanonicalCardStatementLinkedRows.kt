package app.myfinhub.android.feature.money

import app.myfinhub.android.core.data.CanonicalCreditStatementView

/**
 * Read-only interpretation of the source statement links against THIS card's
 * actual activity, not synthetic title/id fallbacks.
 *
 * Duplicate source references and ambiguous or wrong-kind event matches
 * cannot be opened by ID. Technical financial event IDs are never displayed.
 */
data class CanonicalStatementLinkedRow(
    val kindLabel: String,
    val displayTitle: String,
    val amount: Double?,
    val openActivityId: String?,
    val warning: String? = null,
)

internal fun canonicalStatementLinkedRows(
    statement: CanonicalCreditStatementView,
    cardActivity: List<MoneyCardActivity>,
    fullActivityIdCounts: Map<String, Int>? = null,
): List<CanonicalStatementLinkedRow> {
    val source = listOf(
        "Αγορά" to (statement.purchaseIds to MoneyCardActivityKind.PURCHASE),
        "Πληρωμή" to (statement.paymentIds to MoneyCardActivityKind.PAYMENT),
    ).flatMap { (label, collection) ->
        val (ids, kind) = collection
        ids.map { Triple(label, kind, it) }
    }
    val count = source.groupingBy { it.third }.eachCount()
    val events = cardActivity.groupBy { it.id }
    return source.map { (label, kind, id) ->
        val matches = events[id].orEmpty()
        val valid = count[id] == 1 && matches.size == 1 &&
            matches.singleOrNull()?.kind == kind && id.isNotBlank() &&
            (fullActivityIdCounts == null || fullActivityIdCounts[id] == 1)
        val real = matches.singleOrNull()
        if (valid && real != null) {
            CanonicalStatementLinkedRow(
                kindLabel = label,
                displayTitle = real.title.trim().ifBlank { "Χωρίς περιγραφή" },
                amount = real.amount,
                openActivityId = real.id,
            )
        } else {
            val why = when {
                count[id] != 1 -> "Πολλαπλή σύνδεση στην ίδια κατάσταση"
                matches.size > 1 || (fullActivityIdCounts?.get(id) ?: 0) > 1 ->
                    "Αμφίσημη ιστορική ταυτότητα"
                fullActivityIdCounts != null && fullActivityIdCounts[id] != 1 ->
                    "Η συνδεδεμένη κίνηση δεν εντοπίζεται στο συνολικό ιστορικό"
                matches.size == 1 -> "Ασυμφωνία είδους ιστορικής κίνησης"
                else -> "Η συνδεδεμένη ιστορική κίνηση δεν είναι διαθέσιμη"
            }
            CanonicalStatementLinkedRow(
                kindLabel = label,
                displayTitle = "Απαιτείται έλεγχος σύνδεσης",
                amount = null, openActivityId = null, warning = why,
            )
        }
    }
}
