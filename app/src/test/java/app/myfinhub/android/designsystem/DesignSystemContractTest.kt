package app.myfinhub.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemContractTest {
    @Test
    fun foundationGeometry_matchesDocumentedContract() {
        assertEquals(48.dp, MyFinHubDesignMetrics.minimumTouchTarget)
        assertEquals(20.dp, MyFinHubDesignMetrics.screenHorizontalPadding)
        assertEquals(16.dp, MyFinHubDesignMetrics.cardContentPadding)
        assertEquals(1.dp, MyFinHubDesignMetrics.cardBorderWidth)
        assertEquals(36.dp, MyFinHubDesignMetrics.brandMarkDefaultSize)
        assertEquals(40.dp, MyFinHubDesignMetrics.iconBadgeSize)
        assertEquals(20.dp, MyFinHubDesignMetrics.iconBadgeIconSize)
        assertEquals(48.dp, MyFinHubDesignMetrics.primaryActionMinHeight)
        assertEquals(56.dp, MyFinHubDesignMetrics.textFieldMinHeight)
        assertEquals(1.dp, MyFinHubDesignMetrics.textFieldUnfocusedBorder)
        assertEquals(2.dp, MyFinHubDesignMetrics.textFieldFocusedBorder)
        assertEquals(4.dp, MyFinHubDesignMetrics.fieldLabelGap)
        assertEquals(80.dp, MyFinHubDesignMetrics.navigationBarHeight)
        assertEquals(24.dp, MyFinHubDesignMetrics.navigationIconSize)
        assertEquals(64.dp, MyFinHubDesignMetrics.navigationActiveIndicatorWidth)
        assertEquals(32.dp, MyFinHubDesignMetrics.navigationActiveIndicatorHeight)
        assertEquals(4.dp, MyFinHubDesignMetrics.navigationIndicatorLabelGap)
    }

    @Test
    fun lightFinanceSemanticText_meetsNormalTextContrastOnSurfaceAndContainer() {
        val semanticPairs = listOf(
            MyFinHubPalette.lightIncome to MyFinHubPalette.lightIncomeContainer,
            MyFinHubPalette.lightExpense to MyFinHubPalette.lightExpenseContainer,
            MyFinHubPalette.lightSavings to MyFinHubPalette.lightSavingsContainer,
            MyFinHubPalette.lightTransfer to MyFinHubPalette.lightTransferContainer,
            MyFinHubPalette.lightAttention to MyFinHubPalette.lightAttentionContainer,
            MyFinHubPalette.lightNeutral to MyFinHubPalette.lightNeutralContainer,
        )

        semanticPairs.forEach { (accent, container) ->
            assertContrastAtLeast(accent, MyFinHubPalette.surface, 4.5f)
            assertContrastAtLeast(accent, container, 4.5f)
        }
    }

    @Test
    fun essentialLightOutline_meetsGraphicalContrastThreshold() {
        assertContrastAtLeast(MyFinHubPalette.outline, MyFinHubPalette.surface, 3.0f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = maxOf(foregroundLuminance, backgroundLuminance)
        val darker = minOf(foregroundLuminance, backgroundLuminance)
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }
}
