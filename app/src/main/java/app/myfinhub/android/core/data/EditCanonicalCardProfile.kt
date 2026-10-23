package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Edit only the non-secret card profile. The card kind, original identity,
 * encrypted vault reference, PAN last-four, and historical accounting/statement links
 * are intentionally outside this mutation. Web CardCreateDialog's profile editor never
 * touches sensitive card credentials; they belong to a separate AAL2 vault flow.
 */
data class EditCanonicalCardProfile(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val network: String,
    val formFactor: String,
    val creditLimit: Double?,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Επεξεργασία προφίλ κάρτας"

    private fun target(document: CanonicalFinanceDocument): JsonObject? =
        document.state.array("cards").filterIsInstance<JsonObject>()
            .filter { it.string("id") == cardId }.singleOrNull()

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val current = target(document) ?: return false
        return cardId.isNotBlank() && current.bool("active") != false &&
            current.string("bankId") == bankId &&
            current.string("nickname") == nickname.trim() &&
            current.string("network") == network &&
            current.string("formFactor").orEmpty().ifBlank { "physical" } == formFactor &&
            ((creditLimit == null && current.number("creditLimit") == null) ||
                (creditLimit != null && current.number("creditLimit")?.let {
                    moneyToCents(it) == moneyToCents(creditLimit)
                } == true)) &&
            current.string("kind") == expected.string("kind") &&
            current.string("vaultRef") == expected.string("vaultRef") &&
            current.string("last4") == expected.string("last4")
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank() && expected.string("id") == cardId) {
            "Δεν βρέθηκε ασφαλές αναγνωριστικό κάρτας."
        }
        require(nickname.trim().isNotEmpty() && nickname.trim().length <= 500) {
            "Το όνομα κάρτας πρέπει να περιέχει 1–500 χαρακτήρες."
        }
        require(bankId.trim().isNotEmpty() && bankId.trim().length <= 100) {
            "Διάλεξε έγκυρο εκδότη κάρτας."
        }
        require(network in setOf("visa", "mastercard", "other")) {
            "Μη έγκυρο δίκτυο κάρτας."
        }
        require(formFactor in setOf("physical", "virtual")) {
            "Μη έγκυρη μορφή κάρτας."
        }
        val kind = expected.string("kind")
        require(kind in setOf("debit", "prepaid", "credit")) {
            "Δεν επιτρέπεται αλλαγή λογιστικού τύπου κάρτας."
        }
        if (creditLimit != null) {
            require(kind == "credit" && creditLimit.isFinite() &&
                creditLimit > 0.0 && moneyToCents(creditLimit) > 0L) {
                "Το πιστωτικό όριο πρέπει να είναι θετικό και αφορά πιστωτική."
            }
        }
        require(expected.bool("active") != false) { "Η κάρτα είναι αρχειοθετημένη." }
        val rows = document.state.array("cards")
        val matches = rows.filterIsInstance<JsonObject>().filter { it.string("id") == cardId }
        require(matches.size == 1) {
            "Η κάρτα λείπει ή έχει διπλό αναγνωριστικό. Απαιτείται έλεγχος."
        }
        val current = matches.single()
        require(current.bool("active") != false) { "Η κάρτα δεν είναι ενεργή." }
        if (satisfiedBy(document)) return document
        require(current == expected) {
            "Η κάρτα άλλαξε σε άλλη συσκευή. Ανανέωσε τα στοιχεία πριν αποθηκεύσεις."
        }
        val fields = current.toMutableMap()
        fields["bankId"] = JsonPrimitive(bankId.trim())
        fields["nickname"] = JsonPrimitive(nickname.trim())
        fields["network"] = JsonPrimitive(network)
        fields["formFactor"] = JsonPrimitive(formFactor)
        if (kind == "credit") {
            if (creditLimit != null) fields["creditLimit"] = JsonPrimitive(creditLimit)
            else fields.remove("creditLimit")
        }
        fields["updatedAt"] = JsonPrimitive(nowIso)
        val updated = JsonObject(fields)
        val next = rows.map { raw ->
            if ((raw as? JsonObject)?.string("id") == cardId) updated else raw
        }
        return document.withMutableState(
            document.state.updated("cards", JsonArray(next)), nowIso,
        )
    }
}
