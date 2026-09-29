package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import app.myfinhub.android.R

enum class MyFinHubBrandMode {
    Icon,
    Lockup,
}

/**
 * Canonical Android presentation of the owner-approved MyFinHub Brand Kit v2.
 *
 * The runtime resources are APK-optimized derivatives of the supplied PNG artwork: the wallet/MF
 * symbol remains the canonical compact mark, while the supplied horizontal light/dark lockups are
 * used directly instead of reconstructing the wordmark in Compose. Theme selection follows the
 * active Material background so explicit previews use the matching asset.
 */
@Composable
fun MyFinHubBrandMark(
    modifier: Modifier = Modifier,
    mode: MyFinHubBrandMode = MyFinHubBrandMode.Icon,
    iconSize: Dp = MyFinHubDesignMetrics.brandMarkDefaultSize,
    subtitle: String? = null,
) {
    val semanticsModifier =
        modifier.semantics(mergeDescendants = true) { contentDescription = "MyFinHub" }

    if (mode == MyFinHubBrandMode.Icon) {
        Image(
            painter = painterResource(R.drawable.myfinhub_symbol),
            contentDescription = null,
            modifier = semanticsModifier.size(iconSize),
            contentScale = ContentScale.Fit,
        )
        return
    }

    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val lockupResource =
        if (darkTheme) R.drawable.myfinhub_lockup_dark else R.drawable.myfinhub_lockup_light

    Column(
        modifier = semanticsModifier,
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        Image(
            painter = painterResource(lockupResource),
            contentDescription = null,
            modifier = Modifier.height(iconSize),
            contentScale = ContentScale.Fit,
        )
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
