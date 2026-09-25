package app.myfinhub.android.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight

@Composable
fun MyFinHubActionCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MyFinHubDesignMetrics.cardElevation),
        border = BorderStroke(
            MyFinHubDesignMetrics.cardBorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MyFinHubDesignMetrics.cardContentPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            content = content,
        )
    }
}

@Composable
fun MyFinHubBackButton(onBack: () -> Unit) {
    IconButton(
        onClick = onBack,
        modifier = Modifier.size(MyFinHubDesignMetrics.minimumTouchTarget),
    ) {
        Icon(
            imageVector = MyFinHubIcons.Back,
            contentDescription = "Πίσω",
            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
        )
    }
}

@Composable
fun MyFinHubSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    tone: FinanceTone = FinanceTone.Neutral,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let {
            MyFinHubIconBadge(
                icon = it,
                tone = tone,
                contentDescription = null,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun MyFinHubSystemState(
    title: String,
    message: String,
    icon: ImageVector = MyFinHubIcons.Attention,
    tone: FinanceTone = FinanceTone.Neutral,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MyFinHubSectionCard(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
            MyFinHubIconBadge(icon = icon, tone = tone, contentDescription = null)
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (primaryLabel != null && onPrimary != null) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
                ) {
                    Text(primaryLabel)
                }
            }
            if (secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.align(Alignment.End)) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
