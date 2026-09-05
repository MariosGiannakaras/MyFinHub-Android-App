package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Canonical card creation containing safe display/accounting metadata only. */
data class CreateCanonicalCard(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val formFactor: String,
    val last4: String?,
    val creditLimit: Double?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Προσθήκη κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val normalizedId = cardId.trim()
        val normalizedBankId = bankId.trim()
        val normalizedNickname = nickname.trim()
        val normalizedKind = kind.trim().lowercase()
        val normalizedNetwork = network.trim().lowercase()
        val normalizedFormFactor = formFactor.trim().lowercase()
        val normalizedLast4 = last4?.trim()?.takeIf(String::isNotBlank)

        require(normalizedId.isNotBlank()) { "Απαιτείται αναγνωριστικό κάρτας." }
        require(normalizedBankId.isNotBlank() && normalizedBankId.length <= 100) { "Συμπλήρωσε έγκυρο εκδότη κάρτας." }
        require(normalizedNickname.isNotBlank() && normalizedNickname.length <= 500) { "Συμπλήρωσε όνομα κάρτας." }
        require(normalizedKind in setOf("debit", "prepaid", "credit")) { "Ο τύπος κάρτας δεν είναι έγκυρος." }
        require(normalizedNetwork in setOf("visa", "mastercard", "other")) { "Το δίκτυο κάρτας δεν είναι έγκυρο." }
        require(normalizedFormFactor in setOf("physical", "virtual")) { "Η μορφή κάρτας δεν είναι έγκυρη." }
        require(normalizedLast4 == null || Regex("^\\d{4}$").matches(normalizedLast4)) { "Τα τελευταία 4 ψηφία πρέπει να είναι ακριβώς τέσσερα ψηφία." }
        if (creditLimit != null) {
            require(normalizedKind == "credit") { "Πιστωτικό όριο επιτρέπεται μόνο σε πιστωτική κάρτα." }
            require(creditLimit.isFinite() && creditLimit > 0.0) { "Το πιστωτικό όριο πρέπει να είναι μεγαλύτερο από μηδέν." }
        }

        val cards = document.state.array("cards")
        val existing = cards.mapNotNull { it as? JsonObject }.firstOrNull { it.string("id") == normalizedId }
        if (existing != null) {
            val same = existing.string("bankId") == normalizedBankId &&
                existing.string("nickname") == normalizedNickname &&
                existing.string("kind") == normalizedKind &&
                existing.string("network") == normalizedNetwork &&
                existing.string("formFactor").orEmpty().ifBlank { "physical" } == normalizedFormFactor &&
                existing.string("last4") == normalizedLast4 &&
                existing.bool("active") != false
            require(same) { "Υπάρχει ήδη διαφορετική κάρτα με το ίδιο αναγνωριστικό." }
            return document
        }

        val card = JsonObject(buildMap {
            put("id", JsonPrimitive(normalizedId))
            put("bankId", JsonPrimitive(normalizedBankId))
            put("nickname", JsonPrimitive(normalizedNickname))
            put("kind", JsonPrimitive(normalizedKind))
            put("network", JsonPrimitive(normalizedNetwork))
            put("formFactor", JsonPrimitive(normalizedFormFactor))
            normalizedLast4?.let { put("last4", JsonPrimitive(it)) }
            creditLimit?.let { put("creditLimit", JsonPrimitive(it)) }
            put("active", JsonPrimitive(true))
            put("createdAt", JsonPrimitive(nowIso))
            put("updatedAt", JsonPrimitive(nowIso))
        })
        val updatedState = document.state.updated("cards", JsonArray(cards + card))
        return CanonicalFinanceDocument(
            document.raw
                .updated("state", updatedState)
                .updated("updatedAt", JsonPrimitive(nowIso)),
        )
    }
}

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