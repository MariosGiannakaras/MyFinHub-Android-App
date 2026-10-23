package app.myfinhub.android.core.data

import kotlin.math.roundToInt

import java.time.LocalDate
import kotlin.math.floor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * A user-approved, replayable canonical mutation.
 *
 * Mutation instances contain stable ids/timestamps so an explicit retry after a 409 can load the
 * newest server revision and replay the same intent without creating duplicate events.
 */
sealed interface CanonicalFinanceMutation {
    val description: String
    fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument
}

data class CanonicalSplitDraft(
    val id: String,
    val label: String,
    val category: String,
    val subcategory: String? = null,
    val amount: Double,
)

data class CanonicalEventDraft(
    val kind: String,
    val date: String,
    val amount: Double,
    val note: String,
    val category: String? = null,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val cardId: String? = null,
    val statementId: String? = null,
    val parts: List<CanonicalSplitDraft> = emptyList(),
)

/**
 * Compatibility adapter for the legacy four-kind constructor.
 *
 * All new financial events must use the single strict canonical transaction validator and
 * [AppendCanonicalEvent] replay boundary, regardless of which historical UI caller initiates them.
 * Keep the old public DTO for pending-mutation wire compatibility; do not rebuild event JSON here.
 */
fun createCanonicalEventMutation(
    document: CanonicalFinanceDocument,
    draft: CanonicalEventDraft,
    eventId: String,
    nowIso: String,
): CanonicalFinanceMutation {
    require(eventId.isNotBlank()) { "Απαιτείται αναγνωριστικό κίνησης." }
    require(draft.date.isNotBlank()) { "Απαιτείται ημερομηνία." }
    require(draft.note.isNotBlank()) { "Πρόσθεσε μια σύντομη περιγραφή." }
    require(draft.kind in setOf("expense", "transfer", "card_payment", "split")) {
        "Unsupported legacy Android event kind."
    }
    val amountCents = moneyToCents(draft.amount)
    require(amountCents > 0L) { "Το ποσό πρέπει να είναι μεγαλύτερο από μηδέν." }
    if (draft.kind == "split") {
        require(draft.parts.size >= 2) { "Ο διαχωρισμός χρειάζεται τουλάχιστον δύο μέρη." }
        require(draft.parts.sumOf { moneyToCents(it.amount) } == amountCents) {
            "Τα επιμέρους ποσά πρέπει να ισούνται ακριβώς με το σύνολο."
        }
    }

    return createCanonicalTransactionEntryMutation(
        document = document,
        draft = CanonicalTransactionEntryDraft(
            kind = draft.kind,
            date = draft.date,
            amount = centsToMoney(amountCents),
            note = draft.note,
            category = draft.category,
            accountId = draft.accountId,
            fromAccountId = draft.fromAccountId,
            toAccountId = draft.toAccountId,
            cardId = draft.cardId,
            statementId = draft.statementId,
            parts = draft.parts.map { part ->
                CanonicalTransactionSplitDraft(
                    id = part.id,
                    label = part.label,
                    category = part.category.trim().ifBlank { "Άλλο" },
                    subcategory = part.subcategory,
                    amount = part.amount,
                )
            },
        ),
        eventId = eventId,
        nowIso = nowIso,
    )
}

data class AppendCanonicalEvent(
    val event: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση κίνησης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val id = event.string("id") ?: error("Event id is required")
        val events = document.state.array("events")
        if (events.any { (it as? JsonObject)?.string("id") == id }) return document
        // Match Web creditStatements.ts at the canonical append/replay boundary.
        // Unlinked repayments stay unallocated until a statement is explicitly selected.
        val prepared = document.prepareCanonicalCreditStatementEvent(event, nowIso)
        var nextState = document.state.updated("events", JsonArray(events + prepared.event))
        if (prepared.statements != document.state.array("creditStatements")) {
            nextState = nextState.updated("creditStatements", JsonArray(prepared.statements))
        }
        return document.withMutableState(nextState, nowIso)
    }
}

/**
 * Owner-approved recurring pause/stop/reactivation through canonical finance state.
 * Seeded rows write complete recurringOverrides records; custom rows are edited in place.
 * No synthetic payments or seed mutations are created.
 */
data class SetCanonicalRecurringStatus(
    val recurringId: String,
    val status: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αλλαγή κατάστασης παγίου"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(recurringId.isNotBlank()) { "Απαιτείται αναγνωριστικό παγίου." }
        require(status in setOf("active", "paused", "stopped")) { "Άγνωστη κατάσταση παγίου." }
        // A duplicate historical ID would change the first matching source
        // while leaving other rows inconsistent. Preserve them read-only.
        require(document.allCanonicalRecurringItems().count { it.id == recurringId } == 1) {
            "Το πάγιο έχει μη μοναδικό αναγνωριστικό. Η αλλαγή κατάστασης αποκλείστηκε."
        }
        val custom = document.state.array("recurringCustom")
        val index = custom.indexOfFirst { (it as? JsonObject)?.string("id") == recurringId }
        fun updated(item: JsonObject): JsonObject = JsonObject(item + mapOf(
            "active" to JsonPrimitive(status == "active"),
            "status" to JsonPrimitive(status),
        ))
        val newState = if (index >= 0) {
            val mutable = custom.toMutableList()
            mutable[index] = updated(mutable[index] as JsonObject)
            document.state.updated("recurringCustom", JsonArray(mutable))
        } else {
            val seed = document.seed.array("recurring").firstOrNull {
                (it as? JsonObject)?.string("id") == recurringId
            } as? JsonObject ?: throw IllegalArgumentException("Το πάγιο δεν βρέθηκε.")
            val overrides = document.state.obj("recurringOverrides")
            val before = (overrides[recurringId] as? JsonObject) ?: seed
            document.state.updated("recurringOverrides",
                JsonObject(overrides + (recurringId to updated(before))))
        }
        return document.withMutableState(newState, nowIso)
    }
}

data class EditCanonicalActivity(
    val transactionId: String,
    val note: String,
    val category: String,
    val nowIso: String,
    /** null keeps an older queued edit backward-compatible; non-null is an explicit date edit. */
    val date: String? = null,
    /** null preserves the existing value; an empty string explicitly clears subcategory. */
    val subcategory: String? = null,
    /** Full Web LegacyTransactionEditor fields. Null means an old partial queued edit. */
    val legacyType: String? = null,
    val legacyAmount: Double? = null,
    val legacyAccountId: String? = null,
    val legacyFromAccountId: String? = null,
    val legacyToAccountId: String? = null,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση αλλαγών κίνησης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val normalizedNote = note.trim()
        require(normalizedNote.isNotBlank()) { "Η σημείωση δεν μπορεί να είναι κενή." }
        val normalizedCategory = category.trim()
        val categoryValue = normalizedCategory.takeIf(String::isNotBlank)?.let(::JsonPrimitive)
        val normalizedDate = date?.trim()?.also { raw ->
            require(runCatching { LocalDate.parse(raw) }.isSuccess) { "Η ημερομηνία δεν είναι έγκυρη." }
        }
        val subcategoryWasSpecified = subcategory != null
        val normalizedSubcategory = subcategory?.trim()
        val subcategoryValue = normalizedSubcategory?.takeIf(String::isNotBlank)?.let(::JsonPrimitive)
        require(legacyType != null || (legacyAmount == null && legacyAccountId == null &&
            legacyFromAccountId == null && legacyToAccountId == null)) {
            "Δεν επιτρέπονται μεμονωμένες αλλαγές ιστορικού ποσού/λογαριασμού."
        }

        fun legacyUpdates(current: JsonObject): JsonObject {
            val type = legacyType ?: return current
            require(type in setOf("expense", "income", "transfer", "adjustment")) {
                "Ο τύπος ιστορικής κίνησης δεν υποστηρίζεται."
            }
            val cents = moneyToCents(legacyAmount ?: Double.NaN)
            require(if (type == "adjustment") cents != 0L else cents > 0L) {
                if (type == "adjustment") "Η διόρθωση δεν μπορεί να έχει μηδενικό ποσό."
                else "Συμπλήρωσε ποσό μεγαλύτερο από μηδέν."
            }
            val knownAccounts = document.canonicalAccounts()
                .filter { it.kind != "credit" }.map { it.id }.toSet()
            fun account(raw: String?, label: String): String {
                val id = raw?.trim().orEmpty()
                require(id.isNotBlank() && id in knownAccounts) { "$label δεν είναι πλέον διαθέσιμος." }
                return id
            }
            val normalizedAccount = if (type == "transfer") null
                else account(legacyAccountId, "Ο λογαριασμός")
            val fromAccount = if (type == "transfer")
                account(legacyFromAccountId, "Ο λογαριασμός προέλευσης") else null
            val toAccount = if (type == "transfer")
                account(legacyToAccountId, "Ο λογαριασμός προορισμού") else null
            require(type != "transfer" || fromAccount != toAccount) {
                "Η μεταφορά χρειάζεται δύο διαφορετικούς λογαριασμούς."
            }
            return current
                .updated("type", JsonPrimitive(type))
                .updated("amount", JsonPrimitive(centsToMoney(cents)))
                .updated("accountId", normalizedAccount?.let(::JsonPrimitive))
                .updated("fromAccountId", fromAccount?.let(::JsonPrimitive))
                .updated("toAccountId", toAccount?.let(::JsonPrimitive))
                .updated("subcategory", if (normalizedCategory.isBlank()) null else subcategoryValue)
        }

        fun commonUpdates(current: JsonObject): JsonObject {
            var updated = current
                .updated("note", JsonPrimitive(normalizedNote))
                .updated("category", categoryValue)
            if (normalizedDate != null) updated = updated.updated("date", JsonPrimitive(normalizedDate))
            if (subcategoryWasSpecified) updated = updated.updated("subcategory", subcategoryValue)
            return updated
        }

        val events = document.state.array("events")
        val eventIndex = events.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (eventIndex >= 0) {
            require(legacyType == null) { "Τα στοιχεία ποσού/λογαριασμού των events δεν τροποποιούνται." }
            val current = events[eventIndex] as JsonObject
            val updatedEvent = commonUpdates(current).updated("updatedAt", JsonPrimitive(nowIso))
            val updatedEvents = events.toMutableList().apply { this[eventIndex] = updatedEvent }
            return document.withMutableState(document.state.updated("events", JsonArray(updatedEvents)), nowIso)
        }

        val custom = document.state.array("customTransactions")
        val customIndex = custom.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (customIndex >= 0) {
            val updatedTx = legacyUpdates(commonUpdates(custom[customIndex] as JsonObject))
            val updatedCustom = custom.toMutableList().apply { this[customIndex] = updatedTx }
            return document.withMutableState(document.state.updated("customTransactions", JsonArray(updatedCustom)), nowIso)
        }

        val seedTransaction = document.seed.array("transactions")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("id") == transactionId }
            ?: error("Η κίνηση δεν είναι πλέον διαθέσιμη.")
        val overrides = document.state.obj("overrides")
        val currentOverride = overrides[transactionId] as? JsonObject ?: seedTransaction
        val updatedOverride = legacyUpdates(commonUpdates(currentOverride))
        return document.withMutableState(
            document.state.updated("overrides", overrides.updated(transactionId, updatedOverride)),
            nowIso,
        )
    }
}
/** Removes a transaction from the canonical effective ledger while preserving shared schema semantics. */
data class DeleteCanonicalActivity(
    val transactionId: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Διαγραφή κίνησης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val id = transactionId.trim()
        require(id.isNotBlank()) { "Η κίνηση δεν είναι πλέον διαθέσιμη." }

        val events = document.state.array("events")
        val eventIndex = events.indexOfFirst { (it as? JsonObject)?.string("id") == id }
        if (eventIndex >= 0) {
            val updatedEvents = events.toMutableList().apply { removeAt(eventIndex) }
            return document.withMutableState(document.state.updated("events", JsonArray(updatedEvents)), nowIso)
        }

        val custom = document.state.array("customTransactions")
        val customIndex = custom.indexOfFirst { (it as? JsonObject)?.string("id") == id }
        if (customIndex >= 0) {
            val updatedCustom = custom.toMutableList().apply { removeAt(customIndex) }
            return document.withMutableState(document.state.updated("customTransactions", JsonArray(updatedCustom)), nowIso)
        }

        val seedExists = document.seed.array("transactions")
            .mapNotNull { it as? JsonObject }
            .any { it.string("id") == id }
        require(seedExists) { "Η κίνηση δεν είναι πλέον διαθέσιμη." }

        val deleted = when (val current = document.state["deleted"]) {
            is JsonArray -> current.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toMutableSet()
            is JsonObject -> current.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys.toMutableSet()
            else -> mutableSetOf()
        }.apply { add(id) }
        val overrides = document.state.obj("overrides").updated(id, null)
        val updatedState = document.state
            .updated("deleted", JsonArray(deleted.sorted().map(::JsonPrimitive)))
            .updated("overrides", overrides)
        return document.withMutableState(updatedState, nowIso)
    }
}


/**
 * Web savingsGoals.ts: durable goal metadata, not a transfer or an allocation of cash.
 * Captured asOf prevents offline replay from invalidating an originally future deadline.
 */
data class UpsertCanonicalSavingsGoal(
    val goalId: String,
    val name: String,
    val targetAmount: Double,
    val targetDate: String?,
    val asOf: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση στόχου αποταμίευσης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(goalId.isNotBlank() && goalId.length <= 200) { "Μη έγκυρο αναγνωριστικό στόχου." }
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 500) { "Απαιτείται όνομα στόχου." }
        val cents = moneyToCents(targetAmount)
        require(cents > 0) { "Το ποσό στόχου πρέπει να είναι θετικό." }
        val today = runCatching { java.time.LocalDate.parse(asOf) }.getOrNull()
            ?: throw IllegalArgumentException("Άκυρη ημερομηνία ενημέρωσης.")
        val rows = document.state.array("savingsGoals")
        val index = rows.indexOfFirst { (it as? JsonObject)?.string("id") == goalId }
        val original = rows.getOrNull(index) as? JsonObject
        if (targetDate != null) {
            val date = runCatching { java.time.LocalDate.parse(targetDate) }.getOrNull()
                ?: throw IllegalArgumentException("Μη έγκυρη προθεσμία στόχου.")
            require(!date.isBefore(today) || original?.string("targetDate") == targetDate) {
                "Δεν επιτρέπεται νέα παρελθούσα προθεσμία."
            }
        }
        val updated = JsonObject((original?.toMutableMap() ?: mutableMapOf()).apply {
            put("id", JsonPrimitive(goalId))
            put("name", JsonPrimitive(trimmed))
            put("targetAmount", JsonPrimitive(centsToMoney(cents)))
            if (targetDate == null) remove("targetDate") else put("targetDate", JsonPrimitive(targetDate))
            put("createdAt", JsonPrimitive(original?.string("createdAt") ?: nowIso))
            put("updatedAt", JsonPrimitive(nowIso))
        })
        val all = rows.toMutableList()
        if (index >= 0) all[index] = updated else all += updated
        return document.withMutableState(document.state.updated("savingsGoals", JsonArray(all)), nowIso)
    }
}

/** Removes goal metadata only; never deletes a savings account or its finance history. */
data class DeleteCanonicalSavingsGoal(
    val goalId: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Διαγραφή στόχου αποταμίευσης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(goalId.isNotBlank()) { "Απαιτείται αναγνωριστικό στόχου." }
        val rows = document.state.array("savingsGoals")
        val remaining = rows.filterNot { (it as? JsonObject)?.string("id") == goalId }
        if (remaining.size == rows.size) return document
        return document.withMutableState(
            document.state.updated("savingsGoals", JsonArray(remaining)), nowIso,
        )
    }
}

data class UpsertOverallBudget(
    val month: String,
    val amount: Double,
    val alertThreshold: Int,
    val budgetId: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση budget"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val cents = moneyToCents(amount)
        require(cents > 0) { "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν." }
        require(alertThreshold in 1..100) { "Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100% για συμβατότητα παλαιών εκκρεμών αλλαγών." }
        val budgets = document.state.array("budgets")
        val index = budgets.indexOfFirst { raw ->
            val item = raw as? JsonObject
            item?.string("month") == month && item.string("scope") == "overall"
        }
        val existing = budgets.getOrNull(index) as? JsonObject
        val item = JsonObject(buildMap {
            put("id", JsonPrimitive(existing?.string("id") ?: budgetId))
            put("month", JsonPrimitive(month))
            put("scope", JsonPrimitive("overall"))
            put("amount", JsonPrimitive(centsToMoney(cents)))
            put("alertThreshold", JsonPrimitive(alertThreshold.coerceIn(50, 99) / 100.0))
            put("createdAt", JsonPrimitive(existing?.string("createdAt") ?: nowIso))
            put("updatedAt", JsonPrimitive(nowIso))
        })
        val updated = budgets.toMutableList()
        if (index >= 0) updated[index] = item else updated += item
        return document.withMutableState(document.state.updated("budgets", JsonArray(updated)), nowIso)
    }
}



/** User-confirmed metadata removal; no ledger movement or historical event is deleted. */
data class DeleteCategoryBudget(
    val budgetId: String,
    val month: String,
    val category: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Διαγραφή ορίου κατηγορίας"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        budgetId.isNotBlank() &&
            document.state.array("budgets").none {
                (it as? JsonObject)?.string("id") == budgetId
            }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(budgetId.isNotBlank() && category.isNotBlank()) { "Άγνωστο όριο κατηγορίας." }
        val rows = document.state.array("budgets")
        val matches = rows.mapNotNull { it as? JsonObject }
            .filter { it.string("id") == budgetId }
        require(matches.size <= 1) { "Διπλό ID ορίου: απαιτείται έλεγχος." }
        val row = matches.singleOrNull() ?: return document
        require(row == expected && row.string("scope") == "category" &&
            row.string("month") == month && row.string("category") == category) {
            "Το όριο άλλαξε σε άλλη συσκευή. Έλεγξε την ενημερωμένη εγγραφή."
        }
        return document.withMutableState(
            document.state.updated("budgets", JsonArray(rows.filterNot { it == row })),
            nowIso,
        )
    }
}

/** Web SavingsPage target setting. Zero is a valid disabled percentage, not a missing value. */
data class SetCanonicalSavingsTargetRate(
    val rate: Double,
    val expectedRate: Double?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Αλλαγή μηνιαίου στόχου αποταμίευσης"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        rate.isFinite() && rate in 0.0..1.0 &&
            document.settingsObject().number("savingsTargetRate") == rate

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(rate.isFinite() && rate in 0.0..1.0) {
            "Ο στόχος αποταμίευσης πρέπει να είναι 0–100%."
        }
        if (satisfiedBy(document)) return document
        val actual = document.settingsObject().number("savingsTargetRate")
        require(actual == expectedRate) {
            "Ο στόχος αποταμίευσης άλλαξε σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        val settings = document.settingsObject()
        return document.withMutableState(
            document.state.updated("settings",
                JsonObject(settings + ("savingsTargetRate" to JsonPrimitive(rate)))),
            nowIso,
        )
    }
}

/** Web-shaped category budget upsert with expected-row replay and no ledger mutation. */
data class UpsertCategoryBudget(
    val month: String,
    val category: String,
    val amount: Double,
    val alertThreshold: Int,
    val budgetId: String,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση ορίου κατηγορίας"

    private fun matches(document: CanonicalFinanceDocument) =
        document.state.array("budgets").mapNotNull { it as? JsonObject }.filter {
            it.string("month") == month && it.string("scope") == "category" &&
                it.string("category") == category
        }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val row = matches(document).singleOrNull() ?: return false
        val cents = row.number("amount") ?: return false
        val threshold = row.number("alertThreshold") ?: return false
        return budgetId.isNotBlank() && row.string("id") == budgetId &&
            runCatching { moneyToCents(cents) == moneyToCents(amount) }.getOrDefault(false) &&
            (if (threshold <= 1.0) (threshold * 100.0).roundToInt()
             else threshold.roundToInt()) == alertThreshold
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(runCatching { java.time.YearMonth.parse(month).toString() == month }.getOrDefault(false)) {
            "Μη έγκυρος μήνας προϋπολογισμού."
        }
        require(category.isNotBlank() && category == category.trim()) { "Επίλεξε κατηγορία." }
        require(budgetId.isNotBlank()) { "Μη έγκυρο ID ορίου." }
        val cents = moneyToCents(amount)
        require(cents > 0) { "Το όριο πρέπει να είναι μεγαλύτερο από μηδέν." }
        require(alertThreshold in 50..99) { "Η προειδοποίηση πρέπει να είναι 50–99%." }
        val candidates = matches(document)
        require(candidates.size <= 1) { "Ιστορικά διπλό όριο κατηγορίας: μόνο προβολή." }
        if (satisfiedBy(document)) return document
        val current = candidates.singleOrNull()
        require(current == expected && (current == null || current.string("id") == budgetId)) {
            "Το όριο άλλαξε στον server. Έλεγξε τη νεότερη έκδοση."
        }
        val row = JsonObject((current ?: JsonObject(emptyMap())) + mapOf(
            "id" to JsonPrimitive(budgetId),
            "month" to JsonPrimitive(month),
            "scope" to JsonPrimitive("category"),
            "category" to JsonPrimitive(category),
            "amount" to JsonPrimitive(centsToMoney(cents)),
            "alertThreshold" to JsonPrimitive(alertThreshold / 100.0),
            "createdAt" to JsonPrimitive(current?.string("createdAt") ?: nowIso),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
        val rows = document.state.array("budgets").toMutableList()
        if (current == null) rows += row else {
            val index = rows.indexOfFirst { it == current }
            require(index >= 0) { "Το όριο δεν βρέθηκε." }
            rows[index] = row
        }
        return document.withMutableState(document.state.updated("budgets", JsonArray(rows)), nowIso)
    }
}

/** JavaScript Number.MAX_SAFE_INTEGER: Web money.ts refuses larger cent integers. */
internal const val MAX_SAFE_CANONICAL_CENTS = 9_007_199_254_740_991L

/**
 * Web src/lib/money.ts: Math.round((value + sign(value) * Number.EPSILON) * 100).
 *
 * Math.round ties toward +infinity (including negative half-cent values); direct
 * Kotlin roundToLong omits the Web epsilon and can misclassify 1.005 as 1.00.
 * Compare the fractional part instead of adding 0.5 to the entire floating
 * value: adding 0.5 at ~2^53 can accidentally change an already-safe integer.
 * This validation is shared by entry, split, loan and savings mutations.
 */
fun moneyToCents(value: Double): Long {
    require(value.isFinite()) { "Το ποσό δεν είναι έγκυρο." }
    val biased = value + if (value < 0.0) -Math.ulp(1.0) else Math.ulp(1.0)
    val scaled = biased * 100.0
    require(scaled.isFinite()) { "Το ποσό είναι εκτός επιτρεπτού εύρους." }
    val lower = floor(scaled)
    val rounded = if (scaled - lower >= 0.5) lower + 1.0 else lower
    require(rounded >= -MAX_SAFE_CANONICAL_CENTS.toDouble() &&
        rounded <= MAX_SAFE_CANONICAL_CENTS.toDouble()) {
        "Το ποσό είναι εκτός επιτρεπτού εύρους."
    }
    return rounded.toLong()
}

fun centsToMoney(cents: Long): Double = cents / 100.0

fun equalExpenseSplit(
    total: Double,
    parts: Int,
    category: String,
    idPrefix: String,
): List<CanonicalSplitDraft> {
    require(parts >= 2) { "Ο διαχωρισμός χρειάζεται τουλάχιστον δύο μέρη." }
    val totalCents = moneyToCents(total)
    require(totalCents > 0) { "Το σύνολο πρέπει να είναι θετικό." }
    val base = totalCents / parts
    val remainder = (totalCents % parts).toInt()
    return (0 until parts).map { index ->
        CanonicalSplitDraft(
            id = "$idPrefix-${index + 1}",
            label = "Μέρος ${index + 1}",
            category = category.trim().ifBlank { "Άλλο" },
            amount = centsToMoney(base + if (index < remainder) 1L else 0L),
        )
    }
}

internal fun CanonicalFinanceDocument.withMutableState(
    newState: JsonObject,
    nowIso: String,
): CanonicalFinanceDocument = CanonicalFinanceDocument(
    raw
        .updated("state", newState)
        .updated("updatedAt", JsonPrimitive(nowIso)),
)
