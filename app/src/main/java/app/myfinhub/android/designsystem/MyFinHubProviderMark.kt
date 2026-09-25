package app.myfinhub.android.designsystem

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    }
    Image(
        painter = painterResource(drawable),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
