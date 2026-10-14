package app.myfinhub.android.core.data

/** Read-only Web cards.ts withCardProfileDeleted safety rules. Never purges vault or finances here. */
enum class PermanentCardDeletionBlock {
    CARD_NOT_FOUND,
    ACTIVE_CREDIT_CARD,
    CREDIT_OUTSTANDING,
    NON_CREDIT_FINANCE_HISTORY,
}

data class PermanentCardDeletionAssessment(val blocker: PermanentCardDeletionBlock?) {
    val allowed: Boolean get() = blocker == null
}

fun CanonicalFinanceDocument.assessPermanentCardDeletion(
    cardId: String,
    asOf: String,
): PermanentCardDeletionAssessment {
    val card = canonicalCards().firstOrNull { it.id == cardId }
        ?: return PermanentCardDeletionAssessment(PermanentCardDeletionBlock.CARD_NOT_FOUND)
    if (card.kind == "credit") {
        // Web only permits removing a credit profile after it has been archived
        // and every attributable legacy/current card movement has been settled.
        if (card.active) return PermanentCardDeletionAssessment(PermanentCardDeletionBlock.ACTIVE_CREDIT_CARD)
        if (creditDebtForCardAt(card.id, asOf) > 0.005)
            return PermanentCardDeletionAssessment(PermanentCardDeletionBlock.CREDIT_OUTSTANDING)
    } else {
        // A linked purchase/payment forbids physical deletion of non-credit card identity.
        // Preserve references instead of silently orphaning historical finance events.
        if (canonicalEvents().any { it.cardId == card.id &&
                it.kind in setOf("card_purchase", "card_payment") }) {
            return PermanentCardDeletionAssessment(PermanentCardDeletionBlock.NON_CREDIT_FINANCE_HISTORY)
        }
    }
    return PermanentCardDeletionAssessment(null)
}
