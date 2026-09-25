package app.myfinhub.android.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Destructive secondary action: visible 48dp target plus semantic error emphasis. */
@Composable
fun MyFinHubDestructiveTextAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget),
        enabled = enabled,
        colors = ButtonDefaults.textButtonColors(
            contentColor = MaterialTheme.colorScheme.error,
            disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
        ),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
