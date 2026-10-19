package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.network.FinancialProviderRecord
import app.myfinhub.android.core.network.TrustedProviderAssetReference
import app.myfinhub.android.core.network.loadCertifiedProviderBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Session/UI scoped owner catalog. Never store public asset URLs on disk. */
val LocalFinancialProviderCatalog = staticCompositionLocalOf<List<FinancialProviderRecord>> { emptyList() }

/** Renders only origin-certified PNG/JPEG/WebP artwork. All other outcomes remain
 * neutral instead of risking false bank identity or blocking the finance screen.
 */
@Composable
fun MyFinHubCertifiedProviderMark(
    providerId: String,
    asset: TrustedProviderAssetReference,
    fallbackLabel: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = fallbackLabel,
) {
    val bitmap=produceState<android.graphics.Bitmap?>(
        initialValue=null,
        key1=providerId,
        key2=asset,
    ) {
        value=withContext(Dispatchers.IO) {
            loadCertifiedProviderBitmap(providerId,asset)
        }
    }.value
    if (bitmap != null) {
        Image(
            bitmap=bitmap.asImageBitmap(),
            contentDescription=contentDescription,
            modifier=modifier,
            contentScale=ContentScale.Fit,
        )
    } else {
        Box(
            modifier=modifier
                .background(MaterialTheme.colorScheme.surfaceVariant,RoundedCornerShape(6.dp))
                .then(if (contentDescription != null) Modifier.semantics {
                    this.contentDescription=contentDescription
                } else Modifier),
            contentAlignment=Alignment.Center,
        ) {
            Text(
                text=fallbackLabel.trim().take(2).uppercase(),
                style=MaterialTheme.typography.labelSmall,
                fontWeight=FontWeight.Bold,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
