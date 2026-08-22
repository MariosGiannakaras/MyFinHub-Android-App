package app.myfinhub.android.core.network

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyntheticMyFinHubApiTest {
    @Test
    fun bootstrapSummary_isSyntheticAndHasNoCanonicalRevision() = runBlocking {
        val result = SyntheticMyFinHubApi().loadBootstrapSummary()
        val success = result as ApiResult.Success

        assertEquals(DataSource.SYNTHETIC, success.value.source)
        assertNull(success.value.revision)
    }
}
