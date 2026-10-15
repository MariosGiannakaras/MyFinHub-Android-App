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
    override val description: String = "Αρχειοθέτηση κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank()) { "Απαιτείται αναγνωριστικό κάρτας." }
        val cards = document.state.array("cards")
        val index = cards.indexOfFirst { raw -> (raw as? JsonObject)?.string("id") == cardId }
        require(index >= 0) { "Η κάρτα δεν είναι πλέον διαθέσιμη." }

        val current = cards[index] as? JsonObject ?: error("Η κάρτα δεν είναι έγκυρη.")
        if (current.bool("active") == false) return document

        val updatedCard = current
            .updated("active", JsonPrimitive(false))
            .updated("archivedAt", JsonPrimitive(nowIso))
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
/**
 * Web restoreCard parity: reactivate an archived profile using its stable ID.
 * Archive timestamps are cleared but accounting history, unknown Web fields and
 * the unchanged vault isolation policy remain intact. PAN/CVV are never restored.
 */
data class RestoreCanonicalCard(
    val cardId: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Επαναφορά αρχειοθετημένης κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank()) { "Απαιτείται αναγνωριστικό κάρτας." }
        val cards = document.state.array("cards")
        val index = cards.indexOfFirst { raw -> (raw as? JsonObject)?.string("id") == cardId }
        require(index >= 0) { "Η κάρτα δεν είναι διαθέσιμη για επαναφορά." }
        val current = cards[index] as? JsonObject ?: error("Η κάρτα δεν είναι έγκυρη.")
        if (current.bool("active") != false) return document
        val restored = JsonObject(current - "archivedAt")
            .updated("active", JsonPrimitive(true))
            .updated("updatedAt", JsonPrimitive(nowIso))
        val updatedCards = cards.toMutableList().apply { this[index] = restored }
        return CanonicalFinanceDocument(
            document.raw
                .updated("state", document.state.updated("cards", JsonArray(updatedCards)))
                .updated("updatedAt", JsonPrimitive(nowIso)),
        )
    }
}

/**
 * Web withCardSecretCleanupPending: removes only an eligible card profile, never
 * its financial history or private card vault records. A durable cleanup marker
 * remains until authenticated server AND legacy local vault deletion succeed.
 *
 * Not exposed to the UI until the post-commit secret-cleanup contract is validated.
 */
data class PermanentlyDeleteCanonicalCard(
    val cardId: String,
    val nowIso: String,
    val asOf: String = nowIso.take(10),
) : CanonicalFinanceMutation {
    override val description: String = "Οριστική διαγραφή εξοφλημένης κάρτας"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val id = cardId.trim()
        require(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$").matches(id)) {
            "Μη έγκυρο αναγνωριστικό κάρτας."
        }
        val deleteDay = java.time.LocalDate.parse(asOf)
        require(nowIso.startsWith(deleteDay.toString())) {
            "Η ημερομηνία διαγραφής δεν συμφωνεί με την ημερομηνία ελέγχου."
        }
        val cards = document.state.array("cards")
        val target = cards.mapNotNull { it as? JsonObject }.firstOrNull { it.string("id") == id }
        if (target == null) {
            val markerExists = document.state.array("pendingCardSecretDeletes")
                .any { (it as? JsonPrimitive)?.content == id }
            val tombstoneExists = document.state.array("deletedCards")
                .any { (it as? JsonObject)?.string("id") == id &&
                    (it as? JsonObject)?.string("kind") == "credit" }
            require(markerExists || tombstoneExists) {
                "Η κάρτα δεν υπάρχει για οριστική διαγραφή."
            }
            // Retrying the same committed intent must not create duplicate entries.
            return document
        }
        val eligibility = document.assessPermanentCardDeletion(id, asOf)
        require(eligibility.allowed) {
            "Δεν επιτρέπεται οριστική διαγραφή: ${eligibility.blocker}"
        }
        // A future-dated debt must not become orphaned by an as-of deletion.
        val latestLinkedDate = document.state.array("events")
            .mapNotNull { it as? JsonObject }
            .filter { event ->
                event.string("kind") in setOf("card_purchase", "card_payment") &&
                    (event.string("cardId") == id ||
                        (event.string("cardId") == null &&
                            target.string("kind") == "credit" &&
                            document.legacyCreditOwnerForDeletion() == id))
            }
            .mapNotNull { it.string("date") }
            .maxOrNull()
        require(latestLinkedDate == null || latestLinkedDate <= asOf) {
            "Υπάρχουν μελλοντικές συνδεδεμένες κινήσεις κάρτας."
        }

        val kept = JsonArray(cards.filterNot { (it as? JsonObject)?.string("id") == id })
        val alreadyPending = document.state.array("pendingCardSecretDeletes")
        val pendingSecrets = if (alreadyPending.any { (it as? JsonPrimitive)?.content == id }) {
            alreadyPending
        } else JsonArray(alreadyPending + JsonPrimitive(id))
        var next = document.state
            .updated("cards", kept)
            .updated("pendingCardSecretDeletes", pendingSecrets)
        if (target.string("kind") == "credit") {
            val tombstones = document.state.array("deletedCards")
            val createdAt = target.string("createdAt").orEmpty()
            val tombstone = JsonObject(mapOf(
                "id" to JsonPrimitive(id),
                "kind" to JsonPrimitive("credit"),
                "createdAt" to JsonPrimitive(createdAt),
                "deletedAt" to JsonPrimitive(nowIso),
            ))
            next = next.updated("deletedCards", JsonArray(
                tombstones.filterNot { (it as? JsonObject)?.string("id") == id } + tombstone
            ))
        }
        return CanonicalFinanceDocument(
            document.raw.updated("state", next)
                .updated("updatedAt", JsonPrimitive(nowIso))
        )
    }
}

/** Same deleted/current oldest-credit selection as Web cards.ts, used for legacy events. */
private fun CanonicalFinanceDocument.legacyCreditOwnerForDeletion(): String? {
    val live = canonicalCards().filter { it.kind == "credit" }
        .map { it.id to it.createdAt.orEmpty() }
    val deleted = deletedCreditCardReferences().map { it.id to it.createdAt }
    return (live + deleted).minWithOrNull(
        compareBy<Pair<String, String>> { it.second }.thenBy { it.first }
    )?.first
}
