package app.myfinhub.android.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics

/**
 * Shared retained-product form geometry. These wrappers keep field height, shape, error semantics
 * and touch targets consistent while still allowing the underlying Material 3 control to own its
 * interaction/state behavior.
 */
@Composable
fun MyFinHubOutlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    prefix: (@Composable () -> Unit)? = null,
    suffix: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null,
) {
    var fieldModifier = modifier
        .fillMaxWidth()
        .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight)
    if (focusRequester != null) fieldModifier = fieldModifier.focusRequester(focusRequester)
    if (errorMessage != null) {
        fieldModifier = fieldModifier.semantics { error(errorMessage) }
    }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = fieldModifier,
        label = { Text(label) },
        supportingText = when {
            errorMessage != null -> ({ Text(errorMessage) })
            supportingText != null -> ({ Text(supportingText) })
            else -> null
        },
        isError = errorMessage != null,
        keyboardOptions = keyboardOptions,
        singleLine = singleLine,
        prefix = prefix,
        suffix = suffix,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = MaterialTheme.shapes.extraSmall,
        textStyle = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
fun MyFinHubSelectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val semanticsModifier = if (errorMessage == null) modifier else {
        modifier.semantics { error(errorMessage) }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(MyFinHubDesignMetrics.fieldLabelGap),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        OutlinedButton(
            onClick = onClick,
            modifier = semanticsModifier
                .fillMaxWidth()
                .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.extraSmall,
            content = content,
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
fun MyFinHubFieldIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(MyFinHubDesignMetrics.minimumTouchTarget),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(MyFinHubDesignMetrics.standardIconSize),
        )
    }
}
