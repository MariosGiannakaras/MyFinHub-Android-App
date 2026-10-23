package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Web AccountManagementSettings accountReferenced parity: deletion is allowed only
 * for an unused custom cash profile. Bank profiles are deliberately excluded until
 * a separate, AAL2-protected IBAN metadata cleanup can be confirmed.
 *
 * Conservatively block a deletion when the ID occurs anywhere in source finance
 * records, even in an unknown future schema field. Settings account metadata and
 * the custom account's own definition are excluded from that dependency scan.
 */
private fun JsonElement.containsAccountReference(accountId: String): Boolean = when (this) {
    is JsonPrimitive -> contentOrNull == accountId
    is JsonArray -> any { it.containsAccountReference(accountId) }
    is JsonObject -> values.any { it.containsAccountReference(accountId) }
    else -> false
}

fun CanonicalFinanceDocument.customCashAccountCanBeDeleted(accountId: String): Boolean {
    if (accountId.isBlank()) return false
    val settings = settingsObject()
    if (seed.array("accounts").any {
        (it as? JsonObject)?.string("id") == accountId
    }) return false
    val custom = settings.array("customAccounts").filterIsInstance<JsonObject>()
        .filter { it.string("id") == accountId }
    if (custom.size != 1 || custom.single().string("kind") != "cash") return false
    // Finance references can be hidden in event legs, older transaction overrides,
    // recurring/loan/scheduled data or unknown schema extensions.
    if (seed.filterKeys { it != "accounts" }.values.any {
        it.containsAccountReference(accountId)
    }) return false
    if (state.filterKeys { it != "settings" }.values.any {
        it.containsAccountReference(accountId)
    }) return false
    // Transaction rules can be held in state or a settings extension.
    if (settings.array("transactionRules").any {
        it.containsAccountReference(accountId)
    }) return false
    return true
}

/** Destructive profile edit, not a ledger edit: only unused custom cash sources. */
data class DeleteCanonicalCustomCashAccount(
    val accountId: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αφαίρεση μη χρησιμοποιούμενου λογαριασμού μετρητών"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        if (accountId.isBlank()) return false
        val settings = document.settingsObject()
        val absent = settings.array("customAccounts")
            .filterIsInstance<JsonObject>().none { it.string("id") == accountId }
        if (!absent) return false
        val clean = settings.obj("accountNames")[accountId] == null &&
            settings.obj("accountOverrides")[accountId] == null &&
            settings.array("excludedFromAvailable").none {
                (it as? JsonPrimitive)?.contentOrNull == accountId
            }
        val rolesClean = listOf(
            "defaultExpenseAccount", "defaultIncomeAccount", "defaultLoanAccount",
        ).all { settings.string(it) != accountId }
        return clean && rolesClean
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(accountId.isNotBlank() && expected.string("id") == accountId &&
            expected.string("kind") == "cash") {
            "Η διαγραφή επιτρέπεται μόνο για προσαρμοσμένα μετρητά."
        }
        if (satisfiedBy(document)) return document
        require(document.customCashAccountCanBeDeleted(accountId)) {
            "Ο λογαριασμός έχει οικονομικές αναφορές, δεν είναι προσαρμοσμένα " +
                "μετρητά ή έχει ήδη αλλάξει."
        }
        val settings = document.settingsObject()
        val custom = settings.array("customAccounts")
        val actual = custom.filterIsInstance<JsonObject>()
            .singleOrNull { it.string("id") == accountId }
        require(actual == expected) {
            "Ο λογαριασμός άλλαξε σε άλλη συσκευή. Ανανέωσε τα στοιχεία."
        }
        val remaining = custom.filterNot {
            (it as? JsonObject)?.string("id") == accountId
        }
        val names = settings.obj("accountNames").filterKeys { it != accountId }
        val overrides = settings.obj("accountOverrides").filterKeys { it != accountId }
        val excluded = settings.array("excludedFromAvailable").filterNot {
            (it as? JsonPrimitive)?.contentOrNull == accountId
        }
        val options = document.canonicalAccounts().filter {
            it.id != accountId && it.kind != "credit" &&
                it.showInQuickChoices && !it.excludeFromAvailable
        }
        val fallback = options.firstOrNull()?.id.orEmpty()
        val next = settings.toMutableMap()
        next["customAccounts"] = JsonArray(remaining)
        next["accountNames"] = JsonObject(names)
        next["accountOverrides"] = JsonObject(overrides)
        next["excludedFromAvailable"] = JsonArray(excluded)
        listOf("defaultExpenseAccount", "defaultIncomeAccount", "defaultLoanAccount")
            .forEach { role ->
                if (settings.string(role) == accountId) next[role] = JsonPrimitive(fallback)
            }
        return document.withMutableState(
            document.state.updated("settings", JsonObject(next)), nowIso,
        )
    }
}
