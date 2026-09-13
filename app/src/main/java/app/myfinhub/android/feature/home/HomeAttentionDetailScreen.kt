package app.myfinhub.android.feature.home

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
fun HomeAttentionDetailScreen(
    item: HomeAttentionItem?,
    onMarkReviewed: () -> Unit,
    onBack: () -> Unit,
    onOpenAction: () -> Unit = {},
) {
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
                    Text("Γιατί εμφανίζεται", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        item.reason,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val actionLabel = if (item.id == "transaction-review") "Άνοιγμα κινήσεων" else "Άνοιγμα πλάνου"
            MyFinHubPrimaryAction(
                label = actionLabel,
                onClick = onOpenAction,
                modifier = Modifier.fillMaxWidth(),
                icon = if (item.id == "transaction-review") MyFinHubIcons.Activity else MyFinHubIcons.Plan,
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
