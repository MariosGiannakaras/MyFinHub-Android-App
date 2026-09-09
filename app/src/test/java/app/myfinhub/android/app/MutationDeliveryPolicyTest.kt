package app.myfinhub.android.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MutationDeliveryPolicyTest {
    @Test
    fun connectedReadyState_usesImmediateServerPath() {
        assertEquals(
            MutationDeliveryMode.IMMEDIATE_SERVER,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = true, hasPendingMutations = false),
        )
    }

    @Test
    fun offlineState_usesDurableQueueWithUndoSemantics() {
        assertEquals(
            MutationDeliveryMode.DURABLE_OFFLINE_QUEUE,
            mutationDeliveryMode(serverAvailable = false, repositoryReady = true, hasPendingMutations = false),
        )
    }

    @Test
    fun connectedStateWithPendingWork_reconcilesBeforeNewDirectWrites() {
        assertEquals(
            MutationDeliveryMode.RECONCILE_PENDING_FIRST,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = true, hasPendingMutations = true),
        )
        assertEquals(
            MutationDeliveryMode.RECONCILE_PENDING_FIRST,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = false, hasPendingMutations = false),
        )
    }
}
