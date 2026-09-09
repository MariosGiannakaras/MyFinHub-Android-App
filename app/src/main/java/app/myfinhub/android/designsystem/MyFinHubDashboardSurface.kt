package app.myfinhub.android.designsystem

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * High-emphasis dashboard primitives for the production finance surfaces.
 *
 * The previous product pass rendered nearly every section as the same neutral outlined card. These
 * primitives intentionally reserve the brand surface for the single most important answer on each
 * top-level screen, creating a clear visual hierarchy without changing finance semantics.
 */
@Composable
fun MyFinHubHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(MyFinHubSpacing.lg),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        tonalElevation = MyFinHubDesignMetrics.cardElevation,
        shadowElevation = MyFinHubDesignMetrics.cardElevation,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

@Composable
fun MyFinHubHeroHeading(
    eyebrow: String,
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
        )
        supporting?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.78f),
            )
        }
    }
}

@Composable
fun MyFinHubHeroValue(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
    )
}

@Composable
fun MyFinHubHeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.micro),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
        )
    }
}

@Composable
fun MyFinHubHeroAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.myFinHubPressScale(interactionSource),
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.onPrimary,
            contentColor = MaterialTheme.colorScheme.primary,
        ),
        contentPadding = PaddingValues(
            horizontal = MyFinHubDesignMetrics.primaryActionHorizontalPadding,
            vertical = MyFinHubDesignMetrics.primaryActionVerticalPadding,
        ),
    ) {
        if (content != null) content() else Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun MyFinHubHeroDivider(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f),
    ) {}
}
