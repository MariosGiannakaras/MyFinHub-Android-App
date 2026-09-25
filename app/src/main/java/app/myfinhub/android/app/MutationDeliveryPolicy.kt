package app.myfinhub.android.app

/**
 * Owner-approved mutation delivery policy. Normal connected writes stay on the direct server path;
 * the durable local queue and Undo semantics exist for offline work or ordered reconciliation.
 */
internal enum class MutationDeliveryMode {
    IMMEDIATE_SERVER,
    DURABLE_OFFLINE_QUEUE,
    RECONCILE_PENDING_FIRST,
}

internal fun mutationDeliveryMode(
    serverAvailable: Boolean,
    repositoryReady: Boolean,
    hasPendingMutations: Boolean,
): MutationDeliveryMode = when {
    !serverAvailable -> MutationDeliveryMode.DURABLE_OFFLINE_QUEUE
    hasPendingMutations || !repositoryReady -> MutationDeliveryMode.RECONCILE_PENDING_FIRST
    else -> MutationDeliveryMode.IMMEDIATE_SERVER
}
