package app.myfinhub.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class DarkContrastContractTest {
    @Test
    fun semanticTextAndEssentialOutline_meetDarkContrastThresholds() {
        val semanticPairs = listOf(
            MyFinHubPalette.darkIncome to MyFinHubPalette.darkIncomeContainer,
            MyFinHubPalette.darkExpense to MyFinHubPalette.darkExpenseContainer,
            MyFinHubPalette.darkSavings to MyFinHubPalette.darkSavingsContainer,
            MyFinHubPalette.darkTransfer to MyFinHubPalette.darkTransferContainer,
            MyFinHubPalette.darkAttention to MyFinHubPalette.darkAttentionContainer,
            MyFinHubPalette.darkNeutral to MyFinHubPalette.darkNeutralContainer,
        )
        semanticPairs.forEach { (accent, container) ->
            assertContrastAtLeast(accent, MyFinHubPalette.darkSurface, 4.5f)
            assertContrastAtLeast(accent, container, 4.5f)
        }
        assertContrastAtLeast(MyFinHubPalette.darkOutline, MyFinHubPalette.darkSurface, 3.0f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val ratio = (maxOf(foregroundLuminance, backgroundLuminance) + 0.05f) /
            (minOf(foregroundLuminance, backgroundLuminance) + 0.05f)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }
}
