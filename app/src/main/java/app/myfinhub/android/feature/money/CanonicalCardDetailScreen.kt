package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.security.SecureWindowProtection
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onReveal: () -> Unit = {},
    onHideSecrets: () -> Unit = {},
    onSaveCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onDeleteCvv: () -> Unit = {},
    onBack: () -> Unit,
) {
    val relevantState = when (secretState) {
        is CardSecretUiState.Hidden -> secretState.takeIf { it.cardId == null || it.cardId == card?.id }
        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Revealed -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Failure -> secretState.takeIf { it.cardId == card?.id }
        CardSecretUiState.AuthRejected -> secretState
    } ?: CardSecretUiState.Hidden(card?.id)

    SecureWindowProtection(active = relevantState is CardSecretUiState.Revealed)
    var cvvDraft by remember(card?.id) { mutableStateOf("") }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = card?.nickname ?: "Κάρτα",
                subtitle = provider?.institutionLabel ?: card?.kind ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubDesignMetrics.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (card == null) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Η κάρτα δεν είναι διαθέσιμη.")
                }
                return@Column
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (provider != null) {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(36.dp), contentDescription = provider.institutionLabel)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            provider?.institutionLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text("•••• ${card.last4}", style = MaterialTheme.typography.labelLarge)
                    }
                    Text(card.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }
            }

            if (isCredit) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Κινήσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Αγορές και πληρωμές που είναι πραγματικά συνδεδεμένες με αυτή την κάρτα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (card.activity.isEmpty()) {
                            Text("Δεν υπάρχουν ακόμη συνδεδεμένες κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            card.activity.take(20).forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MyFinHubIconBadge(
                                        icon = if (item.kind == MoneyCardActivityKind.PAYMENT) MyFinHubIcons.Income else MyFinHubIcons.Card,
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                        contentDescription = null,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    MyFinHubAmountText(
                                        text = formatSignedCardEuro(item.amount),
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                    )
                                }
                                if (index != card.activity.take(20).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "PAN/λήξη αποκαλύπτονται μόνο από το owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά σε κρυπτογραφημένο vault αυτής της συσκευής.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (relevantState) {
                        is CardSecretUiState.Hidden -> {
                            MyFinHubPrimaryAction(
                                label = "Αποκάλυψη ασφαλών στοιχείων",
                                onClick = onReveal,
                                modifier = Modifier.fillMaxWidth(),
                                icon = null,
                            )
                            Text(
                                "Η οθόνη και το recent-app thumbnail προστατεύονται μόνο όσο εμφανίζονται τα πραγματικά στοιχεία.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        is CardSecretUiState.Loading -> {
                            CircularProgressIndicator()
                            Text("Ανάκτηση ασφαλών στοιχείων…")
                        }
                        is CardSecretUiState.Failure -> {
                            Text(relevantState.message, color = MaterialTheme.colorScheme.error)
                            if (relevantState.retryable) {
                                MyFinHubPrimaryAction(
                                    label = "Δοκιμή ξανά",
                                    onClick = onReveal,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = null,
                                )
                            }
                        }
                        CardSecretUiState.AuthRejected -> {
                            Text("Η ασφαλής συνεδρία δεν είναι πλέον έγκυρη. Θα χρειαστεί νέα σύνδεση.", color = MaterialTheme.colorScheme.error)
                        }
                        is CardSecretUiState.Revealed -> {
                            SecretValue("PAN", relevantState.pan ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("Λήξη", relevantState.expiry ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("CVV", relevantState.cvv ?: "Δεν έχει αποθηκευτεί στη συσκευή")
                            TextButton(onClick = onHideSecrets) { Text("Απόκρυψη στοιχείων") }
                            MyFinHubOutlinedField(
                                value = cvvDraft,
                                onValueChange = { input -> cvvDraft = input.filter { it in '0'..'9' }.take(4) },
                                label = "Νέο CVV για αυτή τη συσκευή",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            MyFinHubPrimaryAction(
                                label = if (relevantState.cvvSaving) "Αποθήκευση…" else "Αποθήκευση CVV στη συσκευή",
                                onClick = {
                                    val chars = cvvDraft.toCharArray()
                                    cvvDraft = ""
                                    onSaveCvv(chars)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !relevantState.cvvSaving && cvvDraft.length in 3..4,
                                icon = null,
                            )
                            if (relevantState.cvv != null) {
                                MyFinHubDestructiveTextAction(
                                    label = "Διαγραφή τοπικού CVV",
                                    onClick = onDeleteCvv,
                                    enabled = !relevantState.cvvSaving,
                                )
                            }
                            relevantState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecretValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(MyFinHubDesignMetrics.secretValueLabelWidth), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatCardEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedCardEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatCardEuro(value)
