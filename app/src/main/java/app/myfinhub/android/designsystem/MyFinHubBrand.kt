package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.myfinhub.android.R

enum class MyFinHubBrandMode {
    Icon,
    Lockup,
}

/**
 * Canonical MyFinHub brand presentation for Android.
 *
 * The bitmap resources are byte-for-byte copies of the canonical runtime artwork from the main
 * MyFinHub repository. The responsive lockup follows the main product contract: authentic square
 * mark plus product word treatment rather than an invented Android-specific logo.
 */
@Composable
fun MyFinHubBrandMark(
    modifier: Modifier = Modifier,
    mode: MyFinHubBrandMode = MyFinHubBrandMode.Icon,
    iconSize: Dp = 36.dp,
    subtitle: String? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val resource = if (darkTheme) R.drawable.myfinhub_brand_dark else R.drawable.myfinhub_brand_light
    val mark = @Composable {
        Image(
            painter = painterResource(resource),
            contentDescription = null,
            modifier = Modifier.size(iconSize),
        )
    }

    if (mode == MyFinHubBrandMode.Icon) {
        Row(
            modifier = modifier.semantics { contentDescription = "MyFinHub" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            mark()
        }
        return
    }

    Row(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "MyFinHub" },
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        mark()
        Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MyFin",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Hub",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
