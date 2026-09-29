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
 * Canonical Android presentation of the owner-approved MyFinHub PureVector identity.
 *
 * Android consumes APK-appropriate raster derivatives of the same vector masters used by the
 * web/desktop client. The compact mark uses the standalone wallet/MF symbol. The supplied
 * horizontal light/dark lockups are byte-identical, so Android keeps one canonical lockup instead
 * of duplicating identical resources by theme.
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

    Column(
        modifier = semanticsModifier,
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
    ) {
        Image(
            painter = painterResource(R.drawable.myfinhub_lockup),
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
