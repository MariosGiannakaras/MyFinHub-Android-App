package app.myfinhub.android.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubSpacing

/**
 * Shared sibling selector for the Κινήσεις root. It keeps Ιστορικό and Ανάλυση inside one
 * top-level stack so drill-down Back returns to the exact originating analysis/history surface.
 */
@Composable
internal fun MovementRootSurface(
    selected: ActivitySection,
    onHistory: () -> Unit,
    onAnalysis: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Row(
                modifier = Modifier.padding(
                    horizontal = MyFinHubDesignMetrics.screenHorizontalPadding,
                    vertical = MyFinHubSpacing.xs,
                ),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                FilterChip(
                    selected = selected == ActivitySection.HISTORY,
                    onClick = onHistory,
                    label = { Text("Ιστορικό") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("activity_section_history"),
                )
                FilterChip(
                    selected = selected == ActivitySection.ANALYSIS,
                    onClick = onAnalysis,
                    label = { Text("Ανάλυση") },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("activity_section_analysis"),
                )
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}