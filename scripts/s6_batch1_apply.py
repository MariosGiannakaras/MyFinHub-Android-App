from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one anchor, found {count}")
    return text.replace(old, new, 1)


# Wallet cards: keep the S5 flat list, but make row identity/debt explicit and stable.
p = Path("app/src/main/java/app/myfinhub/android/feature/money/WalletAccountsScreens.kt")
s = p.read_text()
marker = "private fun WalletCardRow(card: MoneyCard, amountsVisible: Boolean, onClick: () -> Unit) {"
if s.count(marker) != 1:
    raise SystemExit("WalletCardRow marker mismatch")
head, tail = s.split(marker, 1)
tail = replace_once(
    tail,
    "    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {",
    '''    val spokenIdentity = buildString {
        append(card.nickname)
        if (card.last4.isNotBlank()) append(", τελευταία ψηφία ${card.last4}")
        if (identity.isNotBlank()) append(", $identity")
        if (debtText != null) append(", οφειλή $debtText")
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wallet_card_${card.id}")
            .semantics(mergeDescendants = true) { contentDescription = spokenIdentity },
        color = MaterialTheme.colorScheme.background,
    ) {''',
    "WalletCardRow surface",
)
tail = replace_once(
    tail,
    "                debtText?.let { MyFinHubAmountText(it, FinanceTone.Neutral, modifier = Modifier.align(Alignment.End)) }",
    '''                debtText?.let {
                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.align(Alignment.End)) {
                        Text("Οφειλή", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(it, FinanceTone.Neutral)
                    }
                }''',
    "WalletCardRow large-font debt",
)
tail = replace_once(
    tail,
    "                debtText?.let { MyFinHubAmountText(it, FinanceTone.Neutral) }",
    '''                debtText?.let {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Οφειλή", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MyFinHubAmountText(it, FinanceTone.Neutral)
                    }
                }''',
    "WalletCardRow compact debt",
)
p.write_text(head + marker + tail)


# Card detail: stable ID-based switching, precise credit semantics, one primary action,
# and bounded card-scoped activity with an explicit expansion control.
p = Path("app/src/main/java/app/myfinhub/android/feature/money/CanonicalCardDetailScreen.kt")
s = p.read_text()
s = replace_once(
    s,
    "import androidx.compose.material3.CircularProgressIndicator\n",
    "import androidx.compose.material3.CircularProgressIndicator\n"
    "import androidx.compose.material3.ExperimentalMaterial3Api\n"
    "import androidx.compose.material3.ModalBottomSheet\n"
    "import androidx.compose.material3.OutlinedButton\n"
    "import androidx.compose.material3.Surface\n",
    "Card detail imports",
)
s = replace_once(
    s,
    '''@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),''',
    '''@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    cards: List<MoneyCard> = listOfNotNull(card),
    onSelectCard: (String) -> Unit = {},
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),''',
    "Card detail signature",
)
s = replace_once(
    s,
    '''    var secretValidation by remember(card?.id) { mutableStateOf<String?>(null) }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true
''',
    '''    var secretValidation by remember(card?.id) { mutableStateOf<String?>(null) }
    var cardPickerOpen by remember(card?.id) { mutableStateOf(false) }
    var showAllActivity by remember(card?.id) { mutableStateOf(false) }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true
    val availableCredit = card?.let { activeCard ->
        activeCard.limit?.takeIf { isCredit && it > 0.0 }?.let { limit ->
            (limit - activeCard.currentBalance.coerceAtLeast(0.0)).coerceAtLeast(0.0)
        }
    }
''',
    "Card detail state",
)
s = replace_once(
    s,
    '''                    Text(card.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MyFinHubAmountText(formatCardEuro(card.currentBalance), FinanceTone.Expense, style = MaterialTheme.typography.titleLarge)
                            }
                            card.limit?.takeIf { it > 0.0 }?.let { limit ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Όριο", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatCardEuro(limit), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
''',
    '''                    Text(card.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (cards.size > 1) {
                        OutlinedButton(
                            onClick = { cardPickerOpen = true },
                            modifier = Modifier.fillMaxWidth().testTag("card_switcher"),
                        ) { Text("Αλλαγή κάρτας") }
                    }
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MyFinHubAmountText(formatCardEuro(card.currentBalance.coerceAtLeast(0.0)), FinanceTone.Expense, style = MaterialTheme.typography.titleLarge)
                            card.limit?.takeIf { it > 0.0 }?.let { limit ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Πιστωτικό όριο", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(formatCardEuro(limit), style = MaterialTheme.typography.titleMedium)
                                    }
                                    availableCredit?.let { available ->
                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("Διαθέσιμη πίστωση", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Text(formatCardEuro(available), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        }
                    }
''',
    "Card detail identity/credit",
)
s = replace_once(
    s,
    '''                        MyFinHubPrimaryAction(
                            label = "Καταχώριση αγοράς",
                            onClick = onAddPurchase,
                            modifier = Modifier.fillMaxWidth(),
                            icon = null,
                        )
                        MyFinHubPrimaryAction(
                            label = "Πληρωμή πιστωτικής",
                            onClick = onPayCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = card.currentBalance > 0.005,
                            icon = null,
                        )
''',
    '''                        MyFinHubPrimaryAction(
                            label = "Πληρωμή κάρτας",
                            onClick = onPayCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = card.currentBalance > 0.005,
                            icon = null,
                        )
                        OutlinedButton(
                            onClick = onAddPurchase,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Καταχώριση αγοράς") }
''',
    "Card detail action hierarchy",
)
s = replace_once(
    s,
    '''                        } else {
                            card.activity.take(20).forEachIndexed { index, item ->
                                Row(
''',
    '''                        } else {
                            val visibleActivity = if (showAllActivity) card.activity else card.activity.take(5)
                            visibleActivity.forEachIndexed { index, item ->
                                Row(
''',
    "Card activity visible list",
)
s = replace_once(
    s,
    '''                                if (index != card.activity.take(20).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
''',
    '''                                if (index != visibleActivity.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                            if (card.activity.size > 5) {
                                TextButton(onClick = { showAllActivity = !showAllActivity }) {
                                    Text(if (showAllActivity) "Λιγότερες κινήσεις" else "Δες όλες τις κινήσεις (${card.activity.size})")
                                }
                            }
''',
    "Card activity expansion",
)
s = replace_once(
    s,
    '''    }
}

@Composable
private fun SecretValue''',
    '''    }

    if (cardPickerOpen) {
        ModalBottomSheet(onDismissRequest = { cardPickerOpen = false }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(
                    start = MyFinHubSpacing.lg,
                    end = MyFinHubSpacing.lg,
                    bottom = MyFinHubSpacing.xl,
                ),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                Text("Επίλεξε κάρτα", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                cards.forEach { option ->
                    Surface(
                        onClick = {
                            cardPickerOpen = false
                            if (option.id != card?.id) onSelectCard(option.id)
                        },
                        modifier = Modifier.fillMaxWidth().testTag("card_picker_${option.id}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (option.id == card?.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                    ) {
                        Row(
                            modifier = Modifier.padding(MyFinHubSpacing.md),
                            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val optionProvider = financialProvider(option.bankId, option.nickname)
                            if (optionProvider != null) {
                                MyFinHubProviderMark(optionProvider, modifier = Modifier.size(40.dp), contentDescription = null)
                            } else {
                                MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Neutral, null)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(option.nickname, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "•••• ${option.last4.ifBlank { "—" }} · ${option.kind}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretValue''',
    "Card detail stable picker",
)
p.write_text(s)


# Route stable card switching by replacing the current detail destination, not stacking it.
p = Path("app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt")
s = p.read_text()
s = replace_once(
    s,
    '''                        CanonicalCardDetailScreen(
                            card = card,
                            secretState = cardSecretState,''',
    '''                        CanonicalCardDetailScreen(
                            card = card,
                            cards = moneyState.cards,
                            onSelectCard = { selectedCardId ->
                                if (selectedCardId != route.cardId) {
                                    onHideCardSecrets()
                                    moneyBackStack.removeLastOrNull()
                                    moneyBackStack.pushIfNew(AppRoute.CardDetail(selectedCardId))
                                }
                            },
                            secretState = cardSecretState,''',
    "MyFinHubApp card detail wiring",
)
p.write_text(s)


# Focused device contract for the first coherent S6 batch.
test_path = Path("app/src/androidTest/java/app/myfinhub/android/feature/money/S6CardSurfaceTest.kt")
test_path.write_text(r'''package app.myfinhub.android.feature.money

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.myfinhub.android.designsystem.MyFinHubTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class S6CardSurfaceTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val debit = MoneyCard(
        id = "debit",
        nickname = "Καθημερινή",
        last4 = "4242",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "piraeus",
        canonicalKind = "debit",
    )

    private val credit = MoneyCard(
        id = "credit",
        nickname = "Πιστωτική ταξιδιών",
        last4 = "1881",
        kind = "Πιστωτική",
        currentBalance = 312.20,
        limit = 2_000.0,
        vaultState = VaultState.LOCKED,
        network = "MASTERCARD",
        bankId = "revolut",
        canonicalKind = "credit",
    )

    @Test
    fun walletCards_areFlatStableRows_andRouteByStableId() {
        var opened: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalWalletScreen(
                    state = MoneyUiState(cards = listOf(debit, credit)),
                    onOpenAccount = {},
                    onOpenNetPosition = {},
                    onOpenCard = { opened = it },
                    onAddCard = {},
                    onOpenLoans = {},
                    onOpenLending = {},
                    amountsVisibleOverride = true,
                )
            }
        }

        composeRule.onNode(hasText("Κάρτες") and hasClickAction()).performClick()
        composeRule.onNodeWithTag("wallet_card_debit").assertIsDisplayed()
        composeRule.onNodeWithTag("wallet_card_credit").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("credit", opened) }
    }

    @Test
    fun creditDetail_exposesCreditSemantics_primaryPay_andStableSwitcher() {
        var selected: String? = null
        composeRule.setContent {
            MyFinHubTheme {
                CanonicalCardDetailScreen(
                    card = credit,
                    cards = listOf(credit, debit),
                    onSelectCard = { selected = it },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Τρέχουσα οφειλή").assertIsDisplayed()
        composeRule.onNodeWithText("Πιστωτικό όριο").assertIsDisplayed()
        composeRule.onNodeWithText("Διαθέσιμη πίστωση").assertIsDisplayed()
        composeRule.onNodeWithText("Πληρωμή κάρτας").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Καταχώριση αγοράς").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("card_switcher").performScrollTo().performClick()
        composeRule.onNodeWithTag("card_picker_debit").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals("debit", selected) }
    }
}
''')
