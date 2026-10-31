package app.myfinhub.android.feature.utilities

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalFinanceBackupExportSafetyTest {
    private val snapshot = JsonObject(mapOf("revision" to JsonPrimitive("r1")))
    private val changed = JsonObject(mapOf("revision" to JsonPrimitive("r2")))

    @Test fun currentStableDocumentCanBeExported() {
        assertTrue(isCurrentBackupExportCandidate(snapshot, snapshot, 0, false))
        assertTrue(isCurrentBackupExportCandidate(snapshot, JsonObject(snapshot.toMap()), 0, false))
    }

    @Test fun snapshotChangedWhilePickerWasOpenMustBeRejected() {
        assertFalse(isCurrentBackupExportCandidate(snapshot, changed, 0, false))
        assertFalse(isCurrentBackupExportCandidate(snapshot, null, 0, false))
        assertFalse(isCurrentBackupExportCandidate(null, snapshot, 0, false))
    }

    @Test fun uncommittedOrBlockedFinancialChangesMustNotBeExported() {
        assertFalse(isCurrentBackupExportCandidate(snapshot, snapshot, 1, false))
        assertFalse(isCurrentBackupExportCandidate(snapshot, snapshot, 0, true))
        assertFalse(isCurrentBackupExportCandidate(snapshot, snapshot, 2, true))
    }
}
