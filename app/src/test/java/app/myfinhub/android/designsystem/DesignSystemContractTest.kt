package app.myfinhub.android.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DesignSystemContractTest {
    @Test
    fun foundationGeometry_matchesDocumentedContract() {
        assertEquals(48.dp, MyFinHubDesignMetrics.minimumTouchTarget)
        assertEquals(20.dp, MyFinHubDesignMetrics.screenHorizontalPadding)
        assertEquals(16.dp, MyFinHubDesignMetrics.screenTopPadding)
        assertEquals(8.dp, MyFinHubDesignMetrics.screenBottomPadding)
        assertEquals(20.dp, MyFinHubDesignMetrics.sectionGap)
        assertEquals(16.dp, MyFinHubDesignMetrics.cardContentPadding)
        assertEquals(1.dp, MyFinHubDesignMetrics.cardBorderWidth)
        assertEquals(36.dp, MyFinHubDesignMetrics.brandMarkDefaultSize)
        assertEquals(40.dp, MyFinHubDesignMetrics.authBrandMarkSize)
        assertEquals(480.dp, MyFinHubDesignMetrics.authContentMaxWidth)
        assertEquals(40.dp, MyFinHubDesignMetrics.iconBadgeSize)
        assertEquals(20.dp, MyFinHubDesignMetrics.iconBadgeIconSize)
        assertEquals(56.dp, MyFinHubDesignMetrics.secretValueLabelWidth)
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
        assertEquals(96.dp, MyFinHubDesignMetrics.navigationContentBottomClearance)
        assertEquals(152.dp, MyFinHubDesignMetrics.productSnackbarBottomClearance)
    }

    @Test
    fun shapeRadii_matchDocumentedContract() {
        assertEquals(8.dp, MyFinHubShapeSpec.extraSmallRadius)
        assertEquals(12.dp, MyFinHubShapeSpec.smallRadius)
        assertEquals(16.dp, MyFinHubShapeSpec.mediumRadius)
        assertEquals(24.dp, MyFinHubShapeSpec.largeRadius)
        assertEquals(28.dp, MyFinHubShapeSpec.extraLargeRadius)
    }

    @Test
    fun typographyScale_matchesDocumentedContract() {
        assertEquals(30.sp, MyFinHubTypographySpec.headlineLargeSize)
        assertEquals(36.sp, MyFinHubTypographySpec.headlineLargeLineHeight)
        assertEquals(FontWeight.Bold, MyFinHubTypographySpec.headlineLargeWeight)
        assertEquals((-0.4).sp, MyFinHubTypographySpec.headlineLargeLetterSpacing)

        assertEquals(25.sp, MyFinHubTypographySpec.headlineMediumSize)
        assertEquals(31.sp, MyFinHubTypographySpec.headlineMediumLineHeight)
        assertEquals(FontWeight.Bold, MyFinHubTypographySpec.headlineMediumWeight)
        assertEquals((-0.25).sp, MyFinHubTypographySpec.headlineMediumLetterSpacing)

        assertEquals(21.sp, MyFinHubTypographySpec.headlineSmallSize)
        assertEquals(27.sp, MyFinHubTypographySpec.headlineSmallLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.headlineSmallWeight)
        assertEquals((-0.15).sp, MyFinHubTypographySpec.headlineSmallLetterSpacing)

        assertEquals(19.sp, MyFinHubTypographySpec.titleLargeSize)
        assertEquals(25.sp, MyFinHubTypographySpec.titleLargeLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.titleLargeWeight)
        assertEquals(16.sp, MyFinHubTypographySpec.titleMediumSize)
        assertEquals(22.sp, MyFinHubTypographySpec.titleMediumLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.titleMediumWeight)
        assertEquals(16.sp, MyFinHubTypographySpec.bodyLargeSize)
        assertEquals(23.sp, MyFinHubTypographySpec.bodyLargeLineHeight)
        assertEquals(FontWeight.Normal, MyFinHubTypographySpec.bodyLargeWeight)
        assertEquals(14.sp, MyFinHubTypographySpec.bodyMediumSize)
        assertEquals(20.sp, MyFinHubTypographySpec.bodyMediumLineHeight)
        assertEquals(FontWeight.Normal, MyFinHubTypographySpec.bodyMediumWeight)
        assertEquals(14.sp, MyFinHubTypographySpec.labelLargeSize)
        assertEquals(18.sp, MyFinHubTypographySpec.labelLargeLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.labelLargeWeight)
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
    fun darkFinanceSemanticText_meetsNormalTextContrastOnSurfaceAndContainer() {
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
    }

    @Test
    fun essentialOutlines_meetGraphicalContrastThreshold() {
        assertContrastAtLeast(MyFinHubPalette.outline, MyFinHubPalette.surface, 3.0f)
        assertContrastAtLeast(MyFinHubPalette.darkOutline, MyFinHubPalette.darkSurface, 3.0f)
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
