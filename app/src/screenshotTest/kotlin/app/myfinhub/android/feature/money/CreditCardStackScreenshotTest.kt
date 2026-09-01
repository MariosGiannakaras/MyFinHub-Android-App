package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubTheme
import com.android.tools.screenshot.PreviewTest

private const val COMPACT_SCREENSHOT_DEVICE = "spec:width=412dp,height=390dp,dpi=160"

private val referenceScreenshotCards = listOf(
    MoneyCard(
        id = "reference-piraeus",
        nickname = "Visa Classic",
        last4 = "1234",
        kind = "Πιστωτική",
        currentBalance = 312.20,
        limit = 2_000.0,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "piraeus",
    ),
    MoneyCard(
        id = "reference-revolut",
        nickname = "Premium Midnight",
        last4 = "5678",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "revolut",
    ),
    MoneyCard(
        id = "reference-alpha",
        nickname = "Bonus Visa Gold",
        last4 = "9012",
        kind = "Πιστωτική",
        currentBalance = 0.0,
        limit = 3_000.0,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "alpha",
    ),
    MoneyCard(
        id = "reference-payzy",
        nickname = "payzy pro Aqua",
        last4 = "3456",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "VISA",
        bankId = "payzy",
    ),
    MoneyCard(
        id = "reference-viva",
        nickname = "Cobalt Blue",
        last4 = "8100",
        kind = "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.AVAILABLE,
        network = "MASTERCARD",
        bankId = "viva",
    ),
)

@PreviewTest
@Preview(
    name = "card_stack_compact_light",
    device = COMPACT_SCREENSHOT_DEVICE,
    showBackground = true,
)
@Composable
fun CreditCardStackCompactLightScreenshot() {
    ReferenceCardStackScreenshotSurface()
}

@PreviewTest
@Preview(
    name = "card_stack_compact_150_percent_font",
    device = COMPACT_SCREENSHOT_DEVICE,
    fontScale = 1.5f,
    showBackground = true,
)
@Composable
fun CreditCardStackCompactLargeFontScreenshot() {
    ReferenceCardStackScreenshotSurface()
}

@Composable
private fun ReferenceCardStackScreenshotSurface() {
    MyFinHubTheme(darkTheme = false) {
        Box(
            modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 28.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            CreditCardStack(
                cards = referenceScreenshotCards,
                secretState = CardSecretUiState.Hidden("reference-piraeus"),
                onActiveCardChanged = {},
                onRevealSecrets = {},
                onHideSecrets = {},
                onOpenCard = {},
                onDeleteCard = {},
            )
        }
    }
}
