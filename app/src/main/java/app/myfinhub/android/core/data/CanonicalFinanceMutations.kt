package app.myfinhub.android.core.data

import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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
    val parts: List<CanonicalSplitDraft> = emptyList(),
)

fun createCanonicalEventMutation(
    document: CanonicalFinanceDocument,
    draft: CanonicalEventDraft,
    eventId: String,
    nowIso: String,
): CanonicalFinanceMutation {
    require(eventId.isNotBlank()) { "Απαιτείται αναγνωριστικό κίνησης." }
    require(draft.date.isNotBlank()) { "Απαιτείται ημερομηνία." }
    require(draft.note.isNotBlank()) { "Πρόσθεσε μια σύντομη περιγραφή." }
    val amountCents = moneyToCents(draft.amount)
    require(amountCents > 0) { "Το ποσό πρέπει να είναι μεγαλύτερο από μηδέν." }
    val amount = centsToMoney(amountCents)
    val eligibleAccounts = document.canonicalAccounts().filter { it.kind != "credit" }.map { it.id }.toSet()

    val legs = mutableListOf<JsonObject>()
    var creditDelta = 0.0
    val normalizedParts = mutableListOf<JsonObject>()

    when (draft.kind) {
        "expense" -> {
            require(draft.accountId in eligibleAccounts) { "Διάλεξε υπαρκτό λογαριασμό πληρωμής." }
            legs += ledgerLeg(draft.accountId!!, -amount)
        }
        "transfer" -> {
            require(draft.fromAccountId in eligibleAccounts) { "Διάλεξε υπαρκτό λογαριασμό προέλευσης." }
            require(draft.toAccountId in eligibleAccounts) { "Διάλεξε υπαρκτό λογαριασμό προορισμού." }
            require(draft.fromAccountId != draft.toAccountId) { "Οι λογαριασμοί μεταφοράς πρέπει να είναι διαφορετικοί." }
            legs += ledgerLeg(draft.fromAccountId!!, -amount)
            legs += ledgerLeg(draft.toAccountId!!, amount)
        }
        "card_payment" -> {
            require(draft.fromAccountId in eligibleAccounts) { "Διάλεξε υπαρκτό λογαριασμό πληρωμής." }
            require(!draft.cardId.isNullOrBlank()) { "Διάλεξε πιστωτική κάρτα." }
            require(document.canonicalCards().any { it.id == draft.cardId && it.active }) { "Η κάρτα δεν είναι ενεργή." }
            legs += ledgerLeg(draft.fromAccountId!!, -amount)
            legs += ledgerLeg(CREDIT_ACCOUNT_ID, amount)
            creditDelta = amount
        }
        "split" -> {
            require(draft.accountId in eligibleAccounts) { "Διάλεξε υπαρκτό λογαριασμό πληρωμής." }
            require(draft.parts.size >= 2) { "Ο διαχωρισμός χρειάζεται τουλάχιστον δύο μέρη." }
            val normalized = draft.parts.map { part ->
                val cents = moneyToCents(part.amount)
                require(cents > 0) { "Κάθε μέρος του διαχωρισμού πρέπει να έχει θετικό ποσό." }
                CanonicalSplitDraft(
                    id = part.id,
                    label = part.label.trim(),
                    category = part.category.trim().ifBlank { "Άλλο" },
                    subcategory = part.subcategory?.trim()?.takeIf(String::isNotBlank),
                    amount = centsToMoney(cents),
                )
            }
            require(normalized.sumOf { moneyToCents(it.amount) } == amountCents) {
                "Τα επιμέρους ποσά πρέπει να ισούνται ακριβώς με το σύνολο."
            }
            normalizedParts += normalized.map { part ->
                JsonObject(buildMap {
                    put("id", JsonPrimitive(part.id))
                    put("label", JsonPrimitive(part.label))
                    put("category", JsonPrimitive(part.category))
                    part.subcategory?.let { put("subcategory", JsonPrimitive(it)) }
                    put("amount", JsonPrimitive(part.amount))
                    put("kind", JsonPrimitive("expense"))
                })
            }
            legs += ledgerLeg(draft.accountId!!, -amount)
        }
        else -> error("Unsupported Android Quick Entry event kind: ${draft.kind}")
    }

    val event = JsonObject(buildMap {
        put("id", JsonPrimitive(eventId))
        put("date", JsonPrimitive(draft.date))
        put("kind", JsonPrimitive(draft.kind))
        put("amount", JsonPrimitive(amount))
        put("note", JsonPrimitive(draft.note.trim()))
        draft.category?.trim()?.takeIf(String::isNotBlank)?.let { put("category", JsonPrimitive(it)) }
        draft.accountId?.let { put("accountId", JsonPrimitive(it)) }
        draft.fromAccountId?.let { put("fromAccountId", JsonPrimitive(it)) }
        draft.toAccountId?.let { put("toAccountId", JsonPrimitive(it)) }
        draft.cardId?.let { put("cardId", JsonPrimitive(it)) }
        put("legs", JsonArray(legs))
        if (normalizedParts.isNotEmpty()) put("parts", JsonArray(normalizedParts))
        put("savingAmount", JsonPrimitive(0.0))
        put("receivableDelta", JsonPrimitive(0.0))
        put("creditDelta", JsonPrimitive(creditDelta))
        put("source", JsonPrimitive("user"))
        put("createdAt", JsonPrimitive(nowIso))
        put("updatedAt", JsonPrimitive(nowIso))
    })

    return AppendCanonicalEvent(event = event, nowIso = nowIso)
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
        return document.withMutableState(
            document.state.updated("events", JsonArray(events + event)),
            nowIso,
        )
    }
}

data class EditCanonicalActivity(
    val transactionId: String,
    val note: String,
    val category: String,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση αλλαγών κίνησης"

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val normalizedNote = note.trim()
        require(normalizedNote.isNotBlank()) { "Η σημείωση δεν μπορεί να είναι κενή." }
        val normalizedCategory = category.trim()

        val events = document.state.array("events")
        val eventIndex = events.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (eventIndex >= 0) {
            val current = events[eventIndex] as JsonObject
            val updatedEvent = current
                .updated("note", JsonPrimitive(normalizedNote))
                .updated("category", normalizedCategory.takeIf(String::isNotBlank)?.let(::JsonPrimitive))
                .updated("updatedAt", JsonPrimitive(nowIso))
            val updatedEvents = events.toMutableList().apply { this[eventIndex] = updatedEvent }
            return document.withMutableState(document.state.updated("events", JsonArray(updatedEvents)), nowIso)
        }

        val custom = document.state.array("customTransactions")
        val customIndex = custom.indexOfFirst { (it as? JsonObject)?.string("id") == transactionId }
        if (customIndex >= 0) {
            val current = custom[customIndex] as JsonObject
            val updatedTx = current
                .updated("note", JsonPrimitive(normalizedNote))
                .updated("category", normalizedCategory.takeIf(String::isNotBlank)?.let(::JsonPrimitive))
            val updatedCustom = custom.toMutableList().apply { this[customIndex] = updatedTx }
            return document.withMutableState(document.state.updated("customTransactions", JsonArray(updatedCustom)), nowIso)
        }

        val seedTransaction = document.seed.array("transactions")
            .mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("id") == transactionId }
            ?: error("Η κίνηση δεν είναι πλέον διαθέσιμη.")
        val overrides = document.state.obj("overrides")
        val currentOverride = overrides[transactionId] as? JsonObject ?: seedTransaction
        val updatedOverride = currentOverride
            .updated("note", JsonPrimitive(normalizedNote))
            .updated("category", normalizedCategory.takeIf(String::isNotBlank)?.let(::JsonPrimitive))
        return document.withMutableState(
            document.state.updated("overrides", overrides.updated(transactionId, updatedOverride)),
            nowIso,
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

private fun ledgerLeg(accountId: String, amount: Double): JsonObject = JsonObject(
    mapOf(
        "accountId" to JsonPrimitive(accountId),
        "amount" to JsonPrimitive(amount),
    ),
)

private fun CanonicalFinanceDocument.withMutableState(
    newState: JsonObject,
    nowIso: String,
): CanonicalFinanceDocument = CanonicalFinanceDocument(
    raw
        .updated("state", newState)
        .updated("updatedAt", JsonPrimitive(nowIso)),
)
