package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.myfinhub.android.R
import app.myfinhub.android.core.ui.FinancialProvider

@Composable
fun MyFinHubProviderMark(
    provider: FinancialProvider,
    modifier: Modifier = Modifier,
    contentDescription: String? = provider.institutionLabel,
) {
    val drawable = when (provider) {
        FinancialProvider.PIRAEUS -> R.drawable.mfh_bank_piraeus_mark
        FinancialProvider.REVOLUT -> R.drawable.mfh_bank_revolut_mark
        FinancialProvider.ALPHA -> R.drawable.mfh_bank_alpha_mark
        FinancialProvider.PAYZY -> R.drawable.mfh_payzy_reference_logo
        FinancialProvider.VIVA -> R.drawable.mfh_viva_reference_logo
        FinancialProvider.NATIONAL, FinancialProvider.EUROBANK, FinancialProvider.PAYPAL -> null
    }
    if (drawable != null) {
        Image(
            painter = painterResource(drawable),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
    } else {
        // Neutral mark until an actual verified Web asset binding is available.
        // Never impersonate a provider with an unrelated bank's logo.
        Box(
            modifier = modifier
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                .then(if (contentDescription != null) Modifier.semantics {
                    this.contentDescription = contentDescription
                } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = provider.cardLabel.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
