package app.myfinhub.android.feature.money

/**
 * Resolve a card route against the full canonical ID namespace.
 * Never use firstOrNull(id) here: after a finance refresh, duplicates could
 * silently swap details, repayments or a revealed card vault to another row.
 */
fun MoneyUiState.uniqueActiveCardForRoute(cardId: String): MoneyCard? {
    if (cardId.isBlank() || cardId != cardId.trim()) return null
    val card = cards.singleOrNull { it.id == cardId } ?: return null
    if (archivedCards.any { it.id == cardId }) return null
    return card.takeIf { it.archivedAt == null }
}

internal fun canOpenDebitCardQuickEntry(card: MoneyCard?): Boolean =
    card?.canonicalKind == "debit"
