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
import app.myfinhub.android.core.network.CardSecretDeleteReceipt
import app.myfinhub.android.core.network.CardSecretUpdate
import app.myfinhub.android.core.network.CardSecretWriteReceipt
import app.myfinhub.android.core.network.CardSecrets
import app.myfinhub.android.core.network.MyFinHubApi
import app.myfinhub.android.core.security.CardDetailsVault
import app.myfinhub.android.core.security.CvvVault
import app.myfinhub.android.core.security.LocalCardDetails
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
    fun openCard_readsFullServerDetailsAndRedactsStateString() = runBlocking {
        val api = FakeCardApi(
            serverSecrets = CardSecrets("4242424242424242", "12/30", "321"),
        )
        val cvvVault = FakeCvvVault()
        val detailsVault = FakeCardDetailsVault()
        val viewModel = CardSecretViewModel(application, api, cvvVault, detailsVault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }

        val state = viewModel.state.value as CardSecretUiState.Revealed
        assertEquals("4242424242424242", state.pan)
        assertEquals("12/30", state.expiry)
        assertEquals("321", state.cvv)
        assertEquals(1, api.serverSecretReadCalls)
        assertFalse(state.toString().contains("4242424242424242"))
        assertFalse(state.toString().contains("321"))
    }

    @Test
    fun editPanAndExpiry_savesToServerVault_andZeroesInputs() = runBlocking {
        val api = FakeCardApi(serverSecrets = CardSecrets("4242424242424242", "12/30", "321"))
        val cvvVault = FakeCvvVault()
        val detailsVault = FakeCardDetailsVault()
        val viewModel = CardSecretViewModel(application, api, cvvVault, detailsVault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }

        val pan = "5555444433331111".toCharArray()
        val expiry = "09/31".toCharArray()
        viewModel.saveServerSecrets(pan, expiry)

        assertTrue(pan.all { it == '\u0000' })
        assertTrue(expiry.all { it == '\u0000' })
        waitUntil { api.serverSecretWriteCalls == 1 }
        assertEquals("5555444433331111", api.lastUpdate?.pan)
        assertEquals("09/31", api.lastUpdate?.expiry)
        assertNull(api.lastUpdate?.cvv)
    }

    @Test
    fun createFlow_savesPanExpiryAndCvvToServer_andZeroesCallerBuffers() = runBlocking {
        val api = FakeCardApi()
        val cvvVault = FakeCvvVault()
        val detailsVault = FakeCardDetailsVault()
        val viewModel = CardSecretViewModel(application, api, cvvVault, detailsVault)
        viewModel.attachSession(session)

        val pan = "4000000000000002".toCharArray()
        val expiry = "08/30".toCharArray()
        val cvv = "987".toCharArray()
        viewModel.saveCardDetailsForCard("card-new", pan, expiry, cvv)

        assertTrue(pan.all { it == '\u0000' })
        assertTrue(expiry.all { it == '\u0000' })
        assertTrue(cvv.all { it == '\u0000' })
        waitUntil { api.serverSecretWriteCalls == 1 }

        assertEquals("4000000000000002", api.lastUpdate?.pan)
        assertEquals("08/30", api.lastUpdate?.expiry)
        assertEquals("987", api.lastUpdate?.cvv)
    }

    @Test
    fun openCard_migratesLegacyLocalValuesWhenServerSecretIsMissing() = runBlocking {
        val api = FakeCardApi(serverSecrets = null)
        val cvvVault = FakeCvvVault(initial = "321".toCharArray())
        val detailsVault = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        )
        val viewModel = CardSecretViewModel(application, api, cvvVault, detailsVault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        waitUntil { api.serverSecretWriteCalls == 1 }
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }

        assertEquals("4242424242424242", api.lastUpdate?.pan)
        assertEquals("12/30", api.lastUpdate?.expiry)
        assertEquals("321", api.lastUpdate?.cvv)
        assertNull(detailsVault.load("card-1"))
        assertNull(cvvVault.load("card-1"))
    }

    @Test
    fun purgeCard_removesServerResidueAndBothDeviceLocalVaults() = runBlocking {
        val api = FakeCardApi()
        val cvvVault = FakeCvvVault(initial = "321".toCharArray())
        val detailsVault = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        )
        val viewModel = CardSecretViewModel(application, api, cvvVault, detailsVault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }
        viewModel.purgeCard("card-1")
        waitUntil { viewModel.cleanupState.value is CardSecretCleanupUiState.Complete }

        assertEquals(1, api.serverSecretDeleteCalls)
        assertEquals("card-1", detailsVault.deletedCardId)
        assertEquals("card-1", cvvVault.deletedCardId)
        assertNull(detailsVault.load("card-1"))
        assertNull(cvvVault.load("card-1"))
    }

    private suspend fun waitUntil(predicate: () -> Boolean) {
        withTimeout(3_000) {
            while (!predicate()) delay(10)
        }
    }
}

private class FakeCardDetailsVault(
    initialPan: CharArray? = null,
    initialExpiry: CharArray? = null,
) : CardDetailsVault {
    private var storedPan: CharArray? = initialPan?.copyOf()
    private var storedExpiry: CharArray? = initialExpiry?.copyOf()
    var savedPan: CharArray? = null
        private set
    var savedExpiry: CharArray? = null
        private set
    var deletedCardId: String? = null
        private set

    override suspend fun load(cardId: String): LocalCardDetails? {
        val pan = storedPan?.copyOf() ?: return null
        val expiry = storedExpiry?.copyOf() ?: run {
            pan.fill('\u0000')
            return null
        }
        return LocalCardDetails(pan, expiry)
    }

    override suspend fun save(cardId: String, pan: CharArray, expiry: CharArray) {
        savedPan?.fill('\u0000')
        savedExpiry?.fill('\u0000')
        storedPan?.fill('\u0000')
        storedExpiry?.fill('\u0000')
        savedPan = pan.copyOf()
        savedExpiry = expiry.copyOf()
        storedPan = pan.copyOf()
        storedExpiry = expiry.copyOf()
    }

    override suspend fun delete(cardId: String) {
        deletedCardId = cardId
        storedPan?.fill('\u0000')
        storedExpiry?.fill('\u0000')
        storedPan = null
        storedExpiry = null
    }
}

private class FakeCvvVault(initial: CharArray? = null) : CvvVault {
    private var stored: CharArray? = initial?.copyOf()
    var saved: CharArray? = null
        private set
    var deletedCardId: String? = null
        private set

    override suspend fun load(cardId: String): CharArray? = stored?.copyOf()

    override suspend fun save(cardId: String, cvv: CharArray) {
        saved?.fill('\u0000')
        stored?.fill('\u0000')
        saved = cvv.copyOf()
        stored = cvv.copyOf()
    }

    override suspend fun delete(cardId: String) {
        deletedCardId = cardId
        stored?.fill('\u0000')
        stored = null
    }
}

private class FakeCardApi(
    private var serverSecrets: CardSecrets? = CardSecrets("4242424242424242", "12/30", "321"),
) : MyFinHubApi {
    var serverSecretReadCalls: Int = 0
        private set
    var serverSecretWriteCalls: Int = 0
        private set
    var serverSecretDeleteCalls: Int = 0
        private set
    var lastUpdate: CardSecretUpdate? = null
        private set

    override suspend fun loadFinanceData(session: AuthSession): ApiResult<CanonicalFinanceEnvelope> =
        ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun saveMutableState(
        session: AuthSession,
        document: CanonicalFinanceDocument,
        expectedRevision: String,
    ): ApiResult<CanonicalWriteReceipt> = ApiResult.Failure(ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE)

    override suspend fun loadCardSecrets(session: AuthSession, cardId: String): ApiResult<CardSecrets> {
        serverSecretReadCalls += 1
        return serverSecrets?.let { ApiResult.Success(it) } ?: ApiResult.Failure(ApiFailureKind.INVALID_DATA)
    }

    override suspend fun saveCardSecrets(
        session: AuthSession,
        cardId: String,
        update: CardSecretUpdate,
    ): ApiResult<CardSecretWriteReceipt> {
        serverSecretWriteCalls += 1
        lastUpdate = update
        val current = serverSecrets
        serverSecrets = CardSecrets(
            pan = update.pan ?: current?.pan,
            expiry = update.expiry ?: current?.expiry,
            cvv = update.cvv ?: current?.cvv,
        )
        return ApiResult.Success(CardSecretWriteReceipt(saved = true, last4 = serverSecrets?.pan?.takeLast(4)))
    }

    override suspend fun deleteCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> {
        serverSecretDeleteCalls += 1
        return ApiResult.Success(CardSecretDeleteReceipt(deleted = true))
    }
}
