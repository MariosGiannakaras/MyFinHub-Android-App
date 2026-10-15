package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.roundToLong
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
        val custom = document.state.array("recurringCustom")
        val index = custom.indexOfFirst { (it as? JsonObject)?.string("id") == recurringId }
        fun updated(item: JsonObject): JsonObject = JsonObject(item + mapOf(
            "active" to JsonPrimitive(status == "active"),
            "status" to JsonPrimitive(status),
            "updatedAt" to JsonPrimitive(nowIso),
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
            val current = events[eventIndex] as JsonObject
            val updatedEvent = commonUpdates(current).updated("updatedAt", JsonPrimitive(nowIso))
            val updatedEvents = events.toMutableList().apply { this[eventIndex] = updatedEvent }
            return document.withMutableState(document.state.updated("events", JsonArray(updatedEvents)), nowIso)
        }

        val custom = document.state.array("customTransactions")
        val customIndex = custom.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (customIndex >= 0) {
            val updatedTx = commonUpdates(custom[customIndex] as JsonObject)
            val updatedCustom = custom.toMutableList().apply { this[customIndex] = updatedTx }
            return document.withMutableState(document.state.updated("customTransactions", JsonArray(updatedCustom)), nowIso)
        }

        val seedTransaction = document.seed.array("transactions")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("id") == transactionId }
            ?: error("Η κίνηση δεν είναι πλέον διαθέσιμη.")
        val overrides = document.state.obj("overrides")
        val currentOverride = overrides[transactionId] as? JsonObject ?: seedTransaction
        val updatedOverride = commonUpdates(currentOverride)
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
        require(alertThreshold in 1..100) { "Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%." }
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
            put("alertThreshold", JsonPrimitive(alertThreshold))
            put("createdAt", JsonPrimitive(existing?.string("createdAt") ?: nowIso))
            put("updatedAt", JsonPrimitive(nowIso))
        })
        val updated = budgets.toMutableList()
        if (index >= 0) updated[index] = item else updated += item
        return document.withMutableState(document.state.updated("budgets", JsonArray(updated)), nowIso)
    }
}

fun moneyToCents(value: Double): Long {
    require(value.isFinite()) { "Το ποσό δεν είναι έγκυρο." }
    return (value * 100.0).roundToLong()
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

private fun CanonicalFinanceDocument.withMutableState(
    newState: JsonObject,
    nowIso: String,
): CanonicalFinanceDocument = CanonicalFinanceDocument(
    raw
        .updated("state", newState)
        .updated("updatedAt", JsonPrimitive(nowIso)),
)
