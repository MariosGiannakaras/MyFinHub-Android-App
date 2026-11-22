package app.myfinhub.android.core.data

/**
 * Reconstruct installed offline work without silently discarding user-approved changes.
 *
 * During v1 -> generic migration the same logical append may be present in both formats.
 * A matching legacy copy is safe to coalesce only when kind AND payload match; the more
 * conservative NEEDS_REVIEW state always wins so an ambiguous write is never retried.
 * Duplicates within a single storage format are invalid even when their payloads match.
 */
internal fun mergeRestoredPendingMutations(
    current: List<PendingCanonicalMutationIntent>,
    legacy: List<PendingCanonicalMutationIntent>,
): List<PendingCanonicalMutationIntent> {
    check(current.map { it.intentId }.distinct().size == current.size) {
        "Encrypted finance cache contains duplicate current pending mutation ids."
    }
    check(legacy.map { it.intentId }.distinct().size == legacy.size) {
        "Encrypted finance cache contains duplicate legacy pending mutation ids."
    }

    val currentById = current.associateBy(PendingCanonicalMutationIntent::intentId)
    val legacyById = legacy.associateBy(PendingCanonicalMutationIntent::intentId)
    val merged = current.map { present ->
        val previous = legacyById[present.intentId] ?: return@map present
        check(present.kind == previous.kind && present.payload == previous.payload) {
            "Encrypted finance cache contains colliding pending mutation ids with different work."
        }
        if (present.syncState == PendingMutationSyncState.NEEDS_REVIEW ||
            previous.syncState == PendingMutationSyncState.NEEDS_REVIEW
        ) present.copy(syncState = PendingMutationSyncState.NEEDS_REVIEW) else present
    }
    return merged + legacy.filter { it.intentId !in currentById }
}
