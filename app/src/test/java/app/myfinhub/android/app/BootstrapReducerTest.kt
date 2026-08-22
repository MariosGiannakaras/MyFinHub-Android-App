package app.myfinhub.android.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootstrapReducerTest {
    @Test
    fun acknowledgeNativeBaseline_updatesOnlyAcknowledgementState() {
        val initial = BootstrapUiState()
        assertFalse(initial.acknowledged)

        val updated = reduceBootstrapState(initial, BootstrapAction.AcknowledgeNativeBaseline)

        assertTrue(updated.acknowledged)
        assertTrue(updated.copy(acknowledged = false) == initial)
    }
}
