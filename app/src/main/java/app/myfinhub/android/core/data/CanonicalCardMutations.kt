package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deactivates one canonical card by stable ID while retaining its historical record.
 *
 * Activity and card-payment events may continue to reference the card ID, so Android does not
 * physically delete the canonical object. The normal product projection already filters inactive
 * cards, which removes the card from the visible stack without inventing a second card store.
 */
data class DeactivateCanonicalCard(
    val cardId: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Διαγραφή κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank()) { "Απαιτείται αναγνωριστικό κάρτας." }
        val cards = document.state.array("cards")
        val index = cards.indexOfFirst { raw -> (raw as? JsonObject)?.string("id") == cardId }
        require(index >= 0) { "Η κάρτα δεν είναι πλέον διαθέσιμη." }

        val current = cards[index] as? JsonObject ?: error("Η κάρτα δεν είναι έγκυρη.")
        if (current.bool("active") == false) return document

        val updatedCard = current
            .updated("active", JsonPrimitive(false))
            .updated("updatedAt", JsonPrimitive(nowIso))
        val updatedCards = cards.toMutableList().apply { this[index] = updatedCard }
        val updatedState = document.state.updated("cards", JsonArray(updatedCards))

        return CanonicalFinanceDocument(
            document.raw
                .updated("state", updatedState)
                .updated("updatedAt", JsonPrimitive(nowIso)),
        )
    }
}