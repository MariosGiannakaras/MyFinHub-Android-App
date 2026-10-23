package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Web counterpartyKindForEvent: never infer a saved ID from text, notes or OCR. */
fun canonicalCounterpartyKindForEvent(kind: String): String? = when (kind) {
    "expense", "card_purchase", "split", "refund" -> "merchant"
    "income", "lending", "repayment", "lending_writeoff" -> "beneficiary"
    else -> null
}

/**
 * Web withEventCounterparty with a stronger expected-source compare-and-swap guard.
 * Explicitly links one owner-selected identity to one unique canonical event.
 * Does not change transaction note, category, amount, legs or date.
 */
data class LinkCanonicalEventCounterparty(
    val eventId: String,
    val counterpartyId: String?,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Σύνδεση αντισυμβαλλομένου"

    private fun desiredRow(): JsonObject {
        val base = expected.toMutableMap()
        if (counterpartyId == null) base.remove("counterpartyId")
        else base["counterpartyId"] = JsonPrimitive(counterpartyId)
        base["updatedAt"] = JsonPrimitive(nowIso)
        return JsonObject(base)
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val matches = document.state.array("events").filterIsInstance<JsonObject>()
            .filter { it.string("id") == eventId }
        return eventId.isNotBlank() && matches.size == 1 &&
            matches.single() == desiredRow()
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(eventId.isNotBlank() && expected.string("id") == eventId) {
            "Δεν βρέθηκε ασφαλής ταυτότητα συναλλαγής."
        }
        val kind = canonicalCounterpartyKindForEvent(expected.string("kind").orEmpty())
            ?: throw IllegalArgumentException("Η κίνηση δεν δέχεται αντισυμβαλλόμενο.")
        if (counterpartyId != null) {
            require(counterpartyId.isNotBlank()) { "Μη έγκυρος αντισυμβαλλόμενος." }
            val candidates = document.state.array("counterparties")
                .filterIsInstance<JsonObject>()
                .filter { it.string("id") == counterpartyId }
            require(candidates.size == 1 && candidates.single().string("kind") == kind) {
                "Η ταυτότητα δεν υπάρχει ή ανήκει σε διαφορετικό τύπο."
            }
        }
        val rows = document.state.array("events")
        val matches = rows.filterIsInstance<JsonObject>()
            .filter { it.string("id") == eventId }
        require(matches.size == 1) {
            "Η κίνηση απουσιάζει ή έχει διπλό ID. Απαιτείται έλεγχος."
        }
        if (satisfiedBy(document)) return document
        require(matches.single() == expected) {
            "Η κίνηση άλλαξε σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        val newRow = desiredRow()
        return document.withMutableState(
            document.state.updated("events", JsonArray(rows.map { raw ->
                if ((raw as? JsonObject)?.string("id") == eventId) newRow else raw
            })), nowIso,
        )
    }
}
