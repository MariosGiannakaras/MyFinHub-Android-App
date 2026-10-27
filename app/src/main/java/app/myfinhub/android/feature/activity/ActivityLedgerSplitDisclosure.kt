package app.myfinhub.android.feature.activity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.util.Locale

/**
 * Web TransactionSplitDetails, in the real transaction list rather than only
 * the detail route. This is a display-only projection of canonical split parts:
 * no edit, recategorization, invented allocation, or navigation side effect.
 */
@Composable
internal fun ActivityLedgerSplitDisclosure(
    item: ActivityItem,
    amountsVisible: Boolean,
) {
    if (item.canonicalKind != "split" || item.splitParts.size < 2) return
    // Web closes the disclosure on a new parts array; this keyed state resets
    // when the original category/amount/label contents change.
    var open by remember(item.id, item.canonicalSourceOrdinal, item.splitParts) {
        mutableStateOf(false)
    }
    val key = "${item.id}_${item.canonicalSourceOrdinal}"
    Column(
        modifier = Modifier.fillMaxWidth()
            .padding(start = MyFinHubSpacing.lg, bottom = MyFinHubSpacing.xs)
            .testTag("activity_split_disclosure_$key"),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        TextButton(
            onClick = { open = !open },
            modifier = Modifier
                .testTag("activity_split_toggle_$key")
                .semantics {
                    contentDescription =
                        "${item.splitParts.size} μέρη · " +
                            if (open) "Απόκρυψη ανάλυσης" else "Προβολή ανάλυσης"
                },
        ) {
            Text("${item.splitParts.size} μέρη · " +
                if (open) "Απόκρυψη" else "Προβολή ανάλυσης")
        }
        if (open) {
            Column(
                modifier = Modifier.fillMaxWidth()
                    .testTag("activity_split_details_$key"),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                item.splitParts.forEachIndexed { index, part ->
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .testTag("activity_split_part_${key}_$index"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(part.displayLabel,
                                style = MaterialTheme.typography.bodyMedium)
                            // Web shows both user label and the taxonomy path.
                            Text(part.categoryPath,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            amountVisibilityText(
                                NumberFormat.getCurrencyInstance(
                                    Locale.forLanguageTag("el-GR")
                                ).format(part.amount),
                                amountsVisible,
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.testTag(
                                "activity_split_amount_${key}_$index"),
                        )
                    }
                }
            }
        }
    }
}
