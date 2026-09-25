package app.myfinhub.android.core.ui

import app.myfinhub.android.core.auth.AuthFailureKind
import app.myfinhub.android.core.auth.AuthResult
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserNoticeTest {
    @Test
    fun apiFailure_includesSafeOperationStatusAndDiagnosticCode() {
        val notice = ApiResult.Failure(
            kind = ApiFailureKind.SERVER,
            retryable = true,
            statusCode = 503,
        ).toUserNotice("Αποθήκευση")

        assertTrue(notice.details.contains("Ενέργεια: Αποθήκευση"))
        assertTrue(notice.details.contains("HTTP: 503"))
        assertTrue(notice.details.contains("Επανάληψη: επιτρέπεται"))
        assertEquals("MFH-API-SERVER-503", notice.diagnosticCode)
    }

    @Test
    fun authFailure_doesNotExposeGatewayMessage() {
        val secretGatewayMessage = "token=secret-token user@example.com"
        val notice = AuthResult.Failure(
            kind = AuthFailureKind.SERVER,
            message = secretGatewayMessage,
            retryable = true,
            statusCode = 500,
        ).toUserNotice("Σύνδεση")

        assertFalse(notice.message.contains(secretGatewayMessage))
        assertFalse(notice.details.contains(secretGatewayMessage))
        assertFalse(notice.diagnosticCode.contains(secretGatewayMessage))
        assertEquals("MFH-AUTH-SERVER-500", notice.diagnosticCode)
    }

    @Test
    fun unexpectedFailure_exposesOnlyExceptionTypeNotExceptionMessage() {
        val secret = "PAN 4242424242424242 CVV 123"
        val notice = unexpectedUserNotice(
            operation = "Τοπικό vault",
            throwable = IllegalStateException(secret),
        )

        assertTrue(notice.details.contains("IllegalStateException"))
        assertFalse(notice.details.contains(secret))
        assertFalse(notice.message.contains(secret))
    }
}
