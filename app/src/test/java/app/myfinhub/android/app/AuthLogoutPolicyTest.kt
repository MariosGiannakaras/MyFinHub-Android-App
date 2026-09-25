package app.myfinhub.android.app

import app.myfinhub.android.feature.money.CardSecretUiState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLogoutPolicyTest {
    @Test
    fun normalAndRecoverableProductStates_doNotForceLogout() {
        assertFalse(shouldLogoutForProductAuthRejection(FinanceProductState.Idle, CardSecretUiState.Hidden()))
        assertFalse(
            shouldLogoutForProductAuthRejection(
                FinanceProductState.Failure("temporary", retryable = true),
                CardSecretUiState.Hidden(),
            ),
        )
    }

    @Test
    fun onlyAuthoritativeProductAuthRejection_forcesLogout() {
        assertTrue(shouldLogoutForProductAuthRejection(FinanceProductState.AuthRejected, CardSecretUiState.Hidden()))
        assertTrue(shouldLogoutForProductAuthRejection(FinanceProductState.Idle, CardSecretUiState.AuthRejected))
    }
}
