package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubActionCard
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSectionHeading
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

/** Production plan surface: canonical obligations, canonical overall budget, and derived forecast. */
@Composable
fun CanonicalPlanScreen(
    state: PlanUiState,
    onOpenBudget: () -> Unit,
) {
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πλάνο",
                subtitle = "Συγχρονισμένες υποχρεώσεις και budget",
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("plan_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = MyFinHubSpacing.lg,
                top = MyFinHubSpacing.xs,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                MyFinHubSectionHeading(
                    title = "Επόμενες υποχρεώσεις",
                    subtitle = "Μόνο στοιχεία που υπάρχουν στα συγχρονισμένα δεδομένα",
                    icon = MyFinHubIcons.Plan,
                    tone = FinanceTone.Attention,
                )
            }
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    if (state.items.isEmpty()) {
                        Text(
                            "Δεν υπάρχουν προγραμματισμένες ή επαναλαμβανόμενες υποχρεώσεις.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                            state.items.forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .semantics(mergeDescendants = true) {},
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MyFinHubIconBadge(
                                        icon = if (item.kind == PlannedKind.RECURRING) {
                                            MyFinHubIcons.Plan
                                        } else {
                                            MyFinHubIcons.Attention
                                        },
                                        tone = FinanceTone.Attention,
                                        contentDescription = null,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            item.dueLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    MyFinHubAmountText(
                                        text = formatCanonicalPlanEuro(item.amount),
                                        tone = FinanceTone.Expense,
                                    )
                                }
                                if (index != state.items.lastIndex) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                }
                            }
                        }
                    }
                }
            }

            item {
                MyFinHubActionCard(onClick = onOpenBudget, modifier = Modifier.fillMaxWidth()) {
                    MyFinHubSectionHeading(
                        title = "Μηνιαίο budget",
                        subtitle = "Το συνολικό όριο αποθηκεύεται στον λογαριασμό σου",
                        icon = MyFinHubIcons.Savings,
                        tone = FinanceTone.Savings,
                    )
                    val amount = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
                    if (amount != null && amount > 0.0) {
                        MyFinHubAmountText(
                            text = formatCanonicalPlanEuro(amount),
                            tone = FinanceTone.Savings,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            "Ειδοποίηση στο ${state.budget.alertThresholdText}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "Δεν έχει οριστεί συνολικό budget.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        MyFinHubSectionHeading(
                            title = "Πρόβλεψη",
                            subtitle = "Διαθέσιμα μετά τις καταγεγραμμένες επόμενες κινήσεις",
                            icon = MyFinHubIcons.Insights,
                            tone = FinanceTone.Transfer,
                        )
                        MyFinHubAmountText(
                            text = formatCanonicalPlanEuro(state.forecastEndBalance),
                            tone = if (state.forecastEndBalance >= 0.0) FinanceTone.Income else FinanceTone.Expense,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Text(
                            "Η πρόβλεψη βασίζεται μόνο στα τρέχοντα διαθέσιμα και στις συγχρονισμένες εκκρεμείς κινήσεις. Δεν προστίθενται υποθετικά 30/60/90 ημερών σενάρια.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** The only production-editable Plan surface until other canonical mutations are defined. */
@Composable
fun CanonicalBudgetScreen(
    state: PlanUiState,
    onAction: (PlanAction) -> Unit,
    onBack: () -> Unit,
) {
    val limit = state.budget.monthlyLimitText.replace(',', '.').toDoubleOrNull()
    val threshold = state.budget.alertThresholdText.toIntOrNull()
    val limitError = state.message == "Το μηνιαίο όριο πρέπει να είναι μεγαλύτερο από μηδέν."
    val thresholdError = state.message == "Το όριο ειδοποίησης πρέπει να είναι από 1 έως 100%."

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Μηνιαίο budget",
                subtitle = "Συγχρονισμένο συνολικό όριο",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                        MyFinHubSectionHeading(
                            title = "Συνολικό μηνιαίο όριο",
                            subtitle = "Αφορά το συνολικό budget, όχι προσωρινά budgets ανά κατηγορία",
                            icon = MyFinHubIcons.Savings,
                            tone = FinanceTone.Savings,
                        )
                        MyFinHubOutlinedField(
                            value = state.budget.monthlyLimitText,
                            onValueChange = { onAction(PlanAction.MonthlyLimitChanged(it)) },
                            label = "Μηνιαίο όριο",
                            suffix = { Text("€") },
                            errorMessage = state.message.takeIf { limitError },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = ImeAction.Next,
                            ),
                        )
                        MyFinHubOutlinedField(
                            value = state.budget.alertThresholdText,
                            onValueChange = {
                                onAction(PlanAction.AlertThresholdChanged(it.filter(Char::isDigit).take(3)))
                            },
                            label = "Ειδοποίηση στο",
                            suffix = { Text("%") },
                            errorMessage = state.message.takeIf { thresholdError },
                            supportingText = if (thresholdError) null else "Από 1 έως 100%",
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                        )
                        if (limit != null && limit > 0.0 && threshold != null && threshold in 1..100) {
                            Text(
                                "Θα ειδοποιείσαι όταν οι δαπάνες πλησιάσουν το $threshold% των ${formatCanonicalPlanEuro(limit)}.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        MyFinHubPrimaryAction(
                            label = "Αποθήκευση budget",
                            onClick = { onAction(PlanAction.SaveBudget) },
                            modifier = Modifier.fillMaxWidth(),
                            icon = MyFinHubIcons.Savings,
                        )
                        state.message?.takeUnless { limitError || thresholdError }?.let { message ->
                            Text(
                                message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            item {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text(
                            "Budgets ανά κατηγορία και κανόνες",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Δεν εμφανίζονται editable controls μέχρι να υπάρχει αντίστοιχη canonical αποθήκευση. Έτσι μια τοπική αλλαγή δεν μπορεί να παρουσιαστεί κατά λάθος ως συγχρονισμένη.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun formatCanonicalPlanEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)