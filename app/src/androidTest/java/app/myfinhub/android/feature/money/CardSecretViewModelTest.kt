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
    fun sameOwnerAalDowngradeImmediatelyRedactsVaultStateAndBlocksFurtherReads() = runBlocking {
        val api = FakeCardApi()
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        assertEquals(1, api.serverSecretReadCalls)

        vm.attachSession(session.copy(assuranceLevel = AssuranceLevel.AAL1))
        assertTrue(vm.state.value === CardSecretUiState.AuthRejected)
        vm.openCard("card-1")
        assertTrue(vm.state.value === CardSecretUiState.AuthRejected)
        assertEquals(1, api.serverSecretReadCalls)
    }

    @Test
    fun sameOwnerBearerRotationHidesPriorSecretsUntilFreshAuthorizedRead() = runBlocking {
        val api = FakeCardApi()
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        assertEquals(1, api.serverSecretReadCalls)

        api.readDelayMillis = 180
        vm.attachSession(session.copy(accessToken = "synthetic-rotated-bearer"))
        assertTrue(vm.state.value is CardSecretUiState.Loading)
        assertFalse(vm.state.value is CardSecretUiState.Revealed)
        waitUntil { api.serverSecretReadCalls == 2 && vm.state.value is CardSecretUiState.Revealed }
        assertEquals("card-1", (vm.state.value as CardSecretUiState.Revealed).cardId)
    }

    @Test
    fun closeCardRedactsAlreadyRevealedSecretsOnNavigation() = runBlocking {
        val api = FakeCardApi()
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        vm.closeCard("card-1")
        assertEquals(CardSecretUiState.Hidden(), vm.state.value)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        assertEquals(2, api.serverSecretReadCalls)
    }

    @Test
    fun closeOrHideDuringVaultReadCannotRevealLateServerResponse() = runBlocking {
        val api = FakeCardApi()
        api.readDelayMillis = 220
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { api.serverSecretReadCalls == 1 }
        vm.closeCard("card-1")
        assertEquals(CardSecretUiState.Hidden(), vm.state.value)
        delay(300)
        assertEquals(CardSecretUiState.Hidden(), vm.state.value)

        vm.openCard("card-1")
        waitUntil { api.serverSecretReadCalls == 2 }
        vm.hideSecrets()
        assertEquals(CardSecretUiState.Hidden("card-1"), vm.state.value)
        delay(300)
        assertEquals(CardSecretUiState.Hidden("card-1"), vm.state.value)
    }

    @Test
    fun serverSecretEditsRequireCurrentlyRevealedCardAndAal2() = runBlocking {
        val api = FakeCardApi()
        api.readDelayMillis = 260
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        val prematurePan = "4000000000000002".toCharArray()
        val prematureExpiry = "09/31".toCharArray()
        vm.saveServerSecrets(prematurePan, prematureExpiry)
        assertTrue(prematurePan.all { it == '\u0000' })
        assertTrue(prematureExpiry.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)
        waitUntil { vm.state.value is CardSecretUiState.Revealed }

        vm.attachSession(session.copy(assuranceLevel = AssuranceLevel.AAL1))
        val revokedPan = "4000000000000002".toCharArray()
        val revokedExpiry = "09/31".toCharArray()
        vm.saveServerSecrets(revokedPan, revokedExpiry)
        assertTrue(revokedPan.all { it == '\u0000' })
        assertTrue(revokedExpiry.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)
        assertTrue(vm.state.value === CardSecretUiState.AuthRejected)

        val createPan = "4000000000000002".toCharArray()
        val createExpiry = "09/31".toCharArray()
        val createCvv = "123".toCharArray()
        vm.saveCardDetailsForCard("card-new", createPan, createExpiry, createCvv)
        assertTrue(createPan.all { it == '\u0000' })
        assertTrue(createExpiry.all { it == '\u0000' })
        assertTrue(createCvv.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)
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
    fun invalidNewWritesFailBeforeApiAndStillZeroAllCallerBuffers() = runBlocking {
        val api = FakeCardApi()
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)

        val longPan = "1234567890123456789".toCharArray()
        val validExpiry = "12/30".toCharArray()
        val fourCvv = "1234".toCharArray()
        vm.saveCardDetailsForCard("card-new", longPan, validExpiry, fourCvv)
        assertTrue(longPan.all { it == '\u0000' })
        assertTrue(validExpiry.all { it == '\u0000' })
        assertTrue(fourCvv.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)

        val validPan = "4242424242424242".toCharArray()
        val expired = "01/20".toCharArray()
        val validCvv = "123".toCharArray()
        vm.saveCardDetailsForCard("card-new", validPan, expired, validCvv)
        assertTrue(validPan.all { it == '\u0000' })
        assertTrue(expired.all { it == '\u0000' })
        assertTrue(validCvv.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)

        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        val pan15 = "123456789012345".toCharArray()
        val expiry = "12/30".toCharArray()
        vm.saveServerSecrets(pan15, expiry)
        assertTrue(pan15.all { it == '\u0000' })
        assertTrue(expiry.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)
        assertTrue(vm.state.value is CardSecretUiState.Failure)
    }

    @Test
    fun cvvOnlyRejectsFourDigitsButAcceptsThreeInCurrentOwnerSession() = runBlocking {
        val api = FakeCardApi()
        val vm = CardSecretViewModel(application, api, FakeCvvVault(), FakeCardDetailsVault())
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        val fourDigits = "1234".toCharArray()
        vm.saveCvv(fourDigits)
        assertTrue(fourDigits.all { it == '\u0000' })
        assertEquals(0, api.serverSecretWriteCalls)
        val threeDigits = "987".toCharArray()
        vm.saveCvv(threeDigits)
        assertTrue(threeDigits.all { it == '\u0000' })
        waitUntil { api.serverSecretWriteCalls == 1 }
        assertEquals("987", api.lastUpdate?.cvv)
        assertNull(api.lastUpdate?.pan)
        assertNull(api.lastUpdate?.expiry)
    }

    @Test
    fun incompatibleEncryptedLegacyCardCanStillBeReadWithoutUnsafeUploadOrLoss() = runBlocking {
        val api = FakeCardApi(serverSecrets = null)
        val legacyCvv = FakeCvvVault(initial = "1234".toCharArray())
        val legacyDetails = FakeCardDetailsVault(
            initialPan = "1234567890123456789".toCharArray(),
            initialExpiry = "01/20".toCharArray(),
        )
        val vm = CardSecretViewModel(application, api, legacyCvv, legacyDetails)
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        val state = vm.state.value as CardSecretUiState.Revealed
        assertEquals("1234567890123456789", state.pan)
        assertEquals("01/20", state.expiry)
        assertEquals("1234", state.cvv)
        assertEquals(0, api.serverSecretWriteCalls)
        assertEquals("1234567890123456789", legacyDetails.load("card-1")?.pan?.concatToString())
        assertEquals("1234", legacyCvv.load("card-1")?.concatToString())
        assertEquals(null, legacyDetails.deletedCardId)
        assertEquals(null, legacyCvv.deletedCardId)
        assertFalse(state.message.orEmpty().contains("μεταφέρθηκαν στο κοινό"))
    }

    @Test
    fun incompatiblePartialLegacyMigrationNeverDropsOtherEncryptedValues() = runBlocking {
        val api = FakeCardApi(serverSecrets = CardSecrets("4242424242424242", null, null))
        val local = FakeCardDetailsVault(
            initialPan = "1234567890123456789".toCharArray(),
            initialExpiry = "01/20".toCharArray(),
        )
        val cvv = FakeCvvVault(initial = "321".toCharArray())
        val vm = CardSecretViewModel(application, api, cvv, local)
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { vm.state.value is CardSecretUiState.Revealed }
        assertEquals(0, api.serverSecretWriteCalls)
        assertEquals("4242424242424242", (vm.state.value as CardSecretUiState.Revealed).pan)
        assertEquals("01/20", (vm.state.value as CardSecretUiState.Revealed).expiry)
        assertEquals("321", (vm.state.value as CardSecretUiState.Revealed).cvv)
        assertEquals("1234567890123456789", local.load("card-1")?.pan?.concatToString())
        assertEquals("321", cvv.load("card-1")?.concatToString())
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
    fun lateLocalMigrationMustNotWriteToServerAfterCardNavigationCloses() = runBlocking {
        val api = FakeCardApi(serverSecrets = null)
        val local = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        ).also { it.loadDelayMillis = 280 }
        val cvv = FakeCvvVault(initial = "321".toCharArray())
        val vm = CardSecretViewModel(application, api, cvv, local)
        vm.attachSession(session)
        vm.openCard("card-1")
        waitUntil { api.serverSecretReadCalls == 1 }
        vm.closeCard("card-1")
        delay(400)

        assertEquals(0, api.serverSecretWriteCalls)
        assertEquals(CardSecretUiState.Hidden(), vm.state.value)
        assertEquals("4242424242424242", local.load("card-1")?.pan?.concatToString())
        assertEquals("321", cvv.load("card-1")?.concatToString())
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
        assertEquals(1, api.committedSecretDeleteCalls)
        assertEquals("card-1", detailsVault.deletedCardId)
        assertEquals("card-1", cvvVault.deletedCardId)
        assertNull(detailsVault.load("card-1"))
        assertNull(cvvVault.load("card-1"))
        // A recomposition or repeated confirmed-marker notification may not re-delete
        // an already completed server+legacy-local secret cleanup.
        viewModel.purgeCard("card-1")
        delay(50)
        assertEquals(1, api.committedSecretDeleteCalls)
    }

    @Test
    fun bearerRotationDuringCommittedSecretCleanupRequiresExplicitServerRecheck() = runBlocking {
        val api = FakeCardApi().also { it.committedDeleteDelayMillis = 240 }
        val cvv = FakeCvvVault(initial = "321".toCharArray())
        val details = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        )
        val vm = CardSecretViewModel(application, api, cvv, details)
        vm.attachSession(session)
        vm.purgeCard("card-1")
        waitUntil { api.committedSecretDeleteCalls == 1 }
        vm.attachSession(session.copy(accessToken = "rotated-owner-bearer"))
        waitUntil { vm.cleanupState.value is CardSecretCleanupUiState.Failure }
        delay(280)

        val revoked = vm.cleanupState.value as CardSecretCleanupUiState.Failure
        assertTrue(revoked.serverCleanupPending)
        assertTrue(revoked.localCleanupPending)
        assertNull(details.deletedCardId)
        assertNull(cvv.deletedCardId)
        assertEquals("4242424242424242", details.load("card-1")?.pan?.concatToString())
        assertEquals("321", cvv.load("card-1")?.concatToString())

        api.committedDeleteDelayMillis = 0
        vm.retryPurgeCard("card-1")
        waitUntil { vm.cleanupState.value is CardSecretCleanupUiState.Complete }
        assertEquals(2, api.committedSecretDeleteCalls)
        assertEquals("card-1", details.deletedCardId)
        assertEquals("card-1", cvv.deletedCardId)
    }

    @Test
    fun assuranceDowngradeDuringFirstLocalDeleteDoesNotDeleteSecondVault() = runBlocking {
        val api = FakeCardApi()
        val cvv = FakeCvvVault(initial = "321".toCharArray())
        val details = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        ).also { it.deleteDelayMillis = 240 }
        val vm = CardSecretViewModel(application, api, cvv, details)
        vm.attachSession(session)
        vm.purgeCard("card-1")
        waitUntil { details.deleteStarted }
        // The first local delete has begun by now; changing assurance must
        // invalidate any subsequent deletion even if that suspension resumes.
        vm.attachSession(session.copy(assuranceLevel = AssuranceLevel.AAL1))
        delay(300)
        assertNull(cvv.deletedCardId)
        assertEquals("321", cvv.load("card-1")?.concatToString())
        val state = vm.cleanupState.value as CardSecretCleanupUiState.Failure
        assertTrue(state.serverCleanupPending)
        assertTrue(state.localCleanupPending)
        val requestsBeforeReauth = api.committedSecretDeleteCalls
        vm.retryPurgeCard("card-1")
        delay(30)
        assertEquals(requestsBeforeReauth, api.committedSecretDeleteCalls)
        assertNull(cvv.deletedCardId)
        vm.attachSession(session)
        details.deleteDelayMillis = 0
        vm.retryPurgeCard("card-1")
        waitUntil { vm.cleanupState.value is CardSecretCleanupUiState.Complete }
        assertEquals("card-1", cvv.deletedCardId)
    }

    @Test
    fun rejectedCommittedDeletionMustNotEraseLegacyDeviceSecretsOrAutoRetry() = runBlocking {
        val api = FakeCardApi(committedDeletionFailure = ApiFailureKind.INVALID_DATA)
        val cvv = FakeCvvVault(initial = "321".toCharArray())
        val details = FakeCardDetailsVault(
            initialPan = "4242424242424242".toCharArray(),
            initialExpiry = "12/30".toCharArray(),
        )
        val vm = CardSecretViewModel(application, api, cvv, details)
        vm.attachSession(session)
        vm.purgeCard("card-1")
        waitUntil { vm.cleanupState.value is CardSecretCleanupUiState.Failure }
        assertEquals(1, api.committedSecretDeleteCalls)
        assertEquals(0, api.serverSecretDeleteCalls)
        assertNull(details.deletedCardId)
        assertNull(cvv.deletedCardId)
        assertEquals("4242424242424242", details.load("card-1")?.pan?.concatToString())
        assertEquals("321", cvv.load("card-1")?.concatToString())

        // A rerender cannot repeat an AAL2/server-rejected attempt.
        vm.purgeCard("card-1")
        delay(50)
        assertEquals(1, api.committedSecretDeleteCalls)

        // A deliberate retry may succeed and then both local stores may be erased.
        api.committedDeletionFailure = null
        vm.retryPurgeCard("card-1")
        waitUntil { vm.cleanupState.value is CardSecretCleanupUiState.Complete }
        assertEquals(2, api.committedSecretDeleteCalls)
        assertEquals("card-1", details.deletedCardId)
        assertEquals("card-1", cvv.deletedCardId)
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
    var loadDelayMillis: Long = 0
    var deleteDelayMillis: Long = 0
    var deleteStarted = false
        private set
    private var storedPan: CharArray? = initialPan?.copyOf()
    private var storedExpiry: CharArray? = initialExpiry?.copyOf()
    var savedPan: CharArray? = null
        private set
    var savedExpiry: CharArray? = null
        private set
    var deletedCardId: String? = null
        private set

    override suspend fun load(cardId: String): LocalCardDetails? {
        if (loadDelayMillis > 0) delay(loadDelayMillis)
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
        deleteStarted = true
        if (deleteDelayMillis > 0) delay(deleteDelayMillis)
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
    var committedDeletionFailure: ApiFailureKind? = null,
) : MyFinHubApi {
    var readDelayMillis: Long = 0
    var committedDeleteDelayMillis: Long = 0
    var serverSecretReadCalls: Int = 0
        private set
    var serverSecretWriteCalls: Int = 0
        private set
    var serverSecretDeleteCalls: Int = 0
        private set
    var committedSecretDeleteCalls: Int = 0
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
        if (readDelayMillis > 0) delay(readDelayMillis)
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

    override suspend fun deleteCommittedCardSecrets(
        session: AuthSession,
        cardId: String,
    ): ApiResult<CardSecretDeleteReceipt> {
        committedSecretDeleteCalls += 1
        if (committedDeleteDelayMillis > 0) delay(committedDeleteDelayMillis)
        committedDeletionFailure?.let { return ApiResult.Failure(it) }
        return deleteCardSecrets(session, cardId)
    }
}
