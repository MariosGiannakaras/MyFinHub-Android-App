package app.myfinhub.android.core.data

import kotlin.math.roundToInt
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
    CREATE_CARD,
    DEACTIVATE_CARD,
    RESTORE_CARD,
    PERMANENTLY_DELETE_CARD,
    COMPLETE_CARD_SECRET_CLEANUP,
    SET_RECURRING_STATUS,
    CREATE_SCHEDULED,
    COMPLETE_SCHEDULED,
    CLOSE_SCHEDULED,
    UPSERT_SAVINGS_GOAL,
    DELETE_SAVINGS_GOAL,
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
        get() = payload.string("cardId").takeIf {
            kind in setOf(PendingMutationKind.DEACTIVATE_CARD, PendingMutationKind.RESTORE_CARD, PendingMutationKind.PERMANENTLY_DELETE_CARD, PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP)
        }

    fun asMutation(): CanonicalFinanceMutation = when (kind) {
        PendingMutationKind.UPSERT_SAVINGS_GOAL -> UpsertCanonicalSavingsGoal(
            goalId = payload.string("goalId").orEmpty(),
            name = payload.string("name").orEmpty(),
            targetAmount = payload.number("targetAmount") ?: Double.NaN,
            targetDate = payload.string("targetDate"),
            asOf = payload.string("asOf").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.DELETE_SAVINGS_GOAL -> DeleteCanonicalSavingsGoal(
            goalId = payload.string("goalId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.SET_RECURRING_STATUS -> SetCanonicalRecurringStatus(
            recurringId = payload.string("recurringId").orEmpty(),
            status = payload.string("status").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.CREATE_SCHEDULED -> CreateCanonicalScheduledTransaction(
            scheduledId = payload.string("scheduledId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
            draft = CanonicalScheduledDraft(
                kind = payload.string("kind").orEmpty(),
                dueDate = payload.string("dueDate").orEmpty(),
                amount = payload.number("amount") ?: Double.NaN,
                note = payload.string("note").orEmpty(),
                category = payload.string("category"),
                subcategory = payload.string("subcategory"),
                accountId = payload.string("accountId"),
                fromAccountId = payload.string("fromAccountId"),
                toAccountId = payload.string("toAccountId"),
            ),
        )
        PendingMutationKind.COMPLETE_SCHEDULED -> CompleteCanonicalScheduledTransaction(
            scheduledId = payload.string("scheduledId").orEmpty(),
            eventId = payload.string("eventId").orEmpty(),
            date = payload.string("date").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
            amount = payload.number("amount"),
            accountId = payload.string("accountId"),
            fromAccountId = payload.string("fromAccountId"),
            toAccountId = payload.string("toAccountId"),
        )
        PendingMutationKind.CLOSE_SCHEDULED -> CloseCanonicalScheduledTransaction(
            scheduledId = payload.string("scheduledId").orEmpty(),
            status = payload.string("status").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.APPEND_EVENT -> AppendCanonicalEvent(
            event = payload.obj("event"),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.EDIT_ACTIVITY -> EditCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            note = payload.string("note").orEmpty(),
            category = payload.string("category").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
            date = payload.string("date"),
            subcategory = if ("subcategory" in payload) payload.string("subcategory").orEmpty() else null,
        )
        PendingMutationKind.DELETE_ACTIVITY -> DeleteCanonicalActivity(
            transactionId = payload.string("transactionId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.CREATE_CARD -> CreateCanonicalCard(
    cardId = payload.string("cardId").orEmpty(),
    bankId = payload.string("bankId").orEmpty(),
    nickname = payload.string("nickname").orEmpty(),
    kind = payload.string("cardKind").orEmpty(),
    network = payload.string("network").orEmpty(),
    formFactor = payload.string("formFactor").orEmpty(),
    last4 = payload.string("last4"),
    creditLimit = payload.number("creditLimit"),
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
        PendingMutationKind.RESTORE_CARD -> RestoreCanonicalCard(
            cardId = payload.string("cardId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
        )
        PendingMutationKind.PERMANENTLY_DELETE_CARD -> PermanentlyDeleteCanonicalCard(
            cardId = payload.string("cardId").orEmpty(),
            nowIso = payload.string("nowIso").orEmpty(),
            asOf = payload.string("asOf").orEmpty(),
        )
        PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP -> CompleteCanonicalCardSecretCleanup(
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
        PendingMutationKind.UPSERT_SAVINGS_GOAL -> {
            val id = payload.string("goalId").orEmpty()
            val amount = payload.number("targetAmount")
            val name = payload.string("name").orEmpty().trim()
            val actual = document.state.array("savingsGoals")
                .mapNotNull { it as? JsonObject }.firstOrNull { it.string("id") == id }
            id.isNotBlank() && amount != null && amount.isFinite() && actual != null &&
                actual.string("name").orEmpty().trim() == name &&
                actual.number("targetAmount")?.let { moneyToCents(it) == moneyToCents(amount) } == true &&
                actual.string("targetDate") == payload.string("targetDate")
        }
        PendingMutationKind.DELETE_SAVINGS_GOAL -> {
            val id = payload.string("goalId").orEmpty()
            id.isNotBlank() && document.state.array("savingsGoals")
                .none { (it as? JsonObject)?.string("id") == id }
        }
        PendingMutationKind.SET_RECURRING_STATUS -> {
            val id = payload.string("recurringId").orEmpty()
            val expected = payload.string("status")
            val actual = document.allCanonicalRecurringItems()
                .firstOrNull { it.id == id }?.effectiveStatus
            id.isNotBlank() && expected in setOf("active", "paused", "stopped") &&
                (actual == null || actual == expected)
        }
        PendingMutationKind.CREATE_SCHEDULED -> {
            val id = payload.string("scheduledId").orEmpty()
            val row = document.state.array("scheduled")
                .filterIsInstance<JsonObject>().firstOrNull { it.string("id") == id }
            id.isNotBlank() && row != null &&
                (asMutation() as CreateCanonicalScheduledTransaction).matchesExisting(row)
        }
        PendingMutationKind.COMPLETE_SCHEDULED -> {
            val id = payload.string("scheduledId").orEmpty()
            val eventId = payload.string("eventId").orEmpty()
            val row = document.state.array("scheduled")
                .filterIsInstance<JsonObject>().firstOrNull { it.string("id")==id }
            id.isNotBlank() && eventId.isNotBlank() &&
                row?.string("status")=="completed" && row.string("completedEventId")==eventId &&
                document.state.array("events").any { (it as? JsonObject)?.string("id")==eventId }
        }
        PendingMutationKind.CLOSE_SCHEDULED -> {
            val id = payload.string("scheduledId").orEmpty()
            val expected = payload.string("status")
            id.isNotBlank() && expected in setOf("skipped","cancelled") &&
                document.state.array("scheduled").filterIsInstance<JsonObject>()
                    .any { it.string("id")==id && it.string("status")==expected }
        }
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
            val expectedDate = payload.string("date")
            val subcategoryWasSpecified = "subcategory" in payload
            val expectedSubcategory = payload.string("subcategory").orEmpty().trim()
            val transaction = effectiveTransaction(document, id)
            id.isNotBlank() && (
                transaction == null ||
                    (transaction.string("note").orEmpty().trim() == expectedNote &&
                        transaction.string("category").orEmpty().trim() == expectedCategory &&
                        (expectedDate == null || transaction.string("date").orEmpty() == expectedDate) &&
                        (!subcategoryWasSpecified || transaction.string("subcategory").orEmpty().trim() == expectedSubcategory))
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
            val normalizedExpectedThreshold = expectedThreshold?.coerceIn(50, 99)
            document.state.array("budgets")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("month") == month && it.string("scope") == "overall" }
                ?.let { budget ->
                    val actualAmount = budget.number("amount")
                    val actualThreshold = budget.number("alertThreshold")?.let { raw ->
                        if (raw <= 1.0) (raw * 100.0).roundToInt() else raw.roundToInt()
                    }
                    expectedAmount != null && actualAmount != null &&
                        moneyToCents(actualAmount) == moneyToCents(expectedAmount) &&
                        // A previously committed Android percentage (1–100), or the newer
                        // Web canonical fraction (50–99%), satisfies the SAME queued intent.
                        // Do not replay an ambiguous already-accepted historical budget write.
                        (actualThreshold == expectedThreshold ||
                            actualThreshold == normalizedExpectedThreshold)
                } == true
        }
        PendingMutationKind.CREATE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val card = document.state.array("cards")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("id") == cardId }
            cardId.isNotBlank() && card != null &&
                card.string("bankId") == payload.string("bankId") &&
                card.string("nickname") == payload.string("nickname") &&
                card.string("kind") == payload.string("cardKind") &&
                // An archived profile still proves that its earlier create was accepted.
                // Do not resurrect archived server state just to replay a settled create.
                card.string("network") == payload.string("network")
        }
        PendingMutationKind.DEACTIVATE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val card = document.state.array("cards")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("id") == cardId }
            cardId.isNotBlank() && (card == null || card.bool("active") == false)
        }
        PendingMutationKind.RESTORE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val card = document.state.array("cards")
                .mapNotNull { it as? JsonObject }
                .firstOrNull { it.string("id") == cardId }
            // A permanently deleted profile cannot safely be resurrected. Treat the
            // stale restore as resolved rather than replaying against a missing ID.
            cardId.isNotBlank() && (card == null || card.bool("active") != false)
        }
        PendingMutationKind.PERMANENTLY_DELETE_CARD -> {
            val cardId = payload.string("cardId").orEmpty()
            val hasCard = document.state.array("cards")
                .any { (it as? JsonObject)?.string("id") == cardId }
            // A removed profile is resolved even if server-side secret cleanup already
            // cleared the pending marker. Never recreate a deleted record on replay.
            cardId.isNotBlank() && !hasCard
        }
        PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP -> {
            val id = payload.string("cardId").orEmpty()
            id.isNotBlank() &&
                document.state.array("cards").none { (it as? JsonObject)?.string("id") == id } &&
                document.state.array("pendingCardSecretDeletes").none { (it as? JsonPrimitive)?.content == id }
        }
    }

    companion object {
        fun fromMutation(
            mutation: CanonicalFinanceMutation,
            intentId: String,
            syncState: PendingMutationSyncState = PendingMutationSyncState.NEVER_SENT,
        ): PendingCanonicalMutationIntent = when (mutation) {
            is UpsertCanonicalSavingsGoal -> PendingCanonicalMutationIntent(
                intentId = intentId, kind = PendingMutationKind.UPSERT_SAVINGS_GOAL,
                payload = JsonObject(buildMap {
                    put("goalId", JsonPrimitive(mutation.goalId))
                    put("name", JsonPrimitive(mutation.name))
                    put("targetAmount", JsonPrimitive(mutation.targetAmount))
                    mutation.targetDate?.let { put("targetDate", JsonPrimitive(it)) }
                    put("asOf", JsonPrimitive(mutation.asOf))
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                }),
                syncState = syncState,
            )
            is DeleteCanonicalSavingsGoal -> PendingCanonicalMutationIntent(
                intentId = intentId, kind = PendingMutationKind.DELETE_SAVINGS_GOAL,
                payload = JsonObject(mapOf(
                    "goalId" to JsonPrimitive(mutation.goalId),
                    "nowIso" to JsonPrimitive(mutation.nowIso),
                )),
                syncState = syncState,
            )
            is SetCanonicalRecurringStatus -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.SET_RECURRING_STATUS,
                payload = JsonObject(mapOf(
                    "recurringId" to JsonPrimitive(mutation.recurringId),
                    "status" to JsonPrimitive(mutation.status),
                    "nowIso" to JsonPrimitive(mutation.nowIso),
                )),
                syncState = syncState,
            )
            is CreateCanonicalScheduledTransaction -> PendingCanonicalMutationIntent(
                intentId = intentId, kind = PendingMutationKind.CREATE_SCHEDULED,
                payload = JsonObject(buildMap {
                    put("scheduledId", JsonPrimitive(mutation.scheduledId))
                    put("kind", JsonPrimitive(mutation.draft.kind))
                    put("dueDate", JsonPrimitive(mutation.draft.dueDate))
                    put("amount", JsonPrimitive(mutation.draft.amount))
                    put("note", JsonPrimitive(mutation.draft.note))
                    mutation.draft.category?.let { put("category", JsonPrimitive(it)) }
                    mutation.draft.subcategory?.let { put("subcategory", JsonPrimitive(it)) }
                    mutation.draft.accountId?.let { put("accountId", JsonPrimitive(it)) }
                    mutation.draft.fromAccountId?.let { put("fromAccountId", JsonPrimitive(it)) }
                    mutation.draft.toAccountId?.let { put("toAccountId", JsonPrimitive(it)) }
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                }), syncState = syncState,
            )
            is CompleteCanonicalScheduledTransaction -> PendingCanonicalMutationIntent(
                intentId = intentId, kind = PendingMutationKind.COMPLETE_SCHEDULED,
                payload = JsonObject(buildMap {
                    put("scheduledId", JsonPrimitive(mutation.scheduledId))
                    put("eventId", JsonPrimitive(mutation.eventId))
                    put("date", JsonPrimitive(mutation.date))
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                    mutation.amount?.let { put("amount", JsonPrimitive(it)) }
                    mutation.accountId?.let { put("accountId", JsonPrimitive(it)) }
                    mutation.fromAccountId?.let { put("fromAccountId", JsonPrimitive(it)) }
                    mutation.toAccountId?.let { put("toAccountId", JsonPrimitive(it)) }
                }), syncState = syncState,
            )
            is CloseCanonicalScheduledTransaction -> PendingCanonicalMutationIntent(
                intentId = intentId, kind = PendingMutationKind.CLOSE_SCHEDULED,
                payload = JsonObject(mapOf(
                    "scheduledId" to JsonPrimitive(mutation.scheduledId),
                    "status" to JsonPrimitive(mutation.status),
                    "nowIso" to JsonPrimitive(mutation.nowIso),
                )), syncState = syncState,
            )
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
                payload = JsonObject(buildMap {
                    put("transactionId", JsonPrimitive(mutation.transactionId))
                    put("note", JsonPrimitive(mutation.note))
                    put("category", JsonPrimitive(mutation.category))
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                    mutation.date?.let { put("date", JsonPrimitive(it)) }
                    mutation.subcategory?.let { put("subcategory", JsonPrimitive(it)) }
                }),
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
            is CreateCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.CREATE_CARD,
                payload = JsonObject(buildMap {
                    put("cardId", JsonPrimitive(mutation.cardId))
                    put("bankId", JsonPrimitive(mutation.bankId))
                    put("nickname", JsonPrimitive(mutation.nickname))
                    put("cardKind", JsonPrimitive(mutation.kind))
                    put("network", JsonPrimitive(mutation.network))
                    put("formFactor", JsonPrimitive(mutation.formFactor))
                    mutation.last4?.let { put("last4", JsonPrimitive(it)) }
                    mutation.creditLimit?.let { put("creditLimit", JsonPrimitive(it)) }
                    put("nowIso", JsonPrimitive(mutation.nowIso))
                }),
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
            is RestoreCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.RESTORE_CARD,
                payload = JsonObject(
                    mapOf(
                        "cardId" to JsonPrimitive(mutation.cardId),
                        "nowIso" to JsonPrimitive(mutation.nowIso),
                    ),
                ),
                syncState = syncState,
            )
            is PermanentlyDeleteCanonicalCard -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.PERMANENTLY_DELETE_CARD,
                payload = JsonObject(mapOf(
                    "cardId" to JsonPrimitive(mutation.cardId),
                    "nowIso" to JsonPrimitive(mutation.nowIso),
                    "asOf" to JsonPrimitive(mutation.asOf),
                )),
                syncState = syncState,
            )
            is CompleteCanonicalCardSecretCleanup -> PendingCanonicalMutationIntent(
                intentId = intentId,
                kind = PendingMutationKind.COMPLETE_CARD_SECRET_CLEANUP,
                payload = JsonObject(mapOf(
                    "cardId" to JsonPrimitive(mutation.cardId),
                    "nowIso" to JsonPrimitive(mutation.nowIso),
                )),
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
        // Deactivation is Web-style archival: keep a preceding unsent creation
        // so an offline create -> archive -> restore can replay against the server.
        // Deduplicate only a *consecutive* archive for this ID: if a restore came
        // between two archives, the later archive must not disappear.
        val lastForCard = current.lastOrNull {
            it.affectedCardId == cardId ||
                (it.kind == PendingMutationKind.CREATE_CARD &&
                    it.payload.string("cardId") == cardId)
        }
        if (lastForCard?.kind == PendingMutationKind.DEACTIVATE_CARD &&
            lastForCard.syncState == PendingMutationSyncState.NEVER_SENT
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
fun undoLatestNeverSentPendingMutation(
    pending: List<PendingCanonicalMutationIntent>,
): List<PendingCanonicalMutationIntent> =
    if (pending.lastOrNull()?.syncState == PendingMutationSyncState.NEVER_SENT) pending.dropLast(1) else pending

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
