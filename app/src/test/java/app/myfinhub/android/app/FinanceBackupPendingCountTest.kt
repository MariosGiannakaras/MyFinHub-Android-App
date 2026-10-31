package app.myfinhub.android.app

import app.myfinhub.android.feature.utilities.isCurrentBackupExportCandidate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FinanceBackupPendingCountTest {
    private val current = JsonObject(mapOf("revision" to JsonPrimitive("synthetic")))

    @Test fun connectedUiMayHidePendingBannerButBackupMustStillBlock() {
        val counts = financePendingCounts(total = 2, needsReview = 1, exposePending = false)
        assertEquals(0, counts.visibleChangeCount)
        assertEquals(0, counts.visibleReviewCount)
        assertEquals(2, counts.blockingBackupCount)
        assertFalse(isCurrentBackupExportCandidate(
            selectedSnapshot = current,
            currentDocument = current,
            pendingChangeCount = counts.blockingBackupCount,
            mutationBlocked = false,
        ))
    }

    @Test fun offlineUiMayShowPendingAndReviewSubsetWithoutDoubleCountingBackup() {
        val counts = financePendingCounts(total = 3, needsReview = 2, exposePending = true)
        assertEquals(3, counts.visibleChangeCount)
        assertEquals(2, counts.visibleReviewCount)
        // NEEDS_REVIEW is a subset of all unresolved intents, not another 2 writes.
        assertEquals(3, counts.blockingBackupCount)
    }

    @Test fun fullySettledQueuePermitsOtherwiseValidSnapshot() {
        val counts = financePendingCounts(total = 0, needsReview = 0, exposePending = false)
        assertEquals(0, counts.blockingBackupCount)
        assertTrue(isCurrentBackupExportCandidate(current, current, counts.blockingBackupCount, false))
    }

    @Test fun invalidReviewCountCannotUnderstateBackupBlockers() {
        assertThrows(IllegalArgumentException::class.java) {
            financePendingCounts(total = 1, needsReview = 2, exposePending = false)
        }
    }
}
