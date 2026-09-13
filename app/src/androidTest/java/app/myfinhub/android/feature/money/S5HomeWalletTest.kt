package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.runtime.mutableStateOf
import app.myfinhub.android.ProductTestActivity
import app.myfinhub.android.designsystem.MyFinHubTheme
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityKind
import app.myfinhub.android.feature.home.HomeAttentionItem
import app.myfinhub.android.feature.home.HomeAttentionTone
import app.myfinhub.android.feature.home.ProductionHomeScreen
import app.myfinhub.android.feature.home.syntheticHomeUiState
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class S5HomeWalletTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ProductTestActivity>()

    @Test
    fun compactHome_limitsAttentionAndKeepsPrimaryRoutesReachable() {
        val openedAccounts = AtomicBoolean(false)
        val thirdAttention = HomeAttentionItem(
            id = "third",
            title = "Δεν πρέπει να εμφανίζεται",
            reason = "Τρίτη προτεραιότητα",
            dueLabel = "Αύριο",
            tone = HomeAttentionTone.INFO,
        )
        val state = syntheticHomeUiState().let { it.copy(attentionItems = it.attentionItems + thirdAttention) }

        composeRule.setContent {
            MyFinHubTheme {
                ProductionHomeScreen(
                    state = state,
                    onAction = {},
                    onOpenAttention = {},
                    onOpenSettings = {},
                    onOpenQuickEntry = {},
                    onOpenAllAccounts = { openedAccounts.set(true) },
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("Διαθέσιμα τώρα").assertIsDisplayed()
        composeRule.onNodeWithText("Νέα κίνηση").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Ρυθμίσεις").assertIsDisplayed()
        composeRule.onNodeWithText("Δεν πρέπει να εμφανίζεται").assertDoesNotExist()
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasText("Όλοι οι λογαριασμοί"))
        composeRule.onNodeWithText("Όλοι οι λογαριασμοί").performClick()
        assertTrue(openedAccounts.get())
    }

    @Test
    fun walletUsesCanonicalAvailableScopeAndOpensAccount() {
        val openedAccount = AtomicBoolean(false)
        val state = syntheticMoneyUiState().copy(
            accounts = syntheticMoneyAccounts() + MoneyAccount(
                id = "reserve",
                name = "Αποθεματικό",
                balance = 500.0,
                kind = "Τράπεζα",
                canonicalKind = "bank",
                excludeFromAvailable = true,
            ),
        )

        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = state,
                    onOpenAccount = { openedAccount.set(true) },
                    onOpenNetPosition = {},
                    onOpenCard = {},
                    onAddCard = {},
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("Διαθέσιμα για καθημερινή χρήση").assertIsDisplayed()
        composeRule.onNodeWithText("Καθημερινά").assertIsDisplayed()
        composeRule.onNodeWithText("Κύριος λογαριασμός").assertIsDisplayed().performClick()
        assertTrue(openedAccount.get())
    }

    @Test
    fun allAccountsRequestReturnsAnExistingWalletToAccountsSection() {
        val accountsRequest = mutableStateOf(0)
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = syntheticMoneyUiState(),
                    accountsRequest = accountsRequest.value,
                    onOpenAccount = {},
                    onOpenNetPosition = {},
                    onOpenCard = {},
                    onAddCard = {},
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("Κάρτες").performClick()
        composeRule.onNodeWithText("Νέα κάρτα").assertIsDisplayed()
        composeRule.runOnIdle { accountsRequest.value += 1 }
        composeRule.onNodeWithText("Διαθέσιμα για καθημερινή χρήση").assertIsDisplayed()
    }

    @Test
    fun netPositionShowsCanonicalAsOfDate() {
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalNetPositionScreen(
                    state = syntheticMoneyUiState().copy(asOfDate = "2026-09-13"),
                    onBack = {},
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("Εικόνα έως", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Περιουσιακά στοιχεία").assertIsDisplayed()
        composeRule.onNodeWithText("Υποχρεώσεις").assertIsDisplayed()
    }

    @Test
    fun accountLedgerShowsAccountRelativeTransferSigns() {
        val sourceAccount = MoneyAccount("source", "Κύριος", 500.0, "Τράπεζα", canonicalKind = "bank")
        val transfer = ActivityItem(
            id = "transfer",
            dateLabel = "Σήμερα",
            kind = ActivityKind.TRANSFER,
            title = "Μεταφορά",
            subtitle = "Προς αποταμίευση",
            amount = 20.0,
            accountLabel = "Κύριος → Αποταμίευση",
            category = null,
            rawDate = "2026-09-13",
            fromAccountId = "source",
            toAccountId = "saving",
        )

        composeRule.setContent {
            MyFinHubTheme {
                CanonicalAccountDetailScreen(
                    account = sourceAccount,
                    activityItems = accountActivityItems("source", listOf(transfer)),
                    onBack = {},
                    onOpenActivity = {},
                    referenceDate = LocalDate.of(2026, 9, 13),
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNodeWithText("-20,00", substring = true).assertIsDisplayed()
    }

    @Test
    fun accountLedgerHidesAllAmountsWhenPrivacyIsEnabled() {
        val account = MoneyAccount("source", "Κύριος", 500.0, "Τράπεζα", canonicalKind = "bank")
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalAccountDetailScreen(
                    account = account,
                    activityItems = emptyList(),
                    onBack = {},
                    onOpenActivity = {},
                    referenceDate = LocalDate.of(2026, 9, 13),
                    amountsVisibleOverride = false,
                )
            }
        }
        assertTrue(composeRule.onAllNodesWithText("•••• €").fetchSemanticsNodes().isNotEmpty())
    }
}
