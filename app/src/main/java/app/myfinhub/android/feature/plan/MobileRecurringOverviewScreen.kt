package app.myfinhub.android.feature.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.rememberAmountVisibilityPreference
import java.text.NumberFormat
import java.util.Locale

/**
 * Android only reads recurring obligations. Editing cadence, pauses and templates remains Web-only.
 * Source rows come from the same canonical Plan projection, never a second local copy.
 */
@Composable
fun MobileRecurringOverviewScreen(
    items: List<RecurringManageItem>,
    onBack: () -> Unit,
) {
    val amountsVisible = rememberAmountVisibilityPreference()
    val money = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR"))
    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Πάγια και συνδρομές",
                subtitle = "Προβολή · διαχείριση από το Web",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("mobile_recurring_overview"),
            contentPadding = PaddingValues(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = padding.calculateTopPadding() + MyFinHubSpacing.sm,
                bottom = MyFinHubDesignMetrics.productSnackbarBottomClearance,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            if (items.isEmpty()) {
                item("empty") {
                    Text("Δεν υπάρχουν καταγεγραμμένες επαναλαμβανόμενες υποχρεώσεις.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(items, key = RecurringManageItem::id) { recurring ->
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
                        Text(recurring.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (amountsVisible) money.format(recurring.amount) else "•••• €",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Text(
                            listOfNotNull(
                                recurring.cadenceLabel.takeIf(String::isNotBlank),
                                recurring.nextDueDateIso.takeIf(String::isNotBlank)?.let { "Επόμενη: $it" },
                                "Κατάσταση: " + recurring.status,
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
