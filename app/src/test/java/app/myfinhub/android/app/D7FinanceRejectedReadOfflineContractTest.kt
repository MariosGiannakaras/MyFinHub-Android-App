package app.myfinhub.android.app

import app.myfinhub.android.core.network.ApiFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A rejected GET must never discard a validated encrypted cache or create unsafe writes. */
class D7FinanceRejectedReadOfflineContractTest {
    @Test fun invalidServerPayloadAndTransientFailureAllowVerifiedLocalSnapshot() {
        assertTrue(financeReadFailureMayUseCache(ApiFailureKind.INVALID_DATA))
        assertTrue(financeReadFailureMayUseCache(ApiFailureKind.MALFORMED_RESPONSE))
        assertTrue(financeReadFailureMayUseCache(ApiFailureKind.NETWORK))
        assertTrue(financeReadFailureMayUseCache(ApiFailureKind.SERVER))
        assertTrue(financeReadFailureMayUseCache(ApiFailureKind.RATE_LIMITED))
        assertEquals(MutationDeliveryMode.DURABLE_OFFLINE_QUEUE, mutationDeliveryMode(
            serverAvailable = false, repositoryReady = false, hasPendingMutations = true,
        ))
    }

    @Test fun authenticationOrConfigurationFailureCannotBeBypassedWithCache() {
        assertFalse(financeReadFailureMayUseCache(ApiFailureKind.AUTH_REQUIRED))
        assertFalse(financeReadFailureMayUseCache(ApiFailureKind.MFA_REQUIRED))
        assertFalse(financeReadFailureMayUseCache(ApiFailureKind.BUILD_NOT_CONFIGURED))
        assertFalse(financeReadFailureMayUseCache(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE))
    }

    @Test fun healthyValidatedServerRemainsImmediateAndPendingRequiresReconcile() {
        assertEquals(MutationDeliveryMode.IMMEDIATE_SERVER, mutationDeliveryMode(
            serverAvailable = true, repositoryReady = true, hasPendingMutations = false,
        ))
        assertEquals(MutationDeliveryMode.RECONCILE_PENDING_FIRST, mutationDeliveryMode(
            serverAvailable = true, repositoryReady = true, hasPendingMutations = true,
        ))
    }
}
