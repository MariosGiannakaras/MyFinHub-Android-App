package app.myfinhub.android.app

import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.PendingCanonicalMutationIntent
import app.myfinhub.android.core.data.PendingMutationKind
import app.myfinhub.android.core.data.PendingMutationSyncState
import app.myfinhub.android.feature.activity.ActivityItem
import java.time.LocalDate

/**
 * Adds durable pending semantics to the optimistic canonical product projection.
 *
 * The canonical document remains the accounting source of truth. This layer only restores
 * destructive tombstones that the optimistic mutation necessarily removes and annotates
 * canonical write targets until a fresh server document confirms them.
 */
internal fun projectPendingUi(
    projection: CanonicalProductProjection,
    serverDocument: CanonicalFinanceDocument?,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): CanonicalProductProjection {
    val retainedPlanMessage = projection.planState.message
        ?.takeUnless { it.startsWith("Αλλαγή budget ·") }
    if (pending.isEmpty()) {
        return if (retainedPlanMessage == projection.planState.message) {
            projection
        } else {
            projection.copy(planState = projection.planState.copy(message = retainedPlanMessage))
        }
    }

    val pendingTransactionIds = pending
        .mapNotNull(PendingCanonicalMutationIntent::affectedTransactionId)
        .filter(String::isNotBlank)
        .toSet()
    val markedActivity = projection.activityState.items.map { item ->
        if (item.id in pendingTransactionIds) item.copy(pendingSync = true) else item
    }
    val tombstones = serverDocument
        ?.let { pendingDeletionTombstones(it, pending, today) }
        .orEmpty()
    val visibleIds = markedActivity.map(ActivityItem::id).toSet()
    val activityItems = tombstones.asReversed().filterNot { it.id in visibleIds } + markedActivity

    val cardMessage = serverDocument?.let { pendingCardDeletionMessage(it, pending, today) }
    val budgetMessage = pending.lastOrNull { it.kind == PendingMutationKind.UPSERT_OVERALL_BUDGET }
        ?.let { intent -> "Αλλαγή budget · ${intent.syncState.pendingStatusLabel()}" }

    return projection.copy(
        activityState = projection.activityState.copy(items = activityItems),
        moneyState = projection.moneyState.copy(
            frontendMessage = cardMessage ?: projection.moneyState.frontendMessage,
        ),
        planState = projection.planState.copy(
            message = budgetMessage ?: retainedPlanMessage,
        ),
    )
}

private fun pendingDeletionTombstones(
    serverDocument: CanonicalFinanceDocument,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): List<ActivityItem> {
    var replayDocument = serverDocument
    val tombstones = mutableListOf<ActivityItem>()

    for (intent in pending) {
        if (intent.kind == PendingMutationKind.DELETE_ACTIVITY) {
            val transactionId = intent.affectedTransactionId.orEmpty()
            val source = runCatching {
                projectCanonicalProduct(replayDocument, today)
                    .activityState.items
                    .firstOrNull { it.id == transactionId }
            }.getOrNull()
            if (source != null) {
                val deletionStatus = when (intent.syncState) {
                    PendingMutationSyncState.NEVER_SENT -> "Εκκρεμεί διαγραφή"
                    PendingMutationSyncState.NEEDS_REVIEW -> "Αναμονή επιβεβαίωσης διαγραφής από τον server"
                }
                tombstones.removeAll { it.id == transactionId }
                tombstones += source.copy(
                    subtitle = listOf(deletionStatus, source.subtitle)
                        .filter(String::isNotBlank)
                        .joinToString(" · "),
                    pendingSync = true,
                )
            }
        }

        val next = runCatching { intent.asMutation().apply(replayDocument) }.getOrNull() ?: break
        replayDocument = next
    }

    return tombstones
}

private fun pendingCardDeletionMessage(
    serverDocument: CanonicalFinanceDocument,
    pending: List<PendingCanonicalMutationIntent>,
    today: LocalDate,
): String? {
    val cardIntents = pending
        .filter { it.kind == PendingMutationKind.DEACTIVATE_CARD }
        .distinctBy(PendingCanonicalMutationIntent::affectedCardId)
    if (cardIntents.isEmpty()) return null

    val serverCards = runCatching {
        projectCanonicalProduct(serverDocument, today).moneyState.cards.associateBy { it.id }
    }.getOrNull() ?: return null
    val lines = cardIntents.mapNotNull { intent ->
        val cardId = intent.affectedCardId ?: return@mapNotNull null
        val card = serverCards[cardId] ?: return@mapNotNull null
        val last4 = card.last4.takeIf(String::isNotBlank)?.let { " ••••$it" }.orEmpty()
        "${card.nickname}$last4 · Εκκρεμεί διαγραφή · ${intent.syncState.pendingStatusLabel()}"
    }
    if (lines.isEmpty()) return null

    return "Εκκρεμείς διαγραφές καρτών:\n${lines.joinToString("\n")}"
}

private fun PendingMutationSyncState.pendingStatusLabel(): String = when (this) {
    PendingMutationSyncState.NEVER_SENT -> "Προς συγχρονισμό"
    PendingMutationSyncState.NEEDS_REVIEW -> "Αναμονή επιβεβαίωσης από τον server"
}
