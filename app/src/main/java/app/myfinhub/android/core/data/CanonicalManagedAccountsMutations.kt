package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Web AccountManagementSettings canonical settings update. The separately protected
 * owner+AAL2 IBAN metadata store is intentionally untouched by this mutation.
 * Seed accounts remain in seed and receive only a settings.accountOverrides row.
 */
data class UpsertCanonicalManagedAccount(
    val accountId: String,
    val source: String,
    val name: String,
    val mode: String,
    val providerId: String?,
    val providerName: String?,
    val bankCategory: String?,
    val cashType: String?,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Διαχείριση λογαριασμού"

    private fun desired(previous: JsonObject?): JsonObject {
        require(Regex("^account-[A-Za-z0-9._:-]{1,150}$").matches(accountId) ||
            (source != "new" && accountId.isNotBlank())) {
            "Μη έγκυρο αναγνωριστικό λογαριασμού."
        }
        require(source in setOf("new", "seed", "custom")) { "Άγνωστη πηγή λογαριασμού." }
        require(name.trim().length in 1..120) { "Το όνομα πρέπει να έχει 1–120 χαρακτήρες." }
        require(mode in setOf("bank", "cash")) { "Μη έγκυρος τύπος λογαριασμού." }
        if (mode == "bank") {
            require(!providerId.isNullOrBlank() && providerId.length <= 100 &&
                bankCategory in setOf("payroll", "current", "savings", "term", "payment", "other")) {
                "Διάλεξε τραπεζικό πάροχο και κατηγορία."
            }
        } else {
            require(cashType in setOf("cash", "reserve", "other")) {
                "Μη έγκυρος τύπος μετρητών."
            }
        }
        val reserve = mode == "cash" && cashType == "reserve"
        val term = mode == "bank" && bankCategory == "term"
        val effectiveKind = if (mode == "cash") "cash"
            else if (bankCategory == "savings" || term) "savings" else "bank"
        val patched = (previous ?: JsonObject(emptyMap())).toMutableMap()
        patched["id"] = JsonPrimitive(accountId)
        patched["name"] = JsonPrimitive(name.trim())
        patched["kind"] = JsonPrimitive(effectiveKind)
        patched["excludeFromAvailable"] = JsonPrimitive(reserve || term)
        patched["showInQuickChoices"] = JsonPrimitive(!reserve && !term)
        if (source != "seed") patched["custom"] = JsonPrimitive(true)
        if (mode == "bank") {
            patched["providerId"] = JsonPrimitive(providerId!!.trim())
            if (!providerName.isNullOrBlank()) patched["provider"] = JsonPrimitive(providerName.trim())
            else patched.remove("provider")
            patched["bankAccountCategory"] = JsonPrimitive(bankCategory!!)
            patched.remove("cashRole")
            patched.remove("cashType")
        } else {
            patched.remove("providerId")
            patched.remove("provider")
            patched.remove("bankAccountCategory")
            patched["cashRole"] = JsonPrimitive(if (reserve) "reserve" else "daily")
            patched["cashType"] = JsonPrimitive(cashType!!)
        }
        return JsonObject(patched)
    }

    private fun persistedSource(document: CanonicalFinanceDocument): JsonObject? {
        val settings = document.settingsObject()
        return when (source) {
            "seed" -> settings.obj("accountOverrides")[accountId] as? JsonObject
            "custom", "new" -> settings.array("customAccounts")
                .filterIsInstance<JsonObject>()
                .singleOrNull { it.string("id") == accountId }
            else -> null
        }
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val current = persistedSource(document) ?: return false
        val desired = runCatching { desired(expected) }.getOrNull() ?: return false
        val settings = document.settingsObject()
        val excluded = settings.array("excludedFromAvailable")
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
        return desired == current && settings.obj("accountNames").string(accountId) == name.trim() &&
            ((accountId in excluded) == (current.bool("excludeFromAvailable") == true))
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val settings = document.settingsObject()
        val seedMatches = document.seed.array("accounts")
            .filterIsInstance<JsonObject>().filter { it.string("id") == accountId }
        val custom = settings.array("customAccounts")
        val customMatches = custom.filterIsInstance<JsonObject>()
            .filter { it.string("id") == accountId }
        require(seedMatches.size <= 1 && customMatches.size <= 1) {
            "Διπλό αναγνωριστικό λογαριασμού: απαιτείται έλεγχος."
        }
        require(when (source) {
            "seed" -> seedMatches.size == 1 && customMatches.isEmpty()
            "custom" -> customMatches.size == 1 && seedMatches.isEmpty()
            "new" -> customMatches.isEmpty() && seedMatches.isEmpty()
            else -> false
        }) { "Η ταυτότητα ή η πηγή του λογαριασμού άλλαξε." }
        val previous = persistedSource(document)
        val item = desired(previous)
        if (satisfiedBy(document)) return document
        require(previous == expected) {
            "Ο λογαριασμός τροποποιήθηκε από άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        require(custom.size < 2000 || source != "new") {
            "Ξεπεράστηκε το όριο προσαρμοσμένων λογαριασμών."
        }
        val names = JsonObject(settings.obj("accountNames") +
            (accountId to JsonPrimitive(name.trim())))
        val excluded = settings.array("excludedFromAvailable")
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .filter { it != accountId }.toMutableList()
        if (item.bool("excludeFromAvailable") == true) excluded += accountId
        val nextSettings = settings.toMutableMap()
        nextSettings["accountNames"] = names
        nextSettings["excludedFromAvailable"] = JsonArray(excluded.distinct().map(::JsonPrimitive))
        if (source == "seed") {
            nextSettings["accountOverrides"] = JsonObject(
                settings.obj("accountOverrides") + (accountId to item),
            )
        } else {
            nextSettings["customAccounts"] = JsonArray(
                if (source == "new") custom + item else custom.map { row ->
                    if ((row as? JsonObject)?.string("id") == accountId) item else row
                },
            )
        }
        // A reserve cash / term deposit is not available for any default role.
        // Retain all other defaults. If the edited account was a default, select
        // a still-eligible existing source account or empty rather than the reserve.
        if (item.bool("showInQuickChoices") == false) {
            val fallback = document.canonicalAccounts()
                .firstOrNull { it.id != accountId && it.kind != "credit" &&
                    it.showInQuickChoices && !it.excludeFromAvailable }?.id.orEmpty()
            listOf("defaultExpenseAccount", "defaultIncomeAccount", "defaultLoanAccount")
                .forEach { field ->
                    if (settings.string(field) == accountId) {
                        nextSettings[field] = JsonPrimitive(fallback)
                    }
                }
        }
        return document.withMutableState(
            document.state.updated("settings", JsonObject(nextSettings)), nowIso,
        )
    }
}

/** Default payment/source accounts are owner-selected and must be usable, not reserves. */
data class SetCanonicalDefaultAccount(
    val role: String,
    val accountId: String,
    val expectedAccountId: String?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αλλαγή προεπιλεγμένου λογαριασμού"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        role in setOf("defaultExpenseAccount", "defaultIncomeAccount", "defaultLoanAccount") &&
            document.settingsObject().string(role) == accountId

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(role in setOf("defaultExpenseAccount", "defaultIncomeAccount", "defaultLoanAccount")) {
            "Μη έγκυρη προεπιλεγμένη χρήση."
        }
        val candidates = document.canonicalAccounts().filter { it.id == accountId }
        require(candidates.size == 1 && candidates.single().kind != "credit" &&
            !candidates.single().excludeFromAvailable &&
            candidates.single().showInQuickChoices) {
            "Ο επιλεγμένος λογαριασμός δεν είναι διαθέσιμος."
        }
        if (satisfiedBy(document)) return document
        val settings = document.settingsObject()
        require(settings.string(role) == expectedAccountId) {
            "Η προεπιλογή τροποποιήθηκε από άλλη συσκευή. Έλεγξε τα στοιχεία."
        }
        return document.withMutableState(
            document.state.updated("settings",
                JsonObject(settings + (role to JsonPrimitive(accountId)))),
            nowIso,
        )
    }
}
