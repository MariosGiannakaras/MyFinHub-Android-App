package app.myfinhub.android.feature.utilities

import app.myfinhub.android.core.update.UpdateFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class S9UtilitiesContractTest {
    @Test
    fun updater_hasOneDeterministicRecoveryPerFailureFamily() {
        assertEquals(UpdateRecoveryAction.AUTH, updateRecoveryAction(UpdateFailureKind.AUTH_REQUIRED, false))
        assertEquals(UpdateRecoveryAction.AUTH, updateRecoveryAction(UpdateFailureKind.MFA_REQUIRED, false))
        assertEquals(UpdateRecoveryAction.DOWNLOAD, updateRecoveryAction(UpdateFailureKind.DOWNLOAD_DIGEST_MISMATCH, true))
        assertEquals(UpdateRecoveryAction.INSTALL_PERMISSION, updateRecoveryAction(UpdateFailureKind.INSTALL_PERMISSION_REQUIRED, true))
        assertEquals(UpdateRecoveryAction.INSTALL, updateRecoveryAction(UpdateFailureKind.INSTALL_FAILED, true))
        assertEquals(UpdateRecoveryAction.CHECK, updateRecoveryAction(UpdateFailureKind.WRONG_SIGNER, true))
    }

    @Test
    fun amountVisibility_masksExactValuesWithoutChangingVisibleValues() {
        assertEquals("•••• €", amountVisibilityText("1.234,56 €", false))
        assertEquals("1.234,56 €", amountVisibilityText("1.234,56 €", true))
    }

    @Test
    fun diagnosticsCopy_isSanitizedSupportMetadataOnly() {
        val text = diagnosticsSupportText(
            AppDiagnosticsSnapshot(
                versionName = "1.0",
                buildType = "release",
                environment = "Production",
                apiHost = "example.invalid",
                networkStatus = "Συνδεδεμένο",
                apiStatus = "Συγχρονισμένο",
                sessionStatus = "Ενεργή και επαληθευμένη",
                lastSuccessfulSync = null,
                lastDiagnosticCode = "NET-001",
            ),
        )
        assertTrue(text.contains("NET-001"))
        assertFalse(text.contains("PAN", ignoreCase = true))
        assertFalse(text.contains("CVV", ignoreCase = true))
        assertFalse(text.contains("password", ignoreCase = true))
        assertFalse(text.contains("amount", ignoreCase = true))
    }
}
