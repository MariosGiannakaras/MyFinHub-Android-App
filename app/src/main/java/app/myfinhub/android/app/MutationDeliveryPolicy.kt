package app.myfinhub.android.app

import app.myfinhub.android.core.network.ApiFailureKind

/**
 * Owner-approved mutation delivery policy. Normal connected writes stay on the direct server path;
 * the durable local queue and Undo semantics exist for offline work or ordered reconciliation.
 */
/** Encrypted last-good snapshots remain readable for non-auth HTTP 400 and 5xx;
 * they never grant permission to resume server writes without a successful fresh GET.
 */
internal fun canFallbackToEncryptedFinanceCache(
    failure: ApiFailureKind,
    hasCache: Boolean,
): Boolean = hasCache &&
    failure != ApiFailureKind.AUTH_REQUIRED && failure != ApiFailureKind.MFA_REQUIRED

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
