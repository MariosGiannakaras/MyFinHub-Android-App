package app.myfinhub.android.feature.home

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.AmountVisibilityPreference
import app.myfinhub.android.feature.utilities.AppAppearancePreference
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HomeAttentionDetailScreen(
    item: HomeAttentionItem?,
    onMarkReviewed: () -> Unit,
    onBack: () -> Unit,
    onOpenAction: () -> Unit = {},
    amountsVisibleOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.applicationContext.getSharedPreferences(AppAppearancePreference.PREFERENCES_NAME, Context.MODE_PRIVATE)
    }
    var storedAmountsVisible by remember(context) { mutableStateOf(AmountVisibilityPreference.read(context)) }
    val amountsVisible = amountsVisibleOverride ?: storedAmountsVisible
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == AmountVisibilityPreference.KEY) storedAmountsVisible = AmountVisibilityPreference.read(context)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MyFinHubScreenHeader(
                title = "Χρειάζεται προσοχή",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
        ) {
            if (item == null) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Αυτό το στοιχείο δεν είναι πλέον διαθέσιμο.")
                }
                return@Column
            }

            val urgent = item.tone == HomeAttentionTone.URGENT
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                verticalAlignment = Alignment.Top,
            ) {
                MyFinHubIconBadge(
                    icon = MyFinHubIcons.Attention,
                    tone = if (urgent) FinanceTone.Attention else FinanceTone.Neutral,
                    contentDescription = null,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        text = if (urgent) "Άμεσος έλεγχος · ${item.dueLabel}" else item.dueLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Text("Στοιχεία υποχρέωσης", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    item.sourceLabel?.let { AttentionDetailValue("Πηγή", it) }
                    AttentionDetailValue("Ημερομηνία", item.dueDateLabel ?: item.dueLabel)
                    item.amount?.let { amount ->
                        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
                            Text("Ποσό", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MyFinHubAmountText(
                                text = if (amountsVisible) formatAttentionEuro(amount) else "•••• €",
                                tone = FinanceTone.Expense,
                                style = MaterialTheme.typography.titleLarge,
                            )
                        }
                    }
                    Text("Γιατί εμφανίζεται", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        item.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val opensActivity = item.action == HomeAttentionAction.ACTIVITY
            val actionLabel = if (opensActivity) "Άνοιγμα κινήσεων" else "Άνοιγμα πλάνου"
            MyFinHubPrimaryAction(
                label = actionLabel,
                onClick = onOpenAction,
                modifier = Modifier.fillMaxWidth(),
                icon = if (opensActivity) MyFinHubIcons.Activity else MyFinHubIcons.Plan,
            )
            Text(
                "Το άνοιγμα της σχετικής ενότητας δεν καταχωρίζει πληρωμή και δεν αλλάζει οικονομικά δεδομένα από μόνο του.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MyFinHubOutlinedAction(
                label = "Απόκρυψη για τώρα",
                onClick = onMarkReviewed,
                modifier = Modifier.fillMaxWidth(),
                icon = null,
            )
            Text(
                "Η απόκρυψη αφορά την τρέχουσα προβολή. Δεν σημαίνει ότι η υποχρέωση πληρώθηκε ή ολοκληρώθηκε.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AttentionDetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatAttentionEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)
