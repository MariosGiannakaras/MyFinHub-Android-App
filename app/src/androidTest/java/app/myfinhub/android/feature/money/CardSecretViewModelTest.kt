package app.myfinhub.android.feature.money

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.core.auth.AssuranceLevel
import app.myfinhub.android.core.auth.AuthSession
import app.myfinhub.android.core.data.CanonicalFinanceDocument
import app.myfinhub.android.core.data.CanonicalFinanceEnvelope
import app.myfinhub.android.core.data.CanonicalWriteReceipt
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.BootstrapSummary
import app.myfinhub.android.core.network.CardSecretDeleteReceipt
import app.myfinhub.android.core.network.CardSecretUpdate
import app.myfinhub.android.core.network.CardSecretWriteReceipt
import app.myfinhub.android.core.network.CardSecrets
import app.myfinhub.android.core.network.DataSource
import app.myfinhub.android.core.network.MyFinHubApi
import app.myfinhub.android.core.security.CvvVault
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CardSecretViewModelTest {
    private val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val session = AuthSession(
        accessToken = "synthetic-bearer",
        refreshToken = "synthetic-refresh",
        expiresAtEpochSeconds = 99_999,
        userId = "owner",
        assuranceLevel = AssuranceLevel.AAL2,
    )

    @Test
    fun revealAndLocalCvvSave_keepCvvOffServerAndClearOnClose() = runBlocking {
        val api = FakeCardApi(
            revealResult = ApiResult.Success(CardSecrets("4242424242424242", "12/30")),
        )
        val vault = FakeCvvVault(initial = charArrayOf('3', '2', '1'))
        val viewModel = CardSecretViewModel(application, api, vault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        viewModel.reveal()
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }

        val revealed = viewModel.state.value as CardSecretUiState.Revealed
        assertEquals("4242", revealed.pan?.takeLast(4))
        assertEquals("321", revealed.cvv)
        assertFalse(revealed.toString().contains("4242424242424242"))
        assertFalse(revealed.toString().contains("321"))

        val replacement = charArrayOf('9', '8', '7')
        viewModel.saveCvv(replacement)
        assertTrue(replacement.all { it == '\u0000' })
        waitUntil { vault.saved?.contentEquals(charArrayOf('9', '8', '7')) == true }
        assertEquals(0, api.serverSecretWriteCalls)

        viewModel.closeCard("card-1")
        assertTrue(viewModel.state.value is CardSecretUiState.Hidden)
    }

    @Test
    fun failedLocalDelete_keepsExistingCvvVisibleAndReportsFailure() = runBlocking {
        val api = FakeCardApi(ApiResult.Success(CardSecrets(null, null)))
        val vault = FakeCvvVault(initial = charArrayOf('3', '2', '1'), failDelete = true)
        val viewModel = CardSecretViewModel(application, api, vault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        viewModel.reveal()
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }
        viewModel.deleteCvv()
        waitUntil {
            (viewModel.state.value as? CardSecretUiState.Revealed)?.cvvSaving == false &&
                (viewModel.state.value as? CardSecretUiState.Revealed)?.message != null
        }

        val state = viewModel.state.value as CardSecretUiState.Revealed
        assertEquals("321", state.cvv)
        assertTrue(state.message.orEmpty().contains("δεν ολοκληρώθηκε"))
    }

    @Test
    fun authFailureFromCardVault_requestsNormalAuthRecovery() = runBlocking {
        val api = FakeCardApi(
            revealResult = ApiResult.Failure(ApiFailureKind.AUTH_REQUIRED),
        )
        val viewModel = CardSecretViewModel(application, api, FakeCvvVault())

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        viewModel.reveal()
        waitUntil { viewModel.state.value is CardSecretUiState.AuthRejected }

        assertTrue(viewModel.state.value is CardSecretUiState.AuthRejected)
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(3_000) {
            while (!predicate()) delay(10)
        }
    }
}

private class FakeCvvVault(
    initial: CharArray? = null,
    private val failDelete: Boolean = false,
) : CvvVault {
    private var stored: CharArray? = initial?.copyOf()
    var saved: CharArray? = null
        private set

    override suspend fun load(cardId: String): CharArray? = stored?.copyOf()

    override suspend fun save(cardId: String, cvv: CharArray) {
        saved = cvv.copyOf()
        stored?.fill('\u0000')
        stored = cvv.copyOf()
    }

    override suspend fun delete(cardId: String) {
        if (failDelete) error("synthetic delete failure")
        stored?.fill('\u0000')
        stored = null
    }
}

private class FakeCardApi(
    private val revealResult: ApiResult<CardSecrets>,
) : MyFinHubApi {
    var serverSecretWriteCalls: Int = 0
        private set

    override suspend fun loadBootstrapSummary(): ApiResult<BootstrapSummary> =
        ApiResult.Success(BootstrapSummary(DataSource.SYNTHETIC, null))

    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun loadCardSecrets(session: AuthSession, cardId: String): ApiResult<CardSecrets> = revealResult

    override suspend fun saveCardSecrets(
        session: AuthSession,
        cardId: String,
        update: CardSecretUpdate,
    ): ApiResult<CardSecretWriteReceipt> {
        serverSecretWriteCalls += 1
        return ApiResult.Success(CardSecretWriteReceipt(saved = true, last4 = update.pan?.takeLast(4)))
    }

    override suspend fun deleteCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> = ApiResult.Success(CardSecretDeleteReceipt(deleted = true))
}
