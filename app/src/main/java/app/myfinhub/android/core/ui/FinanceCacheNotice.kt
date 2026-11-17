package app.myfinhub.android.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import app.myfinhub.android.designsystem.MyFinHubSpacing

/**
 * Finance cache provenance is not a sync timestamp. Show this only while the
 * canonical finance state comes from an authenticated encrypted local cache
 * without a newly accepted server read. All three primary destinations use it.
 */
@Composable
fun FinanceCacheNotice(
    pendingFinanceChanges: Int,
    modifier: Modifier = Modifier,
    dataWarning: String = "Τα υπόλοιπα μπορεί να μην είναι ενημερωμένα.",
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(MyFinHubSpacing.md),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
        ) {
            Text(
                "Αποθηκευμένα οικονομικά στοιχεία",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Η υπηρεσία δεν έχει επιβεβαιώσει νέα ανάγνωση. $dataWarning",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pendingFinanceChanges > 0) {
                Text(
                    "Εκκρεμούν $pendingFinanceChanges τοπικές αλλαγές για συγχρονισμό.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
