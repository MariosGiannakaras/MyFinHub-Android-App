package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** The exact current source row, preserving Web seed/override/custom identity and metadata. */
data class CanonicalRecurringEditSource(val row: JsonObject, val seeded: Boolean)

fun CanonicalFinanceDocument.canonicalRecurringEditSource(id: String): CanonicalRecurringEditSource? {
    if (id.isBlank()) return null
    val custom = state.array("recurringCustom").filterIsInstance<JsonObject>()
        .filter { it.string("id") == id }
    val seeds = seed.array("recurring").filterIsInstance<JsonObject>()
        .filter { it.string("id") == id }
    // One and only one row across both source arrays; ambiguous historical IDs are read-only.
    if (custom.size + seeds.size != 1) return null
    return if (custom.isNotEmpty()) CanonicalRecurringEditSource(custom.single(), false)
    else CanonicalRecurringEditSource(
        (state.obj("recurringOverrides")[id] as? JsonObject) ?: seeds.single(), true,
    )
}

/**
 * Read-modify-write guard for Web recurringOverrides/custom editing.
 * Offline replay refuses a different server row instead of clobbering remote changes.
 * Lifecycle/source/logo and non-editor fields are retained verbatim.
 */
data class EditCanonicalRecurring(
    val recurringId: String,
    val expected: JsonObject,
    val draft: CanonicalRecurringCreateDraft,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Επεξεργασία παγίου"
    private val editableFields = setOf(
        "name", "amount", "amountMode", "estimateMin", "estimateMax",
        "accountId", "category", "recurrenceUnit", "recurrenceInterval",
        "day", "firstExpectedDate", "endDate",
    )

    fun editedRow(): JsonObject {
        require(expected.string("id") == recurringId && recurringId.isNotBlank()) {
            "Μη έγκυρη ταυτότητα επεξεργασίας παγίου."
        }
        // Reuse the SAME strict Web-derived validation as the creation editor.
        val normalized = CreateCanonicalRecurring(recurringId, nowIso, draft).newRow()
        return JsonObject((expected - editableFields) +
            normalized.filterKeys { it in editableFields })
    }

    fun matchesEdited(current: JsonObject): Boolean = current == editedRow()

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val edited = editedRow()
        val current = document.canonicalRecurringEditSource(recurringId)
            ?: throw IllegalArgumentException("Το πάγιο απουσιάζει ή έχει διπλό ιστορικό ID.")
        if (current.row == edited) return document
        require(current.row == expected) {
            "Το πάγιο άλλαξε στον server. Έλεγξε τη νεότερη έκδοση πριν αποθηκεύσεις."
        }
        require(document.canonicalAccounts().any {
            it.id == draft.accountId && it.kind != "credit"
        }) { "Ο λογαριασμός δεν είναι διαθέσιμος για πάγιο." }
        val state = if (current.seeded) {
            document.state.updated("recurringOverrides",
                JsonObject(document.state.obj("recurringOverrides") + (recurringId to edited)))
        } else {
            val rows = document.state.array("recurringCustom")
            val index = rows.indexOfFirst { (it as? JsonObject)?.string("id") == recurringId }
            require(index >= 0) { "Το πάγιο δεν βρέθηκε." }
            val mutable = rows.toMutableList()
            mutable[index] = edited
            document.state.updated("recurringCustom", JsonArray(mutable))
        }
        return document.withMutableState(state,nowIso)
    }
}
