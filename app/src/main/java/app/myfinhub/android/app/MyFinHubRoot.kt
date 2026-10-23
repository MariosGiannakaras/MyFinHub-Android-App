package app.myfinhub.android.app

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.myfinhub.android.BuildConfig
import app.myfinhub.android.core.config.AppConfiguration
import app.myfinhub.android.core.network.NetworkStatus
import app.myfinhub.android.core.network.NetworkClientFactory
import app.myfinhub.android.core.network.OkHttpMyFinHubApi
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.network.ApiResult
import app.myfinhub.android.core.network.WebAccountSecurityChange
import app.myfinhub.android.core.ui.PrivacySafeNoticeHistoryStore
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.core.ui.UserNotice
import app.myfinhub.android.core.ui.shouldPresentNotice
import app.myfinhub.android.core.update.LocalUpdateController
import app.myfinhub.android.core.update.UpdateController
import app.myfinhub.android.core.update.UpdateViewModel
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.auth.AuthShellScreen
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.auth.AuthShellViewModel
import app.myfinhub.android.feature.money.CardCreateRequest
import app.myfinhub.android.feature.money.CardSecretCleanupUiState
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.CardSecretViewModel
import app.myfinhub.android.feature.utilities.AppAppearance
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import java.net.URI
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.merge

/**
 * Production application root.
 *
 * Local biometric/PIN success is handled by [AuthShellViewModel]. A locally unlocked offline Ready
 * session may render only the encrypted device cache; finance network writes remain disabled until
 * AuthShell revalidates the server session. Card-secret and updater controllers are also detached
 * while the session is offline-only so no network-sensitive capability can use an unvalidated token.
 */
@Composable
fun MyFinHubRoot(
    authViewModel: AuthShellViewModel = viewModel(),
    financeViewModel: FinanceProductViewModel = viewModel(),
    cardSecretViewModel: CardSecretViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appearancePreferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var appearance by remember(context) { mutableStateOf(AppAppearancePreference.read(context)) }
    DisposableEffect(appearancePreferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { preferences, key ->
            if (key == AppAppearancePreference.KEY) {
                appearance = AppAppearance.fromStorage(preferences.getString(key, null))
            }
        }
        appearancePreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { appearancePreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    DisposableEffect(lifecycleOwner, updateViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateViewModel.onAppResumed()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            updateViewModel.onAppResumed()
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance) {
        AppAppearance.SYSTEM -> systemDark
        AppAppearance.LIGHT -> false
        AppAppearance.DARK -> true
    }

    val accountMetadataScope = rememberCoroutineScope()
    var accountMetadataWriteJob by remember { mutableStateOf<Job?>(null) }
    var accountMetadataSaving by remember { mutableStateOf(false) }
    val accountMetadataApi = remember {
        OkHttpMyFinHubApi(AppConfiguration.fromBuildConfig(), NetworkClientFactory.create())
    }
    var accountMetadata by remember { mutableStateOf<Map<String, AccountMetadataRecord>>(emptyMap()) }
    var accountMetadataLoaded by remember { mutableStateOf(false) }
    var metadataSessionKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var serverHistory by remember {
        mutableStateOf<app.myfinhub.android.core.network.ServerFinanceHistorySummary?>(null)
    }
    var serverHistoryLoaded by remember { mutableStateOf(false) }
    var serverHistorySessionKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var serverHistoryRefresh by remember { mutableStateOf(0) }
    var securityWriteJob by remember { mutableStateOf<Job?>(null) }
    var securitySaving by remember { mutableStateOf(false) }
    var securityFeedback by remember { mutableStateOf<String?>(null) }

    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val financeState by financeViewModel.state.collectAsStateWithLifecycle()
    val financialProviderCatalog by financeViewModel.financialProviderCatalog.collectAsStateWithLifecycle()
    val recurringServiceAssets by financeViewModel.recurringServiceAssets.collectAsStateWithLifecycle()
    val deviceSessions by financeViewModel.deviceSessions.collectAsStateWithLifecycle()
    val cardSecretState by cardSecretViewModel.state.collectAsStateWithLifecycle()
    val cardSecretCleanupState by cardSecretViewModel.cleanupState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.state.collectAsStateWithLifecycle()
    val networkStatus by financeViewModel.networkStatus.collectAsStateWithLifecycle()
    val lastSuccessfulSync by financeViewModel.lastSuccessfulSync.collectAsStateWithLifecycle()
    val latestFinanceState by rememberUpdatedState(financeState)
    val snackbarHostState = remember { SnackbarHostState() }
    val noticeHistoryStore = remember(context) { PrivacySafeNoticeHistoryStore(context) }
    val recentNoticePresentation = remember { mutableMapOf<String, Long>() }
    var noticeHistory by remember { mutableStateOf<List<PrivacySafeNoticeRecord>>(emptyList()) }
    var detailNotice by remember { mutableStateOf<UserNotice?>(null) }
    var lastDiagnosticCode by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(noticeHistoryStore) {
        noticeHistory = noticeHistoryStore.load()
    }
    val snackbarBottomPadding = if (
        authState is AuthShellUiState.Ready && financeState is FinanceProductState.Ready
    ) MyFinHubDesignMetrics.productSnackbarBottomClearance else MyFinHubSpacing.sm

    // No disk/cache persistence. A changed, offline or locked session instantly clears values.
    // LaunchedEffect cancels stale network work on session changes.
    LaunchedEffect(authState) {
        securityWriteJob?.cancel()
        securityWriteJob = null
        securitySaving = false
        securityFeedback = null
        accountMetadataWriteJob?.cancel()
        accountMetadataWriteJob = null
        accountMetadataSaving = false
        accountMetadata = emptyMap()
        accountMetadataLoaded = false
        metadataSessionKey = null
        val ready = authState as? AuthShellUiState.Ready
        if (ready != null && !ready.offline) {
            when (val result = accountMetadataApi.loadAccountMetadata(ready.session)) {
                is ApiResult.Success -> {
                    accountMetadata = result.value
                    metadataSessionKey = ready.session.userId to ready.session.accessToken
                    accountMetadataLoaded = true
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    // This history is an owner+AAL2 read-only server snapshot; never persisted, and
    // cancellation on auth/session change prevents cross-account display of history.
    LaunchedEffect(authState, serverHistoryRefresh) {
        serverHistory = null
        serverHistoryLoaded = false
        serverHistorySessionKey = null
        val ready = authState as? AuthShellUiState.Ready
        if (ready != null && !ready.offline) {
            when (val result = accountMetadataApi.loadServerFinanceHistory(ready.session)) {
                is ApiResult.Success -> {
                    serverHistory = result.value
                    serverHistorySessionKey = ready.session.userId to ready.session.accessToken
                    serverHistoryLoaded = true
                }
                is ApiResult.Failure -> Unit
            }
        }
    }

    LaunchedEffect(authState) {
        when (val state = authState) {
            is AuthShellUiState.Ready -> {
                financeViewModel.attachSession(
                    session = state.session,
                    allowAutomaticSync = !state.offline,
                )
                cardSecretViewModel.attachSession(state.session)
                if (state.offline) {
                    updateViewModel.clearSession()
                } else {
                    updateViewModel.attachSession(state.session)
                }
            }
            else -> {
                // Finance clear is intentionally volatile-only; encrypted offline cache survives lock.
                financeViewModel.clear()
                cardSecretViewModel.clear()
                updateViewModel.clearSession()
            }
        }
    }
    // The server-saved finance marker survives process death. Resume vault cleanup only with
    // an online authenticated session and a freshly confirmed profile deletion.
    LaunchedEffect(authState, financeState, cardSecretCleanupState) {
        val authReady = authState as? AuthShellUiState.Ready
        if (authReady != null && !authReady.offline &&
            authReady.session.assuranceLevel == app.myfinhub.android.core.auth.AssuranceLevel.AAL2
        ) {
            val pendingIds = financeViewModel.serverConfirmedPendingSecretCleanupIds()
            when (val cleanup = cardSecretCleanupState) {
                is CardSecretCleanupUiState.Complete -> {
                    if (cleanup.cardId in pendingIds) {
                        financeViewModel.acknowledgeCompletedCardSecretCleanup(cleanup.cardId)
                    } else {
                        pendingIds.firstOrNull()?.let(cardSecretViewModel::purgeCard)
                    }
                }
                CardSecretCleanupUiState.Idle ->
                    pendingIds.firstOrNull()?.let(cardSecretViewModel::purgeCard)
                is CardSecretCleanupUiState.Cleaning,
                is CardSecretCleanupUiState.Failure -> Unit
            }
        }
    }

    LaunchedEffect(financeState, cardSecretState) {
        if (shouldLogoutForProductAuthRejection(financeState, cardSecretState)) {
            financeViewModel.clear()
            cardSecretViewModel.clear()
            authViewModel.logout()
        }
    }
    LaunchedEffect(authViewModel, financeViewModel, cardSecretViewModel) {
        merge(
            authViewModel.notices,
            financeViewModel.notices,
            cardSecretViewModel.notices,
        ).collect { notice ->
            lastDiagnosticCode = notice.diagnosticCode
            val issue = (latestFinanceState as? FinanceProductState.Ready)?.issue
            val duplicateOfSaveIssue = issue != null &&
                notice.details.contains("Ενέργεια: Αποθήκευση οικονομικών δεδομένων")
            if (duplicateOfSaveIssue) return@collect
            if (!shouldPresentNotice(notice, recentNoticePresentation, SystemClock.elapsedRealtime())) return@collect

            noticeHistory = noticeHistoryStore.append(notice, System.currentTimeMillis())
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = "Λεπτομέρειες",
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                detailNotice = notice
            }
        }
    }

    val configuration = remember { AppConfiguration.fromBuildConfig() }
    val diagnostics = AppDiagnosticsSnapshot(
        versionName = BuildConfig.VERSION_NAME,
        buildType = BuildConfig.BUILD_TYPE,
        environment = environmentLabel(configuration.myFinHubApiBaseUrl),
        apiHost = runCatching { URI(configuration.myFinHubApiBaseUrl).host }.getOrNull().orEmpty().ifBlank { "Μη ρυθμισμένο" },
        networkStatus = networkStatusLabel(networkStatus),
        apiStatus = financeDiagnosticStatus(financeState),
        sessionStatus = authDiagnosticStatus(authState),
        lastSuccessfulSync = lastSuccessfulSync,
        lastDiagnosticCode = lastDiagnosticCode,
    )
    val updateController = UpdateController(
        state = updateState,
        check = { updateViewModel.checkForUpdates() },
        download = updateViewModel::downloadAvailableUpdate,
        install = updateViewModel::installReadyUpdate,
        openInstallPermission = {
            updateViewModel.installPermissionIntent()?.let { intent -> context.startActivity(intent) }
        },
    )

    // An account switch or token refresh must never render a previous session's private IBAN,
    // including the single Compose frame before LaunchedEffect clears the stale response.
    val activeMetadataSession = authState as? AuthShellUiState.Ready
    val metadataAuthorized = activeMetadataSession != null && !activeMetadataSession.offline &&
        metadataSessionKey == (activeMetadataSession.session.userId to activeMetadataSession.session.accessToken)
    val historyEnabled = activeMetadataSession != null && !activeMetadataSession.offline
    val historyAuthorized = historyEnabled &&
        serverHistorySessionKey ==
            (activeMetadataSession?.session?.userId to activeMetadataSession?.session?.accessToken)

    MyFinHubTheme(darkTheme = darkTheme) {
        CompositionLocalProvider(LocalUpdateController provides updateController) {
            Box(modifier = Modifier.fillMaxSize()) {
                AuthShellScreen(
                    state = authState,
                    onSignIn = authViewModel::signIn,
                    onSubmitTotp = authViewModel::submitTotp,
                    onEnrollPin = authViewModel::enrollPin,
                    onVerifyPin = authViewModel::verifyPin,
                    onBiometricSuccess = authViewModel::biometricSucceeded,
                    onPinFallbackRequested = authViewModel::requestPinFallback,
                    readyContent = {
                        FinanceProductSurface(
                            state = financeState,
                            accountMetadata = if (metadataAuthorized) accountMetadata else emptyMap(),
                            accountMetadataLoaded = metadataAuthorized && accountMetadataLoaded,
                            financialProviderCatalog = if (metadataAuthorized)
                                financialProviderCatalog else emptyList(),
                            recurringServiceAssets = if (metadataAuthorized)
                                recurringServiceAssets else emptyList(),
                            deviceSessions = if (metadataAuthorized) deviceSessions
                                else DeviceSessionsUiSnapshot(),
                            serverHistory = if (historyAuthorized) serverHistory else null,
                            serverHistoryEnabled = historyEnabled,
                            serverHistoryLoaded = historyAuthorized && serverHistoryLoaded,
                            onRefreshServerHistory = { serverHistoryRefresh += 1 },
                            accountSecurityEnabled = historyEnabled &&
                                activeMetadataSession?.session?.assuranceLevel ==
                                    app.myfinhub.android.core.auth.AssuranceLevel.AAL2,
                            accountSecuritySaving = securitySaving,
                            accountSecurityFeedback = securityFeedback,
                            onChangeAccountSecurity = { change ->
                                val authorized = authViewModel.state.value as? AuthShellUiState.Ready
                                if (!securitySaving && authorized != null && !authorized.offline &&
                                    authorized.session.assuranceLevel ==
                                        app.myfinhub.android.core.auth.AssuranceLevel.AAL2) {
                                    val original = authorized.session
                                    securitySaving = true
                                    securityFeedback = null
                                    securityWriteJob = accountMetadataScope.launch {
                                        val reply = accountMetadataApi.changeAccountSecurity(original, change)
                                        val active = authViewModel.state.value as? AuthShellUiState.Ready
                                            ?: return@launch
                                        if (active.offline || active.session.userId != original.userId ||
                                            active.session.accessToken != original.accessToken) return@launch
                                        securitySaving = false
                                        securityFeedback = when (reply) {
                                            is ApiResult.Success -> when (change) {
                                                is WebAccountSecurityChange.Email ->
                                                    if (reply.value.emailPendingConfirmation)
                                                        "Ζητήθηκε αλλαγή email. Ελέγξτε το νέο email για επιβεβαίωση."
                                                    else "Το email ενημερώθηκε στον server."
                                                is WebAccountSecurityChange.Password ->
                                                    "Ο κωδικός ενημερώθηκε στον server. Συνδεθείτε ξανά αν ζητηθεί."
                                            }
                                            is ApiResult.Failure ->
                                                "Η υπηρεσία δεν επιβεβαίωσε την αλλαγή. Επαληθεύστε τη συνεδρία και δοκιμάστε ξανά."
                                        }
                                    }
                                }
                            },
                            onRevokeDeviceSession = financeViewModel::revokeConfirmedOtherDeviceSession,
                            cardSecretState = cardSecretState,
                            cardSecretCleanupState = cardSecretCleanupState,
                            onRetryLoad = financeViewModel::retryLoad,
                            onRetryMutation = financeViewModel::retryPendingMutation,
                            onUndoPendingChange = financeViewModel::undoLatestPendingMutation,
                            onLogout = authViewModel::logout,
                            onHomeAction = financeViewModel::onHomeAction,
                            onActivityAction = financeViewModel::onActivityAction,
                            onQuickEntryAction = financeViewModel::onQuickEntryAction,
                            onSaveBudget = financeViewModel::saveOverallBudget,
                            onSaveCategoryBudget = financeViewModel::saveCategoryBudget,
                            onDeleteCategoryBudget = financeViewModel::deleteCategoryBudget,
                            onSaveMonthlySavingsRate = financeViewModel::saveMonthlySavingsRate,
                            onToggleTransactionRule = financeViewModel::setTransactionRuleEnabled,
                            onSaveTransactionRule = financeViewModel::saveTransactionRule,
                            onSaveCounterparty = financeViewModel::saveCounterparty,
                            onLinkActivityCounterparty = financeViewModel::linkActivityCounterparty,
                            onDeleteTransactionRule = financeViewModel::deleteTransactionRule,
                            onReorderTransactionRule = financeViewModel::reorderTransactionRule,
                            onSetRecurringStatus = financeViewModel::setRecurringStatus,
                            onCreateRecurring = financeViewModel::createRecurring,
                            onEditRecurring = financeViewModel::editRecurring,
                            onCreateScheduled = financeViewModel::createScheduled,
                            onCompleteScheduled = financeViewModel::completeScheduled,
                            onCloseScheduled = financeViewModel::closeScheduled,
                            onRecordLoanPayment = financeViewModel::recordLoanPayment,
                            onSaveLoanMetadata = financeViewModel::saveLoanMetadata,
                            onSaveSavingsGoal = financeViewModel::saveSavingsGoal,
                            onDeleteSavingsGoal = financeViewModel::deleteSavingsGoal,
                            onSaveAccountIban = { id, rawIban ->
                                val sessionReady = authViewModel.state.value as? AuthShellUiState.Ready
                                val ready = financeViewModel.state.value as? FinanceProductState.Ready
                                val account = ready?.projection?.moneyState?.accounts?.singleOrNull { it.id == id }
                                val eligible = account?.canonicalKind in setOf("bank", "savings")
                                if (!accountMetadataSaving && accountMetadataLoaded &&
                                    metadataSessionKey != null && sessionReady != null &&
                                    !sessionReady.offline && eligible &&
                                    metadataSessionKey ==
                                        (sessionReady.session.userId to sessionReady.session.accessToken)) {
                                    val session = sessionReady.session
                                    val revision = accountMetadata[id]?.revision ?: 0L
                                    accountMetadataSaving = true
                                    accountMetadataWriteJob = accountMetadataScope.launch {
                                        val response = accountMetadataApi.updateAccountIban(
                                            session, id, rawIban, revision,
                                        )
                                        val fresh = authViewModel.state.value as? AuthShellUiState.Ready
                                            ?: return@launch
                                        if (fresh.session.userId != session.userId ||
                                            fresh.session.accessToken != session.accessToken ||
                                            fresh.offline || metadataSessionKey !=
                                            (session.userId to session.accessToken)) return@launch
                                        accountMetadataSaving = false
                                        when (response) {
                                            is ApiResult.Success -> {
                                                accountMetadata = accountMetadata + (id to response.value)
                                                snackbarHostState.showSnackbar("Το IBAN ενημερώθηκε με επιβεβαίωση server.")
                                            }
                                            is ApiResult.Failure -> {
                                                // Revision conflict or ambiguous write: no optimistic value.
                                                accountMetadataLoaded = false
                                                accountMetadata = emptyMap()
                                                val reload = accountMetadataApi.loadAccountMetadata(session)
                                                val check = authViewModel.state.value as? AuthShellUiState.Ready
                                                if (check != null &&
                                                    check.session.userId == session.userId &&
                                                    check.session.accessToken == session.accessToken &&
                                                    !check.offline) {
                                                    if (reload is ApiResult.Success) {
                                                        accountMetadata = reload.value
                                                        accountMetadataLoaded = true
                                                    }
                                                    snackbarHostState.showSnackbar(
                                                        "Η αλλαγή δεν επιβεβαιώθηκε. Ελέγχθηκαν ξανά τα metadata.")
                                                }
                                            }
                                        }
                                    }
                                }
                            },
                            accountMetadataSaving = accountMetadataSaving,
                            onCardDetailOpened = cardSecretViewModel::openCard,
                            onCardDetailClosed = cardSecretViewModel::closeCard,
                            onRevealCardSecrets = cardSecretViewModel::reveal,
                            onHideCardSecrets = cardSecretViewModel::hideSecrets,
                            onSaveServerCardSecrets = cardSecretViewModel::saveServerSecrets,
                            onSaveCvv = cardSecretViewModel::saveCvv,
                            onSaveCardDetails = cardSecretViewModel::saveCardDetailsForCard,
                            onRetryCardSecretCleanup = cardSecretViewModel::retryPurgeCard,
                            onDeleteCard = financeViewModel::deleteCard,
                            onRestoreCard = financeViewModel::restoreCard,
                            onCreateCard = financeViewModel::createCard,
                            onSaveCardProfile = financeViewModel::saveCardProfile,
                            onSaveCreditBillingSettings = financeViewModel::saveCreditBillingSettings,
                            onAddCreditRateVersion = financeViewModel::addCreditRateVersion,
                            diagnostics = diagnostics,
                            noticeHistory = noticeHistory,
                        )
                    },
                )
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = MyFinHubSpacing.md)
                        .padding(bottom = snackbarBottomPadding),
                )
            }

            detailNotice?.let { notice ->
                UserNoticeDetailsDialog(
                    notice = notice,
                    onDismiss = { detailNotice = null },
                )
            }
        }
    }
}

@Composable
internal fun UserNoticeDetailsDialog(
    notice: UserNotice,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Λεπτομέρειες σφάλματος") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                Text(notice.details)
                Text(
                    "Κωδικός: ${notice.diagnosticCode}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Κλείσιμο")
            }
        },
    )
}

@Composable
private fun FinanceProductSurface(
    state: FinanceProductState,
    accountMetadata: Map<String, AccountMetadataRecord>,
    accountMetadataLoaded: Boolean,
    financialProviderCatalog: List<app.myfinhub.android.core.network.FinancialProviderRecord>,
    recurringServiceAssets: List<app.myfinhub.android.core.network.RecurringServiceAssetRecord>,
    deviceSessions: DeviceSessionsUiSnapshot,
    serverHistory: app.myfinhub.android.core.network.ServerFinanceHistorySummary?,
    serverHistoryEnabled: Boolean,
    serverHistoryLoaded: Boolean,
    onRefreshServerHistory: () -> Unit,
    accountSecurityEnabled: Boolean,
    accountSecuritySaving: Boolean,
    accountSecurityFeedback: String?,
    onChangeAccountSecurity: (WebAccountSecurityChange) -> Unit,
    onRevokeDeviceSession: (String) -> Unit,
    cardSecretState: CardSecretUiState,
    cardSecretCleanupState: CardSecretCleanupUiState,
    onRetryLoad: () -> Unit,
    onRetryMutation: () -> Unit,
    onUndoPendingChange: () -> Unit,
    onLogout: () -> Unit,
    onHomeAction: (app.myfinhub.android.feature.home.HomeAction) -> Unit,
    onActivityAction: (app.myfinhub.android.feature.activity.ActivityAction) -> Unit,
    onQuickEntryAction: (app.myfinhub.android.feature.quickentry.QuickEntryAction) -> Unit,
    onSaveBudget: (String, String) -> Unit,
    onSaveCategoryBudget: (app.myfinhub.android.feature.plan.CategoryBudgetEditRequest) -> Unit,
    onDeleteCategoryBudget: (String) -> Unit,
    onSaveMonthlySavingsRate: (String) -> Unit,
    onToggleTransactionRule: (String, Boolean) -> Unit,
    onSaveTransactionRule: (app.myfinhub.android.feature.utilities.TransactionRuleEditRequest) -> Unit,
    onSaveCounterparty: (app.myfinhub.android.feature.utilities.CanonicalCounterpartyEditRequest) -> Unit,
    onLinkActivityCounterparty: (String, String?) -> Unit,
    onDeleteTransactionRule: (String) -> Unit,
    onReorderTransactionRule: (String, Int) -> Unit,
    onSetRecurringStatus: (String, String) -> Unit,
    onCreateRecurring: (app.myfinhub.android.feature.plan.RecurringCreateRequest) -> Unit,
    onEditRecurring: (String, app.myfinhub.android.feature.plan.RecurringCreateRequest) -> Unit,
    onCreateScheduled: (app.myfinhub.android.feature.plan.ScheduledCreateRequest) -> Unit,
    onCompleteScheduled: (app.myfinhub.android.feature.plan.ScheduledCompletionRequest) -> Unit,
    onCloseScheduled: (String, String) -> Unit,
    onRecordLoanPayment: (String, String, String, Int, Double?, String?) -> Unit,
    onSaveLoanMetadata: (app.myfinhub.android.feature.money.LoanMetadataEditRequest) -> Unit,
    onSaveSavingsGoal: (String?, String, String, String?) -> Unit,
    onDeleteSavingsGoal: (String) -> Unit,
    onSaveAccountIban: (String, String?) -> Unit,
    accountMetadataSaving: Boolean,
    onCardDetailOpened: (String) -> Unit,
    onCardDetailClosed: (String) -> Unit,
    onRevealCardSecrets: () -> Unit,
    onHideCardSecrets: () -> Unit,
    onSaveServerCardSecrets: (CharArray, CharArray) -> Unit,
    onSaveCvv: (CharArray) -> Unit,
    onSaveCardDetails: (String, CharArray, CharArray, CharArray) -> Unit,
    onRetryCardSecretCleanup: (String) -> Unit,
    onDeleteCard: (String) -> Unit,
    onRestoreCard: (String) -> Unit,
    onCreateCard: (CardCreateRequest) -> Unit,
    onSaveCardProfile: (app.myfinhub.android.feature.money.CardProfileEditRequest) -> Unit,
    onSaveCreditBillingSettings: (app.myfinhub.android.feature.money.CreditBillingSettingsRequest) -> Unit,
    onAddCreditRateVersion: (app.myfinhub.android.feature.money.CreditRateVersionRequest) -> Unit,
    diagnostics: AppDiagnosticsSnapshot,
    noticeHistory: List<PrivacySafeNoticeRecord>,
) {
    when (state) {
        FinanceProductState.Idle,
        FinanceProductState.Loading,
        FinanceProductState.AuthRejected -> FinanceLoadingScreen()

        is FinanceProductState.Failure -> FinanceFailureScreen(
            message = state.message,
            retryable = state.retryable,
            onRetry = onRetryLoad,
            onLogout = onLogout,
        )

        is FinanceProductState.Ready -> {
            val projection = state.projection
            Box(modifier = Modifier.fillMaxSize()) {
                MyFinHubAppContent(
                    homeState = projection.homeState,
                    accountMetadata = accountMetadata,
                    accountMetadataLoaded = accountMetadataLoaded,
                    onSaveAccountIban = onSaveAccountIban,
                    accountMetadataSaving = accountMetadataSaving,
                    financialProviderCatalog = financialProviderCatalog,
                    recurringServiceAssets = recurringServiceAssets,
                    backupFinanceDocument = projection.document.raw,
                    backupPendingChangeCount = state.pendingChangeCount + state.pendingReviewCount,
                    backupMutationBlocked = state.saving || state.issue != null,
                    backupOffline = state.offline,
                    deviceSessions = deviceSessions,
                    serverHistory = serverHistory,
                    serverHistoryEnabled = serverHistoryEnabled,
                    serverHistoryLoaded = serverHistoryLoaded,
                    onRefreshServerHistory = onRefreshServerHistory,
                    accountSecurityEnabled = accountSecurityEnabled,
                    accountSecuritySaving = accountSecuritySaving,
                    accountSecurityFeedback = accountSecurityFeedback,
                    onChangeAccountSecurity = onChangeAccountSecurity,
                    onRevokeDeviceSession = onRevokeDeviceSession,
                    onHomeAction = onHomeAction,
                    activityState = projection.activityState,
                    activityHistoricalBalance = { item ->
                        canonicalActivityBalanceAtDate(projection.document, item)
                    },
                    onActivityAction = onActivityAction,
                    activityMutationInFlight = state.saving,
                    activityMutationBlocked = state.saving || state.issue != null,
                    quickEntryState = projection.quickEntryState,
                    onQuickEntryAction = onQuickEntryAction,
                    quickEntryMutationInFlight = state.saving,
                    moneyState = projection.moneyState,
                    cardSecretState = cardSecretState,
                    cardSecretCleanupState = cardSecretCleanupState,
                    onCardDetailOpened = onCardDetailOpened,
                    onCardDetailClosed = onCardDetailClosed,
                    onRevealCardSecrets = onRevealCardSecrets,
                    onHideCardSecrets = onHideCardSecrets,
                    onSaveServerCardSecrets = onSaveServerCardSecrets,
                    onSaveCvv = onSaveCvv,
                    onSaveCardDetails = onSaveCardDetails,
                    onRetryCardSecretCleanup = onRetryCardSecretCleanup,
                    onDeleteCard = onDeleteCard,
                    onRestoreCard = onRestoreCard,
                    onCreateCard = onCreateCard,
                    onSaveCardProfile = onSaveCardProfile,
                    onSaveCreditBillingSettings = onSaveCreditBillingSettings,
                    onAddCreditRateVersion = onAddCreditRateVersion,
                    planState = projection.planState,
                    onSaveBudget = onSaveBudget,
                    onSaveCategoryBudget = onSaveCategoryBudget,
                    onDeleteCategoryBudget = onDeleteCategoryBudget,
                    onSaveMonthlySavingsRate = onSaveMonthlySavingsRate,
                    onToggleTransactionRule = onToggleTransactionRule,
                    onSaveTransactionRule = onSaveTransactionRule,
                    onSaveCounterparty = onSaveCounterparty,
                    onLinkActivityCounterparty = onLinkActivityCounterparty,
                    onDeleteTransactionRule = onDeleteTransactionRule,
                    onReorderTransactionRule = onReorderTransactionRule,
                    onSetRecurringStatus = onSetRecurringStatus,
                    onCreateRecurring = onCreateRecurring,
                    onEditRecurring = onEditRecurring,
                    onCreateScheduled = onCreateScheduled,
                    onCompleteScheduled = onCompleteScheduled,
                    onCloseScheduled = onCloseScheduled,
                    onRecordLoanPayment = onRecordLoanPayment,
                    onSaveLoanMetadata = onSaveLoanMetadata,
                    onSaveSavingsGoal = onSaveSavingsGoal,
                    onDeleteSavingsGoal = onDeleteSavingsGoal,
                    planMutationInFlight = state.saving,
                    planMutationBlocked = state.issue != null,
                    insightsState = projection.insightsState,
                    diagnostics = diagnostics,
                    noticeHistory = noticeHistory,
                    onLogout = onLogout,
                )
                if (state.saving) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding(),
                    )
                }
                if (state.latestPendingChange != null || state.issue != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                            .padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),
                        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                    ) {
                        state.latestPendingChange?.let { latest ->
                            PendingChangesBanner(
                                changeCount = state.pendingChangeCount,
                                latest = latest,
                                onUndoLatest = onUndoPendingChange,
                            )
                        }
                        state.issue?.let { issue ->
                            FinanceSyncIssueBanner(
                                issue = issue,
                                onRetryMutation = onRetryMutation,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PendingChangesBanner(
    changeCount: Int,
    latest: PendingChangeUi,
    onUndoLatest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
        shadowElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = MyFinHubSpacing.md, vertical = MyFinHubSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
                Text(
                    if (changeCount == 1) "1 αλλαγή σε αναμονή" else "$changeCount αλλαγές σε αναμονή",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    "${latest.label} · ${latest.statusLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (latest.canUndo) {
                TextButton(onClick = onUndoLatest) { Text("Αναίρεση") }
            }
        }
    }
}

@Composable
private fun FinanceSyncIssueBanner(
    issue: FinanceSyncIssue,
    onRetryMutation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (issue.kind) {
        FinanceSyncIssueKind.REVISION_CONFLICT -> "Χρειάζεται νέος συγχρονισμός"
        FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Αναμονή σύνδεσης"
        FinanceSyncIssueKind.SAVE_FAILED -> "Εκκρεμεί ασφαλής συγχρονισμός"
    }
    val retryLabel = when (issue.kind) {
        FinanceSyncIssueKind.REVISION_CONFLICT -> "Φόρτωση και επανάληψη"
        FinanceSyncIssueKind.WAITING_FOR_NETWORK -> "Δοκιμή ξανά"
        FinanceSyncIssueKind.SAVE_FAILED -> "Επανάληψη με έλεγχο"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
        shadowElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                issue.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetryMutation) {
                Text(retryLabel)
            }
            Text(
                "Οι τοπικές αλλαγές παραμένουν ορατές ως εκκρεμείς μέχρι να επιβεβαιωθεί ο συγχρονισμός.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun environmentLabel(apiBaseUrl: String): String = when {
    apiBaseUrl.isBlank() -> "Μη ρυθμισμένο"
    apiBaseUrl.contains("localhost", ignoreCase = true) || apiBaseUrl.contains("127.0.0.1") -> "Τοπικό public client"
    else -> "Production public client"
}

private fun networkStatusLabel(status: NetworkStatus): String = when (status) {
    NetworkStatus.ONLINE -> "Συνδεδεμένο"
    NetworkStatus.OFFLINE -> "Χωρίς σύνδεση"
    NetworkStatus.UNKNOWN -> "Μη επιβεβαιωμένη σύνδεση"
}

private fun financeDiagnosticStatus(state: FinanceProductState): String = when (state) {
    FinanceProductState.Idle -> "Δεν έχει ξεκινήσει"
    FinanceProductState.Loading -> "Συγχρονισμός"
    FinanceProductState.AuthRejected -> "Απαιτεί νέα σύνδεση"
    is FinanceProductState.Failure -> "Μη διαθέσιμο"
    is FinanceProductState.Ready -> when {
        state.saving -> "Αποθήκευση"
        state.issue != null -> "Απαιτεί ανάκτηση"
        state.offline -> "Offline cache · ${state.pendingTransactionCount} εκκρεμείς"
        state.pendingTransactionCount > 0 -> "Συγχρονισμός · ${state.pendingTransactionCount} εκκρεμείς"
        else -> "Συγχρονισμένο"
    }
}

private fun authDiagnosticStatus(state: AuthShellUiState): String = when (state) {
    AuthShellUiState.Loading -> "Έλεγχος"
    is AuthShellUiState.Unconfigured -> "Μη ρυθμισμένο"
    is AuthShellUiState.Login -> "Απαιτεί σύνδεση"
    is AuthShellUiState.Mfa -> "Απαιτεί επαλήθευση λογαριασμού"
    is AuthShellUiState.PinEnrollment -> "Ρύθμιση τοπικού PIN"
    is AuthShellUiState.Locked -> "Η εφαρμογή είναι κλειδωμένη"
    is AuthShellUiState.Ready -> if (state.offline) {
        "Τοπικά διαθέσιμη · αναμονή επαλήθευσης σύνδεσης"
    } else {
        "Ενεργή και επαληθευμένη"
    }
}

@Composable
private fun FinanceLoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            CircularProgressIndicator()
            Text("Φόρτωση των οικονομικών δεδομένων…")
        }
    }
}

@Composable
private fun FinanceFailureScreen(
    message: String,
    retryable: Boolean,
    onRetry: () -> Unit,
    onLogout: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            Text("Δεν ήταν δυνατή η φόρτωση", style = MaterialTheme.typography.headlineSmall)
            Text(message)
            if (retryable) {
                MyFinHubPrimaryAction(
                    label = "Δοκιμή ξανά",
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                    icon = null,
                )
            }
            TextButton(onClick = onLogout) {
                Text("Αποσύνδεση")
            }
        }
    }
}
