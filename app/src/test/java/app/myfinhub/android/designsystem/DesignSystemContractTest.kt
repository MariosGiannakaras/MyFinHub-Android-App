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
    fun foundationGeometry_matchesDocumentedS2Contract() {
        assertEquals(48.dp, MyFinHubDesignMetrics.minimumTouchTarget)
        assertEquals(20.dp, MyFinHubDesignMetrics.screenHorizontalPadding)
        assertEquals(24.dp, MyFinHubDesignMetrics.sectionGap)
        assertEquals(12.dp, MyFinHubDesignMetrics.groupGap)
        assertEquals(4.dp, MyFinHubDesignMetrics.labelValueGap)
        assertEquals(12.dp, MyFinHubDesignMetrics.iconTextGap)
        assertEquals(64.dp, MyFinHubDesignMetrics.rowMinHeight)
        assertEquals(52.dp, MyFinHubDesignMetrics.primaryActionMinHeight)
        assertEquals(56.dp, MyFinHubDesignMetrics.textFieldMinHeight)
        assertEquals(24.dp, MyFinHubDesignMetrics.standardIconSize)
        assertEquals(24.dp, MyFinHubDesignMetrics.navigationIconSize)
        assertEquals(16.dp, MyFinHubDesignMetrics.contentRadius)
        assertEquals(12.dp, MyFinHubDesignMetrics.smallRadius)
        assertEquals(24.dp, MyFinHubDesignMetrics.sheetRadius)
        assertEquals(20.dp, MyFinHubDesignMetrics.cardArtRadius)
        assertEquals(80.dp, MyFinHubDesignMetrics.navigationBarHeight)
        assertEquals(96.dp, MyFinHubDesignMetrics.navigationContentBottomClearance)
        assertEquals(156.dp, MyFinHubDesignMetrics.productSnackbarBottomClearance)
    }

    @Test
    fun spacingAndShapeRadii_matchDocumentedS2Contract() {
        assertEquals(4.dp, MyFinHubSpacingSpec.xxs)
        assertEquals(8.dp, MyFinHubSpacingSpec.xs)
        assertEquals(12.dp, MyFinHubSpacingSpec.sm)
        assertEquals(16.dp, MyFinHubSpacingSpec.md)
        assertEquals(20.dp, MyFinHubSpacingSpec.lg)
        assertEquals(24.dp, MyFinHubSpacingSpec.xl)
        assertEquals(32.dp, MyFinHubSpacingSpec.xxl)

        assertEquals(12.dp, MyFinHubShapeSpec.extraSmallRadius)
        assertEquals(12.dp, MyFinHubShapeSpec.smallRadius)
        assertEquals(16.dp, MyFinHubShapeSpec.mediumRadius)
        assertEquals(20.dp, MyFinHubShapeSpec.largeRadius)
        assertEquals(24.dp, MyFinHubShapeSpec.extraLargeRadius)
    }

    @Test
    fun typographyScale_matchesDocumentedS2Contract() {
        assertEquals(34.sp, MyFinHubTypographySpec.headlineLargeSize)
        assertEquals(40.sp, MyFinHubTypographySpec.headlineLargeLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.headlineLargeWeight)

        assertEquals(28.sp, MyFinHubTypographySpec.headlineMediumSize)
        assertEquals(34.sp, MyFinHubTypographySpec.headlineMediumLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.headlineMediumWeight)

        assertEquals(24.sp, MyFinHubTypographySpec.headlineSmallSize)
        assertEquals(30.sp, MyFinHubTypographySpec.headlineSmallLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.headlineSmallWeight)

        assertEquals(18.sp, MyFinHubTypographySpec.titleLargeSize)
        assertEquals(24.sp, MyFinHubTypographySpec.titleLargeLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.titleLargeWeight)
        assertEquals(16.sp, MyFinHubTypographySpec.titleMediumSize)
        assertEquals(22.sp, MyFinHubTypographySpec.titleMediumLineHeight)
        assertEquals(FontWeight.Medium, MyFinHubTypographySpec.titleMediumWeight)
        assertEquals(16.sp, MyFinHubTypographySpec.bodyLargeSize)
        assertEquals(24.sp, MyFinHubTypographySpec.bodyLargeLineHeight)
        assertEquals(FontWeight.Normal, MyFinHubTypographySpec.bodyLargeWeight)
        assertEquals(14.sp, MyFinHubTypographySpec.bodyMediumSize)
        assertEquals(20.sp, MyFinHubTypographySpec.bodyMediumLineHeight)
        assertEquals(FontWeight.Normal, MyFinHubTypographySpec.bodyMediumWeight)
        assertEquals(15.sp, MyFinHubTypographySpec.labelLargeSize)
        assertEquals(20.sp, MyFinHubTypographySpec.labelLargeLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.labelLargeWeight)
        assertEquals(12.sp, MyFinHubTypographySpec.labelMediumSize)
        assertEquals(16.sp, MyFinHubTypographySpec.labelMediumLineHeight)
        assertEquals(FontWeight.Medium, MyFinHubTypographySpec.labelMediumWeight)
    }

    @Test
    fun documentedLightPalette_matchesS2Spec() {
        assertEquals(Color(0xFFF6F7FA), MyFinHubPalette.lightBackground)
        assertEquals(Color(0xFFFFFFFF), MyFinHubPalette.lightSurface)
        assertEquals(Color(0xFFEEF1F5), MyFinHubPalette.lightSecondarySurface)
        assertEquals(Color(0xFF151922), MyFinHubPalette.lightText)
        assertEquals(Color(0xFF596273), MyFinHubPalette.lightSecondaryText)
        assertEquals(Color(0xFF3659E3), MyFinHubPalette.lightAccent)
        assertEquals(Color(0xFF087443), MyFinHubPalette.lightPositive)
        assertEquals(Color(0xFFB42335), MyFinHubPalette.lightNegative)
        assertEquals(Color(0xFF895400), MyFinHubPalette.lightWarning)
        assertEquals(Color(0xFF7B8494), MyFinHubPalette.lightControlBorder)
        assertEquals(Color(0xFFE0E4EB), MyFinHubPalette.lightDivider)
    }

    @Test
    fun documentedDarkPalette_matchesS2Spec() {
        assertEquals(Color(0xFF101216), MyFinHubPalette.darkBackground)
        assertEquals(Color(0xFF191D24), MyFinHubPalette.darkSurface)
        assertEquals(Color(0xFF242A34), MyFinHubPalette.darkSecondarySurface)
        assertEquals(Color(0xFFF2F4F8), MyFinHubPalette.darkText)
        assertEquals(Color(0xFFB6BECC), MyFinHubPalette.darkSecondaryText)
        assertEquals(Color(0xFFA6B8FF), MyFinHubPalette.darkAccent)
        assertEquals(Color(0xFF73D6A1), MyFinHubPalette.darkPositive)
        assertEquals(Color(0xFFFFADB8), MyFinHubPalette.darkNegative)
        assertEquals(Color(0xFFF4C56A), MyFinHubPalette.darkWarning)
        assertEquals(Color(0xFF8792A5), MyFinHubPalette.darkControlBorder)
        assertEquals(Color(0xFF343B48), MyFinHubPalette.darkDivider)
    }

    @Test
    fun lightSemanticText_meetsNormalTextContrastOnSurfaceAndSecondarySurface() {
        listOf(
            MyFinHubPalette.lightPositive,
            MyFinHubPalette.lightNegative,
            MyFinHubPalette.lightAccent,
            MyFinHubPalette.lightSecondaryText,
            MyFinHubPalette.lightWarning,
        ).forEach { accent ->
            assertContrastAtLeast(accent, MyFinHubPalette.lightSurface, 4.5f)
            assertContrastAtLeast(accent, MyFinHubPalette.lightSecondarySurface, 4.5f)
        }
    }

    @Test
    fun darkSemanticText_meetsNormalTextContrastOnSurfaceAndSecondarySurface() {
        listOf(
            MyFinHubPalette.darkPositive,
            MyFinHubPalette.darkNegative,
            MyFinHubPalette.darkAccent,
            MyFinHubPalette.darkSecondaryText,
            MyFinHubPalette.darkWarning,
        ).forEach { accent ->
            assertContrastAtLeast(accent, MyFinHubPalette.darkSurface, 4.5f)
            assertContrastAtLeast(accent, MyFinHubPalette.darkSecondarySurface, 4.5f)
        }
    }

    @Test
    fun essentialOutlines_meetGraphicalContrastThreshold() {
        assertContrastAtLeast(MyFinHubPalette.lightControlBorder, MyFinHubPalette.lightSurface, 3.0f)
        assertContrastAtLeast(MyFinHubPalette.darkControlBorder, MyFinHubPalette.darkSurface, 3.0f)
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