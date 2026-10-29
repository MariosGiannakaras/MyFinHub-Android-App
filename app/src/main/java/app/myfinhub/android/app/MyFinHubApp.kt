package app.myfinhub.android.app

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.myfinhub.android.designsystem.MyFinHubTheme
import kotlinx.serialization.json.JsonObject
import app.myfinhub.android.designsystem.LocalFinancialProviderCatalog
import app.myfinhub.android.designsystem.LocalRecurringServiceAssets
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.feature.activity.ActivityAction
import app.myfinhub.android.feature.activity.ActivityEditScreen
import app.myfinhub.android.feature.activity.ActivityLedgerScreen
import app.myfinhub.android.feature.activity.ActivityReadDetailScreen
import app.myfinhub.android.feature.activity.ActivityUiState
import app.myfinhub.android.feature.activity.ActivityViewModel
import app.myfinhub.android.feature.home.HomeAction
import app.myfinhub.android.feature.home.HomeAttentionAction
import app.myfinhub.android.feature.home.HomeAttentionDetailScreen
import app.myfinhub.android.feature.home.HomeUiState
import app.myfinhub.android.feature.home.HomeViewModel
import app.myfinhub.android.feature.home.ProductionHomeScreen
import app.myfinhub.android.feature.insights.InsightsScreen
import app.myfinhub.android.feature.insights.InsightsUiState
import app.myfinhub.android.feature.insights.InsightsViewModel
import app.myfinhub.android.feature.money.CanonicalAccountDetailScreen
import app.myfinhub.android.feature.money.CanonicalCardCreateScreen
import app.myfinhub.android.feature.money.CanonicalCardDetailScreen
import app.myfinhub.android.feature.money.CanonicalCardSecureDetailsScreen
import app.myfinhub.android.feature.money.CardCreateRequest
import app.myfinhub.android.feature.money.CardSecretCleanupUiState
import app.myfinhub.android.feature.money.accountActivityItems
import app.myfinhub.android.feature.money.CanonicalLendingScreen
import app.myfinhub.android.feature.money.CanonicalLoansScreen
import app.myfinhub.android.feature.money.CanonicalNetPositionScreen
import app.myfinhub.android.feature.money.CanonicalWalletScreen
import app.myfinhub.android.feature.money.CanonicalSavingsScreen
import app.myfinhub.android.feature.money.CardSecretUiState
import app.myfinhub.android.feature.money.MoneyUiState
import app.myfinhub.android.feature.money.MoneyViewModel
import app.myfinhub.android.feature.plan.CanonicalBudget2026Screen
import app.myfinhub.android.feature.plan.CanonicalPlan2026Screen
import app.myfinhub.android.feature.plan.CanonicalPlanForecastScreen
import app.myfinhub.android.feature.plan.MobileRecurringOverviewScreen
import app.myfinhub.android.feature.plan.CanonicalScheduledManagementScreen
import app.myfinhub.android.feature.plan.PlannedKind
import app.myfinhub.android.feature.plan.ScheduledCreateRequest
import app.myfinhub.android.feature.plan.PlanUiState
import app.myfinhub.android.feature.plan.PlanAction
import app.myfinhub.android.feature.plan.PlanViewModel
import app.myfinhub.android.feature.plan.PlannedFlow
import app.myfinhub.android.feature.plan.PlannedItem
import app.myfinhub.android.feature.quickentry.ProductionQuickEntryScreen
import app.myfinhub.android.feature.quickentry.QuickEntryAction
import app.myfinhub.android.feature.quickentry.QuickEntryBackGuard
import app.myfinhub.android.feature.quickentry.QuickEntryKind
import app.myfinhub.android.feature.quickentry.QuickEntryUiState
import app.myfinhub.android.feature.quickentry.QuickEntryViewModel
import app.myfinhub.android.feature.utilities.AppDiagnosticsSnapshot
import app.myfinhub.android.feature.utilities.FrontendUtilitiesAction
import app.myfinhub.android.feature.utilities.FrontendUtilitiesUiState
import app.myfinhub.android.feature.utilities.NoticeHistoryScreen
import app.myfinhub.android.core.ui.PrivacySafeNoticeRecord
import app.myfinhub.android.feature.utilities.ProductionDiagnosticsScreen
import app.myfinhub.android.feature.utilities.MobileDeviceSettingsScreen
import app.myfinhub.android.feature.utilities.reduceFrontendUtilities

@Composable
fun MyFinHubApp(
    homeViewModel: HomeViewModel = viewModel(),
    activityViewModel: ActivityViewModel = viewModel(),
    quickEntryViewModel: QuickEntryViewModel = viewModel(),
    moneyViewModel: MoneyViewModel = viewModel(),
    planViewModel: PlanViewModel = viewModel(),
    insightsViewModel: InsightsViewModel = viewModel(),
) {
    val homeState by homeViewModel.state.collectAsStateWithLifecycle()
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val quickEntryState by quickEntryViewModel.state.collectAsStateWithLifecycle()
    val moneyState by moneyViewModel.state.collectAsStateWithLifecycle()
    val planState by planViewModel.state.collectAsStateWithLifecycle()
    val insightsState by insightsViewModel.state.collectAsStateWithLifecycle()

    MyFinHubTheme {
        MyFinHubAppContent(
            homeState = homeState,
            onHomeAction = homeViewModel::onAction,
            activityState = activityState,
            onActivityAction = activityViewModel::onAction,
            quickEntryState = quickEntryState,
            onQuickEntryAction = quickEntryViewModel::onAction,
            moneyState = moneyState,
            onDeleteCard = moneyViewModel::deleteCard,
            planState = planState,
            onPlanAction = planViewModel::onAction,
            insightsState = insightsState,
        )
    }
}

@Composable
internal fun MyFinHubAppContent(
    homeState: HomeUiState,
    onHomeAction: (HomeAction) -> Unit,
    accountMetadata: Map<String, AccountMetadataRecord> = emptyMap(),
    accountMetadataLoaded: Boolean = false,
    onSaveAccountIban: ((String, String?) -> Unit)? = null,
    accountMetadataSaving: Boolean = false,
    activityState: ActivityUiState = ActivityUiState(),
    activityHistoricalBalance: ((app.myfinhub.android.feature.activity.ActivityItem) -> Double?)? = null,
    onActivityAction: (ActivityAction) -> Unit = {},
    activityMutationInFlight: Boolean = false,
    activityMutationBlocked: Boolean = false,
    quickEntryState: QuickEntryUiState = QuickEntryUiState(),
    onQuickEntryAction: (QuickEntryAction) -> Unit = {},
    quickEntryMutationInFlight: Boolean = false,
    moneyState: MoneyUiState = MoneyUiState(),
    financialProviderCatalog: List<app.myfinhub.android.core.network.FinancialProviderRecord> = emptyList(),
    recurringServiceAssets: List<app.myfinhub.android.core.network.RecurringServiceAssetRecord> = emptyList(),
    backupFinanceDocument: JsonObject? = null,
    backupPendingChangeCount: Int = 0,
    backupMutationBlocked: Boolean = false,
    backupOffline: Boolean = false,
    deviceSessions: DeviceSessionsUiSnapshot = DeviceSessionsUiSnapshot(),
    serverHistory: app.myfinhub.android.core.network.ServerFinanceHistorySummary? = null,
    serverHistoryEnabled: Boolean = false,
    serverHistoryLoaded: Boolean = false,
    onRefreshServerHistory: () -> Unit = {},
    accountSecurityEnabled: Boolean = false,
    accountSecuritySaving: Boolean = false,
    accountSecurityFeedback: String? = null,
    onChangeAccountSecurity: ((app.myfinhub.android.core.network.WebAccountSecurityChange) -> Unit)? = null,
    onRevokeDeviceSession: ((String) -> Unit)? = null,
    cardSecretState: CardSecretUiState = CardSecretUiState.Hidden(),
    cardSecretCleanupState: CardSecretCleanupUiState = CardSecretCleanupUiState.Idle,
    onCardDetailOpened: (String) -> Unit = {},
    onCardDetailClosed: (String) -> Unit = {},
    onRevealCardSecrets: () -> Unit = {},
    onHideCardSecrets: () -> Unit = {},
    onSaveServerCardSecrets: (CharArray, CharArray) -> Unit = { pan, expiry -> pan.fill('\u0000'); expiry.fill('\u0000') },
    onSaveCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onSaveCardDetails: (String, CharArray, CharArray, CharArray) -> Unit = { _, pan, expiry, cvv ->
        pan.fill('\u0000'); expiry.fill('\u0000'); cvv.fill('\u0000')
    },
    onRetryCardSecretCleanup: (String) -> Unit = {},
    onDeleteCard: (String) -> Unit = {},
    onRestoreCard: (String) -> Unit = {},
    onCreateCard: (CardCreateRequest) -> Unit = {},
    onSaveCardProfile: ((app.myfinhub.android.feature.money.CardProfileEditRequest) -> Unit)? = null,
    onSaveCreditBillingSettings: ((app.myfinhub.android.feature.money.CreditBillingSettingsRequest) -> Unit)? = null,
    onAddCreditRateVersion: ((app.myfinhub.android.feature.money.CreditRateVersionRequest) -> Unit)? = null,
    planState: PlanUiState = PlanUiState(),
    onPlanAction: (PlanAction) -> Unit = {},
    onSaveBudget: (String, String) -> Unit = { _, _ -> },
    onSaveCategoryBudget: (app.myfinhub.android.feature.plan.CategoryBudgetEditRequest) -> Unit = {},
    onDeleteCategoryBudget: (String) -> Unit = {},
    onSaveSubcategoryBudget: (app.myfinhub.android.feature.plan.SubcategoryBudgetEditRequest) -> Unit = {},
    onDeleteSubcategoryBudget: (String) -> Unit = {},
    onDeleteOverallBudget: (String) -> Unit = {},
    onSaveMonthlySavingsRate: ((String) -> Unit)? = null,
    onToggleTransactionRule: ((String, Boolean) -> Unit)? = null,
    onSaveTransactionRule: ((app.myfinhub.android.feature.utilities.TransactionRuleEditRequest) -> Unit)? = null,
    onSaveCounterparty: ((app.myfinhub.android.feature.utilities.CanonicalCounterpartyEditRequest) -> Unit)? = null,
    onSaveManagedAccount: ((app.myfinhub.android.feature.utilities.ManagedAccountEditRequest) -> Unit)? = null,
    onDeleteUnusedCustomCashAccount: ((String) -> Unit)? = null,
    onReorderCategoryTaxonomy: ((String, String, String?, Int) -> Unit)? = null,
    onAddCategoryTaxonomy: ((String, String, String?) -> Unit)? = null,
    onRenameCategoryTaxonomy: ((String, String, String?, String) -> Unit)? = null,
    onMoveSubcategoryTaxonomy: ((String, String, String, String) -> Unit)? = null,
    onRetireCategoryTaxonomy: ((String, String, String?) -> Unit)? = null,
    onSetCategoryIconColor: ((String, String, String?, String?) -> Unit)? = null,
    onSetCategoryIconPack: ((String) -> Unit)? = null,
    onSetCategoryIconGlyph: ((String, String, String?, String?) -> Unit)? = null,
    onSetDefaultManagedAccount: ((String, String) -> Unit)? = null,
    onLinkActivityCounterparty: ((String, String?) -> Unit)? = null,
    onDeleteTransactionRule: ((String) -> Unit)? = null,
    onReorderTransactionRule: ((String, Int) -> Unit)? = null,
    onSetRecurringStatus: (String, String) -> Unit = { _, _ -> },
    onCreateRecurring: (app.myfinhub.android.feature.plan.RecurringCreateRequest) -> Unit = {},
    onEditRecurring: (String, app.myfinhub.android.feature.plan.RecurringCreateRequest) -> Unit = { _, _ -> },
    onCreateScheduled: (ScheduledCreateRequest) -> Unit = {},
    onCompleteScheduled: (app.myfinhub.android.feature.plan.ScheduledCompletionRequest) -> Unit = {},
    onCloseScheduled: (String, String) -> Unit = { _, _ -> },
    onRecordLoanPayment: (String, String, String, Int, Double?, String?) -> Unit = { _, _, _, _, _, _ -> },
    onSaveLoanMetadata: ((app.myfinhub.android.feature.money.LoanMetadataEditRequest) -> Unit)? = null,
    onSaveSavingsGoal: ((String?, String, String, String?) -> Unit)? = null,
    onDeleteSavingsGoal: ((String) -> Unit)? = null,
    planMutationInFlight: Boolean = false,
    planMutationBlocked: Boolean = false,
    insightsState: InsightsUiState = InsightsUiState(),
    diagnostics: AppDiagnosticsSnapshot? = null,
    noticeHistory: List<PrivacySafeNoticeRecord> = emptyList(),
    onLogout: (() -> Unit)? = null,
) {
    // Display server-owned provider names only for exact canonical provider IDs.
    // Never use a matching label as a surrogate owner ID or load an unverified URL.
    val homeProviderState = remember(homeState, financialProviderCatalog) {
        homeState.copy(accounts = homeState.accounts.map { account ->
            account.copy(institution = canonicalCatalogProviderName(
                account.providerId, account.institution, financialProviderCatalog,
            ))
        })
    }
    val moneyProviderState = remember(moneyState, financialProviderCatalog) {
        moneyState.copy(accounts = moneyState.accounts.map { account ->
            account.copy(institution = canonicalCatalogProviderName(
                account.providerId, account.institution, financialProviderCatalog,
            ))
        })
    }

    var currentDestination by rememberSaveable { mutableStateOf(TopLevelDestination.HOME) }
    var walletAccountsRequest by rememberSaveable { mutableStateOf(0) }
    var insightsPeriodId by rememberSaveable { mutableStateOf(insightsState.defaultPeriodId) }
    var frontendUtilitiesState by remember { mutableStateOf(FrontendUtilitiesUiState()) }

    val onFrontendUtilitiesAction: (FrontendUtilitiesAction) -> Unit = { action ->
        frontendUtilitiesState = reduceFrontendUtilities(frontendUtilitiesState, action)
    }
    val homeBackStack = rememberNavBackStack(AppRoute.Home)
    val activityBackStack = rememberNavBackStack(AppRoute.Activity)
    val moneyBackStack = rememberNavBackStack(AppRoute.Money)
    val planBackStack = rememberNavBackStack(AppRoute.Plan)
    val insightsBackStack = rememberNavBackStack(AppRoute.Insights)

    val activeBackStack: NavBackStack<NavKey> = when (currentDestination) {
        TopLevelDestination.HOME -> homeBackStack
        TopLevelDestination.ACTIVITY -> activityBackStack
        TopLevelDestination.MONEY -> moneyBackStack
        TopLevelDestination.PLAN -> planBackStack
        TopLevelDestination.INSIGHTS -> insightsBackStack
    }

    fun openFastExpense(backStack: NavBackStack<NavKey>) {
        onQuickEntryAction(QuickEntryAction.Reset)
        onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE))
        backStack.pushIfNew(AppRoute.QuickEntry)
    }

    val navigationContent: @Composable () -> Unit = {
        CompositionLocalProvider(
            LocalFinancialProviderCatalog provides financialProviderCatalog,
            LocalRecurringServiceAssets provides recurringServiceAssets,
        ) {
        NavDisplay(
            backStack = activeBackStack,
            onBack = {
                if (activeBackStack.size > 1) activeBackStack.removeLastOrNull()
            },
            entryProvider = entryProvider {
                entry<AppRoute.Home> {
                    ProductionHomeScreen(
                        state = homeProviderState,
                        accountMetadata = accountMetadata,
                        accountMetadataLoaded = accountMetadataLoaded,
                        onAction = onHomeAction,
                        onOpenAttention = { id -> homeBackStack.pushIfNew(AppRoute.HomeAttention(id)) },
                        onOpenSettings = { homeBackStack.pushIfNew(AppRoute.Settings) },
                        onOpenQuickEntry = { openFastExpense(homeBackStack) },
                        onAccountQuickEntry = { accountId, role ->
                            onQuickEntryAction(QuickEntryAction.Reset)
                            if (role == app.myfinhub.android.feature.home.HomePrimaryRole.SAVINGS) {
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.SAVING))
                                onQuickEntryAction(QuickEntryAction.ToAccountChanged(accountId))
                            } else {
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE))
                                onQuickEntryAction(QuickEntryAction.AccountChanged(accountId))
                            }
                            homeBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                        onAccountTransactions = { accountId ->
                            onActivityAction(ActivityAction.ApplyFilters(
                                type = app.myfinhub.android.feature.activity.ActivityFilter.ALL,
                                accountId = accountId, category = null,
                                dateFrom = null, dateTo = null,
                            ))
                            activityBackStack.popToRoot()
                            currentDestination = TopLevelDestination.ACTIVITY
                        },
                        onOpenAccount = { accountId -> homeBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },
                        onOpenAllAccounts = { homeBackStack.pushIfNew(AppRoute.Accounts) },
                        onOpenRecent = { eventId -> homeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                    )
                }
                entry<AppRoute.HomeAttention> { route ->
                    HomeAttentionDetailScreen(
                        item = homeState.attentionItems.firstOrNull { it.id == route.attentionId },
                        onMarkReviewed = {
                            onHomeAction(HomeAction.DismissAttention(route.attentionId))
                            homeBackStack.removeLastOrNull()
                        },
                        onBack = { homeBackStack.removeLastOrNull() },
                        onOpenAction = {
                            homeBackStack.popToRoot()
                            val action = homeState.attentionItems
                                .firstOrNull { it.id == route.attentionId }
                                ?.action
                            if (action == HomeAttentionAction.ACTIVITY) {
                                currentDestination = TopLevelDestination.ACTIVITY
                                activityBackStack.popToRoot()
                            } else {
                                currentDestination = TopLevelDestination.PLAN
                                planBackStack.popToRoot()
                            }
                        },
                    )
                }
                entry<AppRoute.Settings> {
                    MobileDeviceSettingsScreen(
                        onBack = { homeBackStack.removeLastOrNull() },
                        onLogout = onLogout,
                    )
                }
                entry<AppRoute.Accounts> {
                    CanonicalWalletScreen(
                        state = moneyProviderState,
                        accountsOnly = true,
                        accountMetadata = accountMetadata,
                        accountMetadataLoaded = accountMetadataLoaded,
                        onOpenAccount = { id -> homeBackStack.pushIfNew(AppRoute.AccountDetail(id)) },
                        onOpenNetPosition = { homeBackStack.pushIfNew(AppRoute.NetPosition) },
                        onOpenCard = { id ->
                            currentDestination = TopLevelDestination.MONEY
                            moneyBackStack.pushIfNew(AppRoute.CardDetail(id))
                        },
                        onAddCard = {
                            currentDestination = TopLevelDestination.MONEY
                            moneyBackStack.pushIfNew(AppRoute.CardCreate)
                        },
                        onOpenLoans = { homeBackStack.pushIfNew(AppRoute.Loans) },
                        onOpenLending = { homeBackStack.pushIfNew(AppRoute.Lending) },
                    )
                }
                entry<AppRoute.NoticeHistory> {
                    NoticeHistoryScreen(
                        entries = noticeHistory,
                        onBack = { homeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Diagnostics> {
                    val snapshot = diagnostics
                    if (snapshot != null) {
                        ProductionDiagnosticsScreen(
                            diagnostics = snapshot,
                            onBack = { homeBackStack.removeLastOrNull() },
                        )
                    } else {
                        LaunchedEffect(Unit) { homeBackStack.removeLastOrNull() }
                    }
                }
                entry<AppRoute.Activity> {
                    ActivityLedgerScreen(
                            state = activityState,
                            categoryIconColors = planState.categoryIconColors,
                            subcategoryIconColors = planState.subcategoryIconColors,
                            categoryGlyphOverrides = planState.categoryGlyphOverrides,
                            onAction = onActivityAction,
                            onOpenDetail = { eventId -> activityBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                            onOpenQuickEntry = { openFastExpense(activityBackStack) },
                        )
                }
                entry<AppRoute.ActivityDetail> { route ->
                    val item = activityState.uniqueItem(route.eventId)
                    ActivityReadDetailScreen(
                        item = item,
                        accountOptions = activityState.accountOptions,
                        mutationBlocked = activityMutationBlocked ||
                            !activityState.isUniqueMutationTarget(route.eventId),
                        historicalBalance = item?.let { activityHistoricalBalance?.invoke(it) },
                        counterpartyRows = planState.counterpartyDirectory,
                        onChooseCounterparty = onLinkActivityCounterparty?.let { callback ->
                            { selectedId -> callback(route.eventId, selectedId) }
                        },
                        onBack = { activeBackStack.removeLastOrNull() },
                        onEdit = { activeBackStack.pushIfNew(AppRoute.ActivityEdit(route.eventId)) },
                        onDelete = { onActivityAction(ActivityAction.Delete(route.eventId)) },
                        onDeleted = { activeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.ActivityEdit> { route ->
                    val item = activityState.uniqueItem(route.eventId)
                    ActivityEditScreen(
                        item = item,
                        categoryOptions = item?.let(activityState::categoryOptionsFor).orEmpty(),
                        accountOptions = activityState.accountOptions,
                        incomeCategoryOptions = activityState.incomeCategories,
                        expenseCategoryOptions = activityState.expenseCategories,
                        categoryGlyphOverrides = planState.categoryGlyphOverrides,
                        mutationInFlight = activityMutationInFlight,
                        mutationBlocked = activityMutationBlocked ||
                            !activityState.isUniqueMutationTarget(route.eventId),
                        onBack = { activeBackStack.removeLastOrNull() },
                        onSave = { date, note, category, subcategory, legacy ->
                            onActivityAction(
                                ActivityAction.SaveEdit(
                                    id = route.eventId,
                                    note = note,
                                    category = category,
                                    date = date,
                                    subcategory = subcategory,
                                    legacy = legacy,
                                ),
                            )
                        },
                        onSaved = { activeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.CategoryActivity> { route ->
                    ActivityLedgerScreen(
                        categoryIconColors = planState.categoryIconColors,
                        subcategoryIconColors = planState.subcategoryIconColors,
                        categoryGlyphOverrides = planState.categoryGlyphOverrides,
                        state = activityState.forCategories(
                            categories = route.categories.ifEmpty { listOf(route.category) },
                            label = route.category,
                            start = route.start,
                            end = route.end,
                        ),
                        onAction = onActivityAction,
                        onOpenDetail = { eventId -> activeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                        onOpenQuickEntry = { openFastExpense(activeBackStack) },
                        onBack = { activeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.QuickEntry> {
                    QuickEntryBackGuard(
                        state = quickEntryState,
                        onAction = onQuickEntryAction,
                        onExit = { activeBackStack.removeLastOrNull() },
                    ) {
                        ProductionQuickEntryScreen(
                            state = quickEntryState,
                            onAction = onQuickEntryAction,
                            onBack = { activeBackStack.removeLastOrNull() },
                            mutationInFlight = quickEntryMutationInFlight,
                            categoryGlyphOverrides = planState.categoryGlyphOverrides,
                        )
                    }
                }
                entry<AppRoute.Money> {
                    CanonicalWalletScreen(
                        state = moneyProviderState,
                        cardsOnly = true,
                        onOpenAccount = { accountId -> moneyBackStack.pushIfNew(AppRoute.AccountDetail(accountId)) },
                        accountMetadata = accountMetadata,
                        accountMetadataLoaded = accountMetadataLoaded,
                        onOpenNetPosition = { moneyBackStack.pushIfNew(AppRoute.NetPosition) },
                        onOpenCard = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardDetail(cardId)) },
                        onPayCreditCard = { cardId ->
                            onQuickEntryAction(QuickEntryAction.Reset)
                            onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT))
                            onQuickEntryAction(QuickEntryAction.CardChanged(cardId))
                            moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                        onAddCreditPurchase = { cardId ->
                            onQuickEntryAction(QuickEntryAction.Reset)
                            onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE))
                            onQuickEntryAction(QuickEntryAction.CardChanged(cardId))
                            moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                        onOpenCreditActivity = { eventId -> activeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                        onAddCard = { moneyBackStack.pushIfNew(AppRoute.CardCreate) },
                        cardSecretState = cardSecretState,
                        onCardActiveChanged = onCardDetailOpened,
                        onDeleteCard = onDeleteCard,
                        onRestoreCard = onRestoreCard,
                        onOpenLoans = { moneyBackStack.pushIfNew(AppRoute.Loans) },
                        onOpenLending = { moneyBackStack.pushIfNew(AppRoute.Lending) },
                    )
                }
                entry<AppRoute.AccountDetail> { route ->
                    val account = moneyProviderState.accounts.firstOrNull { it.id == route.accountId }
                    CanonicalAccountDetailScreen(
                        account = account,
                        ibanRecord = accountMetadata[route.accountId],
                        accountMetadataLoaded = accountMetadataLoaded,
                        onSaveAccountIban = onSaveAccountIban,
                        accountMetadataSaving = accountMetadataSaving,
                        activityItems = accountActivityItems(route.accountId, activityState.items),
                        fullActivityIdCounts = activityState.items.groupingBy { it.id }.eachCount(),
                        categoryGlyphOverrides = planState.categoryGlyphOverrides,
                        onBack = { activeBackStack.removeLastOrNull() },
                        onOpenActivity = { eventId ->
                            if (activityState.isUniqueMutationTarget(eventId)) {
                                activeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId))
                            }
                        },
                        onNewTransaction = {
                            onQuickEntryAction(QuickEntryAction.Reset)
                            onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.EXPENSE))
                            onQuickEntryAction(QuickEntryAction.AccountChanged(route.accountId))
                            activeBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                    )
                }
                entry<AppRoute.NetPosition> {
                    CanonicalNetPositionScreen(
                        state = moneyProviderState,
                        onBack = { activeBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.CardCreate> {
                    CanonicalCardCreateScreen(
                        cards = moneyState.cards,
                        financialProviderCatalog = financialProviderCatalog,
                        onCreate = onCreateCard,
                        onSaveCardDetails = onSaveCardDetails,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.CardDetail> { route ->
                    val card = moneyState.cards.firstOrNull { it.id == route.cardId }
                    CanonicalCardDetailScreen(
                        cardId = route.cardId,
                        card = card,
                        cards = moneyState.cards,
                        statements = moneyState.creditStatementsByCard[route.cardId].orEmpty(),
                        onSelectCard = { selectedCardId ->
                            if (selectedCardId != route.cardId) {
                                moneyBackStack.removeLastOrNull()
                                moneyBackStack.pushIfNew(AppRoute.CardDetail(selectedCardId))
                            }
                        },
                        cleanupState = cardSecretCleanupState,
                        onRetryCleanup = onRetryCardSecretCleanup,
                        onOpenSecureDetails = { cardId -> moneyBackStack.pushIfNew(AppRoute.CardSecureDetails(cardId)) },
                        onRemoveCard = onDeleteCard,
                        onSaveCardProfile = onSaveCardProfile,
                        onSaveCreditBillingSettings = onSaveCreditBillingSettings,
                        onAddCreditRateVersion = onAddCreditRateVersion,
                        profileMutationBlocked = planMutationInFlight || planMutationBlocked,
                        cardProviderOptions = financialProviderCatalog.map {
                            it.id to it.displayName
                        },
                        onAddPurchase = {
                            onQuickEntryAction(QuickEntryAction.Reset)
                            onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE))
                            onQuickEntryAction(QuickEntryAction.CardChanged(route.cardId))
                            moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                        onPayCard = {
                            onQuickEntryAction(QuickEntryAction.Reset)
                            onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT))
                            onQuickEntryAction(QuickEntryAction.CardChanged(route.cardId))
                            moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                        },
                        onPayStatement = { statementId, remaining ->
                            val matching = moneyState.creditStatementsByCard[route.cardId].orEmpty()
                                .filter { it.id == statementId }
                            if (matching.size == 1 && matching.single().remaining > 0.005 &&
                                kotlin.math.abs(matching.single().remaining - remaining) < 0.005
                            ) {
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT))
                                onQuickEntryAction(QuickEntryAction.CardChanged(route.cardId))
                                onQuickEntryAction(QuickEntryAction.StatementChanged(statementId))
                                onQuickEntryAction(QuickEntryAction.AmountChanged(remaining.toString()))
                                moneyBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },
                        onBack = { moneyBackStack.removeLastOrNull() },
                        onOpenActivity = { eventId -> activeBackStack.pushIfNew(AppRoute.ActivityDetail(eventId)) },
                    )
                }
                entry<AppRoute.CardSecureDetails> { route ->
                    DisposableEffect(route.cardId) {
                        onCardDetailOpened(route.cardId)
                        onDispose {
                            onCardDetailClosed(route.cardId)
                        }
                    }
                    CanonicalCardSecureDetailsScreen(
                        cardId = route.cardId,
                        card = moneyState.cards.firstOrNull { it.id == route.cardId },
                        secretState = cardSecretState,
                        onReveal = onRevealCardSecrets,
                        onSaveServerSecrets = onSaveServerCardSecrets,
                        onSaveCvv = onSaveCvv,
                        onBack = { moneyBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Savings> {
                    CanonicalSavingsScreen(
                        state = moneyState,
                        onBack = { activeBackStack.removeLastOrNull() },
                        onSaveGoal = onSaveSavingsGoal,
                        onDeleteGoal = onDeleteSavingsGoal,
                        onSaveMonthlySavingsRate = onSaveMonthlySavingsRate,
                        mutationBlocked = planMutationInFlight || planMutationBlocked,
                    )
                }
                entry<AppRoute.Loans> {
                    CanonicalLoansScreen(
                        state = moneyState,
                        onBack = { activeBackStack.removeLastOrNull() },
                        onRecordPayment = onRecordLoanPayment,
                        onSaveLoanMetadata = onSaveLoanMetadata,
                        expenseCategories = planState.budgetCategories,
                        mutationBlocked = planMutationInFlight || planMutationBlocked,
                    )
                }
                entry<AppRoute.Lending> {
                    CanonicalLendingScreen(
                        state = moneyState,
                        onBack = { activeBackStack.removeLastOrNull() },
                        mutationBlocked = planMutationInFlight || planMutationBlocked,
                        onRecordRepayment = { lending ->
                            if (!planMutationInFlight && !planMutationBlocked &&
                                moneyState.lendingItems.count { it.personLabel == lending.personLabel } == 1 &&
                                lending.amount > 0.005
                            ) {
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.REPAYMENT))
                                onQuickEntryAction(QuickEntryAction.AmountChanged(lending.amount.toString()))
                                onQuickEntryAction(QuickEntryAction.PersonChanged(lending.personLabel))
                                if (lending.note.isNotBlank()) {
                                    onQuickEntryAction(QuickEntryAction.NoteChanged(lending.note))
                                }
                                activeBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },
                        onRecordLending = { person ->
                            if (!planMutationInFlight && !planMutationBlocked &&
                                person.isNotBlank() && (
                                    moneyState.lendingItems.any { it.personLabel == person } ||
                                    moneyState.lendingHistory.any { it.personLabel == person }
                                )
                            ) {
                                onQuickEntryAction(QuickEntryAction.Reset)
                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.LENDING))
                                onQuickEntryAction(QuickEntryAction.PersonChanged(person))
                                activeBackStack.pushIfNew(AppRoute.QuickEntry)
                            }
                        },
                    )
                }
                entry<AppRoute.Plan> {
                    CanonicalPlan2026Screen(
                        state = planState,
                        onOpenForecast = { planBackStack.pushIfNew(AppRoute.PlanForecast) },
                        onOpenBudget = { planBackStack.pushIfNew(AppRoute.PlanBudgets) },
                        onOpenRecurring = { planBackStack.pushIfNew(AppRoute.PlanRecurring) },
                        onOpenScheduled = { planBackStack.pushIfNew(AppRoute.PlanScheduled) },
                        onOpenSavings = { planBackStack.pushIfNew(AppRoute.Savings) },
                        onOpenLoans = { planBackStack.pushIfNew(AppRoute.Loans) },
                        onOpenLending = { planBackStack.pushIfNew(AppRoute.Lending) },
                    )
                }
                entry<AppRoute.PlanRecurring> {
                    MobileRecurringOverviewScreen(
                        items = planState.recurringManageItems,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.PlanScheduled> {
                    CanonicalScheduledManagementScreen(
                        state = planState,
                        onBack = { planBackStack.removeLastOrNull() },
                        onCreate = onCreateScheduled,
                        onComplete = onCompleteScheduled,
                        onClose = onCloseScheduled,
                        mutationBlocked = planMutationInFlight || planMutationBlocked,
                    )
                }
                entry<AppRoute.PlanForecast> {
                    CanonicalPlanForecastScreen(
                        state = planState,
                        onBack = { planBackStack.removeLastOrNull() },
                        onRecordItem = { item ->
                            if (item.kind == PlannedKind.SCHEDULED) {
                                // Never create an unrelated Quick Entry event and leave
                                // the scheduled charge pending in the forecast.
                                planBackStack.pushIfNew(AppRoute.PlanScheduled)
                            } else {
                                val actions = plannedItemQuickEntryPrefillActions(item, quickEntryState)
                                if (actions.isNotEmpty()) {
                                    actions.forEach(onQuickEntryAction)
                                    planBackStack.pushIfNew(AppRoute.QuickEntry)
                                }
                            }
                        },
                    )
                }
                entry<AppRoute.PlanBudgets> {
                    CanonicalBudget2026Screen(
                        state = planState,
                        onSaveBudget = onSaveBudget,
                        onShiftBudgetMonth = { delta ->
                            onPlanAction(PlanAction.ShiftBudgetMonth(delta))
                        },
                        onSelectBudgetMonth = { monthIso ->
                            onPlanAction(PlanAction.SelectBudgetMonth(monthIso))
                        },
                        onSaveCategoryBudget = onSaveCategoryBudget,
                        onDeleteCategoryBudget = onDeleteCategoryBudget,
                        onSaveSubcategoryBudget = onSaveSubcategoryBudget,
                        onDeleteSubcategoryBudget = onDeleteSubcategoryBudget,
                        onDeleteOverallBudget = onDeleteOverallBudget,
                        mutationInFlight = planMutationInFlight,
                        mutationBlocked = planMutationBlocked,
                        onBack = { planBackStack.removeLastOrNull() },
                    )
                }
                entry<AppRoute.Insights> {
                    InsightsScreen(
                        state = insightsState,
                        categoryGlyphOverrides = planState.categoryGlyphOverrides,
                        selectedPeriodId = insightsPeriodId,
                        onPeriodSelected = { insightsPeriodId = it },
                        onOpenSupportingActivity = {
                            currentDestination = TopLevelDestination.ACTIVITY
                            activityBackStack.popToRoot()
                        },
                        onOpenNetPosition = { insightsBackStack.pushIfNew(AppRoute.NetPosition) },
                        onOpenCategoryActivity = { category, start, end ->
                            insightsBackStack.pushIfNew(
                                AppRoute.CategoryActivity(
                                    category = category.name,
                                    start = start,
                                    end = end,
                                    categories = category.sourceCategories,
                                ),
                            )
                        },
                    )
                }
            },
        )
        }
    }

    val activeRoute = activeBackStack.lastOrNull() as? AppRoute
    if (activeRoute?.showsGlobalNavigation == true) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                TopLevelDestination.entries.forEach { destination ->
                    item(
                        selected = currentDestination == destination,
                        onClick = {
                            if (currentDestination == destination) {
                                activeBackStack.popToRoot()
                            } else {
                                currentDestination = destination
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.label),
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(destination.label),
                                maxLines = 2,
                                overflow = TextOverflow.Clip,
                            )
                        },
                        alwaysShowLabel = true,
                    )
                }
            },
        ) {
            navigationContent()
        }
    } else {
        navigationContent()
    }
}

internal fun plannedItemQuickEntryPrefillActions(
    item: PlannedItem,
    quickEntryState: QuickEntryUiState,
): List<QuickEntryAction> {
    val kind = when (item.flow) {
        PlannedFlow.OBLIGATION -> QuickEntryKind.EXPENSE
        PlannedFlow.INCOME -> QuickEntryKind.INCOME
        PlannedFlow.TRANSFER -> return emptyList()
    }
    val categoryOptions = when (kind) {
        QuickEntryKind.INCOME -> quickEntryState.incomeCategories
        else -> quickEntryState.expenseCategories
    }
    return buildList {
        add(QuickEntryAction.Reset)
        add(QuickEntryAction.SelectKind(kind))
        add(QuickEntryAction.AmountChanged(item.amount.toString()))
        if (item.category.isNotBlank() && categoryOptions.any { option -> option.name == item.category }) {
            add(QuickEntryAction.CategoryChanged(item.category))
        }
        add(QuickEntryAction.NoteChanged(item.note.ifBlank { item.title }))
    }
}

private fun NavBackStack<NavKey>.pushIfNew(route: NavKey) {
    if (lastOrNull() != route) add(route)
}

private fun NavBackStack<NavKey>.popToRoot() {
    while (size > 1) removeLastOrNull()
}
