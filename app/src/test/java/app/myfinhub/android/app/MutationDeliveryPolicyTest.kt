package app.myfinhub.android.app

import app.myfinhub.android.core.network.ApiFailureKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class MutationDeliveryPolicyTest {
    @Test
    fun http400WithoutNetworkOutageFallsBackToCacheAndQueuesLocally() {
        assertTrue(canFallbackToEncryptedFinanceCache(ApiFailureKind.INVALID_DATA, true))
        assertTrue(canFallbackToEncryptedFinanceCache(ApiFailureKind.NETWORK, true))
        assertTrue(canFallbackToEncryptedFinanceCache(ApiFailureKind.MALFORMED_RESPONSE, true))
        assertFalse(canFallbackToEncryptedFinanceCache(ApiFailureKind.INVALID_DATA, false))
        assertFalse(canFallbackToEncryptedFinanceCache(ApiFailureKind.AUTH_REQUIRED, true))
        assertFalse(canFallbackToEncryptedFinanceCache(ApiFailureKind.MFA_REQUIRED, true))
        assertEquals(
            MutationDeliveryMode.DURABLE_OFFLINE_QUEUE,
            mutationDeliveryMode(
                serverAvailable = false, repositoryReady = false, hasPendingMutations = true,
            ),
        )
    }


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
