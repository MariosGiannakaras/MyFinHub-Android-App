package app.myfinhub.android.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

enum class FinanceTone {
    Income,
    Expense,
    Savings,
    Transfer,
    Attention,
    Neutral,
}

data class FinanceToneColors(
    val accent: Color,
    val container: Color,
)

@Composable
fun financeToneColors(tone: FinanceTone): FinanceToneColors {
    val finance = MyFinHubThemeTokens.finance
    return when (tone) {
        FinanceTone.Income -> FinanceToneColors(finance.income, finance.incomeContainer)
        FinanceTone.Expense -> FinanceToneColors(finance.expense, finance.expenseContainer)
        FinanceTone.Savings -> FinanceToneColors(finance.savings, finance.savingsContainer)
        FinanceTone.Transfer -> FinanceToneColors(finance.transfer, finance.transferContainer)
        FinanceTone.Attention -> FinanceToneColors(finance.attention, finance.attentionContainer)
        FinanceTone.Neutral -> FinanceToneColors(finance.neutral, finance.neutralContainer)
    }
}

@Composable
fun MyFinHubScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    navigation: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Row(
            modifier = Modifier.padding(
                start = MyFinHubDesignMetrics.screenHorizontalPadding,
                top = MyFinHubDesignMetrics.screenTopPadding,
                end = MyFinHubDesignMetrics.screenHorizontalPadding,
                bottom = MyFinHubDesignMetrics.screenBottomPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navigation?.invoke()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.semantics { heading() },
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            trailing?.invoke(this)
        }
    }
}

@Composable
fun MyFinHubSectionCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MyFinHubDesignMetrics.cardContentPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val elevation = CardDefaults.cardElevation(defaultElevation = MyFinHubDesignMetrics.cardElevation)
    val border = BorderStroke(
        MyFinHubDesignMetrics.cardBorderWidth,
        MaterialTheme.colorScheme.outlineVariant,
    )
    val shape = MaterialTheme.shapes.medium
    if (onClick == null) {
        Card(
            modifier = modifier,
            colors = colors,
            elevation = elevation,
            border = border,
            shape = shape,
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = modifier,
            colors = colors,
            elevation = elevation,
            border = border,
            shape = shape,
        ) {
            Box(modifier = Modifier.padding(contentPadding)) {
                content()
            }
        }
    }
}

@Composable
fun MyFinHubIconBadge(
    icon: ImageVector,
    tone: FinanceTone,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val colors = financeToneColors(tone)
    Surface(
        modifier = modifier.size(MyFinHubDesignMetrics.iconBadgeSize),
        shape = MaterialTheme.shapes.small,
        color = colors.container,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = colors.accent,
                modifier = Modifier.size(MyFinHubDesignMetrics.iconBadgeIconSize),
            )
        }
    }
}

@Composable
fun MyFinHubFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    icon: ImageVector,
    tone: FinanceTone,
    modifier: Modifier = Modifier,
) {
    val colors = financeToneColors(tone)
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(MyFinHubDesignMetrics.compactIconSize),
            )
        },
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = colors.container,
            selectedLabelColor = colors.accent,
            selectedLeadingIconColor = colors.accent,
        ),
    )
}

@Composable
fun MyFinHubSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight),
        leadingIcon = {
            Icon(
                imageVector = MyFinHubIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
            )
        },
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraSmall,
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
fun MyFinHubAmountText(
    text: String,
    tone: FinanceTone,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = financeToneColors(tone).accent,
        maxLines = 1,
    )
}

@Composable
fun MyFinHubFinanceRow(
    icon: ImageVector,
    iconDescription: String?,
    title: String,
    subtitle: String,
    meta: String,
    amountText: String,
    tone: FinanceTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = MyFinHubDesignMetrics.cardElevation),
        border = BorderStroke(
            MyFinHubDesignMetrics.cardBorderWidth,
            MaterialTheme.colorScheme.outlineVariant,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MyFinHubDesignMetrics.rowHorizontalPadding,
                vertical = MyFinHubDesignMetrics.rowVerticalPadding,
            ),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MyFinHubIconBadge(
                icon = icon,
                tone = tone,
                contentDescription = iconDescription,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(MyFinHubSpacing.micro))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(MyFinHubSpacing.xs))
            MyFinHubAmountText(text = amountText, tone = tone)
        }
    }
}

@Composable
fun MyFinHubPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = MyFinHubIcons.Add,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.primaryActionHorizontalPadding,
            vertical = MyFinHubDesignMetrics.primaryActionVerticalPadding,
        ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(MyFinHubDesignMetrics.compactIconSize),
        )
        Spacer(modifier = Modifier.width(MyFinHubDesignMetrics.buttonIconGap))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}
