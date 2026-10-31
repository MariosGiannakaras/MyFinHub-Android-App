package app.myfinhub.android.feature.utilities

import app.myfinhub.android.core.network.DeviceSessionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeviceSessionRevocationSafetyTest {
    private val current = record("5b90719d-65c1-4abc-8299-3e07b192ac00", true)
    private val remote = record("5b90719d-65c1-4abc-8299-3e07b192ac11", false)

    @Test fun uniqueNonCurrentSourceIsEligible() {
        assertEquals(remote, revocableDeviceSession(remote.sessionId, listOf(current, remote)))
    }

    @Test fun missingBlankOrCurrentSessionCanNeverBeRevoked() {
        val rows = listOf(current, remote)
        assertNull(revocableDeviceSession(null, rows))
        assertNull(revocableDeviceSession("", rows))
        assertNull(revocableDeviceSession("missing", rows))
        assertNull(revocableDeviceSession(current.sessionId, rows))
    }

    @Test fun duplicateIdFailsClosedEvenIfOneRecordIsMarkedCurrent() {
        assertNull(revocableDeviceSession(remote.sessionId, listOf(remote, remote.copy(label = "Duplicate"))))
        assertNull(revocableDeviceSession(remote.sessionId, listOf(remote, remote.copy(current = true))))
    }

    private fun record(id: String, current: Boolean) = DeviceSessionRecord(
        sessionId = id,
        platform = "android",
        label = "Synthetic device",
        appVersion = "1.0.0",
        firstSeenAt = "2026-10-01T09:00:00Z",
        lastSeenAt = "2026-10-30T09:00:00Z",
        current = current,
    )
}
