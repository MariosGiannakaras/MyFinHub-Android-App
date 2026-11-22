package app.myfinhub.android.app

import app.myfinhub.android.core.network.NetworkStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Offline provenance must survive reconnect until a server-confirmed response. */
class FinanceNetworkProvenanceTest {
    @Test fun networkLossAndUnvalidatedNetworksRequireCacheDisclosure() {
        assertTrue(financeSnapshotRequiresRevalidation(false, NetworkStatus.OFFLINE))
        assertTrue(financeSnapshotRequiresRevalidation(false, NetworkStatus.UNKNOWN))
        assertFalse(financeSnapshotRequiresRevalidation(false, NetworkStatus.ONLINE))
    }

    @Test fun reconnectMustKeepPreviousCachedProjectionUnverifiedUntilAcceptedRead() {
        assertTrue(financeSnapshotRequiresRevalidation(true, NetworkStatus.ONLINE))
        assertTrue(financeSnapshotRequiresRevalidation(true, NetworkStatus.UNKNOWN))
        assertTrue(financeSnapshotRequiresRevalidation(true, NetworkStatus.OFFLINE))
    }
}
