package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Durable, device-local representation of a user-approved canonical finance mutation.
 *
 * The intent contains only the stable parameters needed to reconstruct the mutation. It is kept
 * separate from the last server-accepted document so reconnect can always start from a fresh server
 * revision before replaying local work.
 */
enum class PendingMutationSyncState {
    NEVER_SENT,
    NEEDS_REVIEW,
}

enum class PendingMutationKind {
    APPEND_EVENT,
    EDIT_ACTIVITY,
    DELETE_ACTIVITY,
    UPSERT_OVERALL_BUDGET,
    DEACTIVATE_CARD,
}

data class PendingCanonicalMutationIntent(
    val intentId: String,
    val kind: PendingMutationKind,
    val payload: JsonObject,
    val syncState: PendingMutationSyncState = PendingMutationSyncState.NEVER_SENT,
) {
    init {
        require(intentId.isNotBlank()) { "Pending mutation requires a stable intent id." }
    }

    val appendedEventId: String?
        get() = if (kind == PendingMutationKind.APPEND_EVENT) payload.obj("event").string("id") else null

    val affectedTransactionId: String?
        get() = when (kind) {
            PendingMutationKind.APPEND_EVENT -> appendedEventId
            PendingMutationKind.EDIT_ACTIVITY,
            PendingMutationKind.DELETE_ACTIVITY -> payload.string("transactionId")
            else -> null
        }

    val affectedCardId: String?
        get() = payload.string("cardId").takeIf { kind == PendingMutationKind.DEACTIVATE_CARD }

    fun asMutation(): CanonicalFinanceMutation = when (kind) {
        PendingMutationKind.APPEND_EVENT -> AppendCanonicalEvent(
            event = payload.obj("event"),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.EDIT_ACTIVITY -> EditCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            note = payload.string("note").orEmpty(),
            category = payload.string("category").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.DELETE_ACTIVITY -> DeleteCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.UPSERT_OVERALL_BUDGET -> UpsertOverallBudget(
            month = payload.string("month").orEmpty(),
            amount = payload.number("amount") ?: Double.NaN,
            alertThreshold = payload.number("alertThreshold")?.toInt() ?: 0,
            budgetId = payload.string("budgetId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.DEACTIVATE_CARD -> DeactivateCanonicalCard(
            cardId = payload.string("cardId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
    }

    /**
     * Returns true when a fresh canonical server document already reflects this intent. A stale
     * edit/deactivation whose target was removed elsewhere is also resolved here: there is no safe
     * target left to mutate, so it must not poison persistence or be replayed against a missing id.
     */
    fun isSatisfiedBy(document: CanonicalFinanceDocument): Boolean = when (kind) {
        PendingMutationKind.APPEND_EVENT -> {
            val eventId = appendedEventId.orEmpty()
            eventId.isNotBlank() && document.state.array("events").any {
                (it as? JsonObject)?.string("id") == eventId
            }
        }
        PendingMutationKind.EDIT_ACTIVITY -> {
            val id = payload.string("transactionId").orEmpty()
            val expectedNote = payload.string("note").orEmpty().trim()
            val expectedCategory = payload.string("category").orEmpty().trim()
            val transaction = effectiveTransaction(document, id)
            id.isNotBlank() && (
                transaction == null ||
                    (transaction.string("note").orEmpty().trim() == expectedNote &&
                        transaction.string("category").orEmpty().trim() == expectedCategory)
            )
        }
        PendingMutationKind.DELETE_ACTIVITY -> {
            val id = payload.string("transactionId").orEmpty()
            id.isNotBlank() && effectiveTransaction(document, id) == null
        }
        PendingMutationKind.UPSERT_OVERALL_BUDGET -> {
            val month = payload.string("month").orEmpty()
            val expectedAmount = payload.number("amount")
            val expectedThreshold = payload.number("alertThreshold")?.toInt()
            document.state.array("budgets")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("month") == month && it.string("scope") == "overall" }
                ?.let { budget ->
                    val actualAmount = budget.number("amount")
                    val actualThreshold = budget.number("alertThreshold")?.toInt()
                    expectedAmount != null && actualAmount != null &&
                        moneyToCents(actualAmount) == moneyToCents(expectedAmount) &&
                        actualThreshold == expectedThreshold
                } == true
        }
        PendingMutationKind.DEACTIVATE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val card = document.state.array("cards")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("id") == cardId }
            cardId.isNotBlank() && (card == null || card.bool("active") == false)
        }
    }

    companion object {
        fun fromMutation(
            mutation: CanonicalFinanceMutation,
            intentId: String,
            syncState: PendingMutationSyncState = PendingMutationSyncState.NEVER_SENT,
        ): PendingCanonicalMutationIntent = when (mutation) {
            is AppendCanonicalEvent -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.APPEND_EVENT,
                payload = JsonObject(
                    mapOf(
                        "event" to mutation.event,
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
            is EditCanonicalActivity -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.EDIT_ACTIVITY,
                payload = JsonObject(
                    mapOf(
                        "transactionId" to JsonPrimitive(mutation.transactionId),
                        "note" to JsonPrimitive(mutation.note),
                        "category" to JsonPrimitive(mutation.category),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
            is DeleteCanonicalActivity -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.DELETE_ACTIVITY,
                payload = JsonObject(
                    mapOf(
                        "transactionId" to JsonPrimitive(mutation.transactionId),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
            is UpsertOverallBudget -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.UPSERT_OVERALL_BUDGET,
                payload = JsonObject(
                    mapOf(
                        "month" to JsonPrimitive(mutation.month),
                        "amount" to JsonPrimitive(mutation.amount),
                        "alertThreshold" to JsonPrimitive(mutation.alertThreshold),
                        "budgetId" to JsonPrimitive(mutation.budgetId),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
            is DeactivateCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.DEACTIVATE_CARD,
                payload = JsonObject(
                    mapOf(
                        "cardId" to JsonPrimitive(mutation.cardId),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
        }
    }
}

/**
 * Reduces redundant NEVER_SENT work while never rewriting an ambiguous NEEDS_REVIEW intent.
 */
fun compactPendingMutationIntents(
    current: List<PendingCanonicalMutationIntent>,
    next: PendingCanonicalMutationIntent,
): List<PendingCanonicalMutationIntent> {
    if (next.syncState != PendingMutationSyncState.NEVER_SENT) return current + next

    val transactionId = next.affectedTransactionId
    if (next.kind == PendingMutationKind.DELETE_ACTIVITY && !transactionId.isNullOrBlank()) {
        val unsentAppend = current.lastOrNull {
            it.kind == PendingMutationKind.APPEND_EVENT &&
                it.appendedEventId == transactionId &&
                it.syncState == PendingMutationSyncState.NEVER_SENT
        }
        if (unsentAppend != null) {
            // A locally-created event that was never sent can be cancelled completely. Keep any
            // ambiguous intents untouched; they may already exist on the server.
            return current.filterNot {
                it.syncState == PendingMutationSyncState.NEVER_SENT &&
                    it.affectedTransactionId == transactionId
            }
        }
        return current.filterNot {
            it.kind == PendingMutationKind.EDIT_ACTIVITY &&
                it.affectedTransactionId == transactionId &&
                it.syncState == PendingMutationSyncState.NEVER_SENT
        } + next
    }

    if (next.kind == PendingMutationKind.EDIT_ACTIVITY && !transactionId.isNullOrBlank()) {
        return current.filterNot {
            it.kind == PendingMutationKind.EDIT_ACTIVITY &&
                it.affectedTransactionId == transactionId &&
                it.syncState == PendingMutationSyncState.NEVER_SENT
        } + next
    }

    if (next.kind == PendingMutationKind.UPSERT_OVERALL_BUDGET) {
        val month = next.payload.string("month")
        return current.filterNot {
            it.kind == PendingMutationKind.UPSERT_OVERALL_BUDGET &&
                it.payload.string("month") == month &&
                it.syncState == PendingMutationSyncState.NEVER_SENT
        } + next
    }

    if (next.kind == PendingMutationKind.DEACTIVATE_CARD) {
        val cardId = next.affectedCardId
        if (current.any {
                it.kind == PendingMutationKind.DEACTIVATE_CARD &&
                    it.affectedCardId == cardId &&
                    it.syncState == PendingMutationSyncState.NEVER_SENT
            }
        ) return current
    }

    return current + next
}

/**
 * Reconciliation is intentionally ordered. Only a contiguous satisfied prefix can be removed.
 * A later intent may look satisfied only because an earlier unresolved intent has not happened on
 * the server yet (for example ambiguous create -> offline delete). Removing such a later intent
 * independently would break the user's causal sequence and could resurrect local data.
 */
fun reconcileSatisfiedPendingMutations(
    serverDocument: CanonicalFinanceDocument,
    pending: List<PendingCanonicalMutationIntent>,
): List<PendingCanonicalMutationIntent> = pending.dropWhile { it.isSatisfiedBy(serverDocument) }

private fun effectiveTransaction(document: CanonicalFinanceDocument, transactionId: String): JsonObject? {
    if (transactionId.isBlank()) return null

    document.state.array("events")
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("id") == transactionId }
        ?.let { return it }

    document.state.array("customTransactions")
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("id") == transactionId }
        ?.let { return it }

    val deleted = when (val raw = document.state["deleted"]) {
        is kotlinx.serialization.json.JsonArray -> raw.mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
        is JsonObject -> raw.filterValues { (it as? JsonPrimitive)?.content == "true" }.keys
        else -> emptySet()
    }
    if (transactionId in deleted) return null

    val seed = document.seed.array("transactions")
        .mapNotNull { it as? JsonObject }
        .firstOrNull { it.string("id") == transactionId }
        ?: return null
    return document.state.obj("overrides")[transactionId] as? JsonObject ?: seed
}
