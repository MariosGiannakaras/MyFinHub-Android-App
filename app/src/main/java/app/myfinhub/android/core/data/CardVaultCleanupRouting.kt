package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * A card archive/restore and a secret-cleanup acknowledgement must NEVER trigger a vault purge.
 * Return IDs only when a permanent deletion intent was committed by the server, its profile
 * is absent, and a durable server-side pending secret marker proves that cleanup is owed.
 *
 * The event itself is non-durable. The marker remains the source of truth until both
 * authenticated server vault and legacy local vault cleanup have been acknowledged.
 */
fun committedPermanentCardCleanupIds(
    committed: List<PendingCanonicalMutationIntent>,
    confirmedServerDocument: CanonicalFinanceDocument,
): List<String> {
    val retainedIds = confirmedServerDocument.state.array("cards")
        .mapNotNull { (it as? JsonObject)?.string("id") }.toSet()
    val pendingSecretIds = confirmedServerDocument.state.array("pendingCardSecretDeletes")
        .mapNotNull { (it as? JsonPrimitive)?.content }.toSet()
    return committed.asSequence()
        .filter { it.kind == PendingMutationKind.PERMANENTLY_DELETE_CARD }
        .mapNotNull(PendingCanonicalMutationIntent::affectedCardId)
        .filter { it !in retainedIds && it in pendingSecretIds }
        .distinct()
        .toList()
}
