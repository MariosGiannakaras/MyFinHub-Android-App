package app.myfinhub.android.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualTokenContractTest {
    @Test
    fun shapeScale_matchesDocumentedRoles() {
        assertEquals(12.dp, MyFinHubShapeSpec.extraSmallRadius)
        assertEquals(12.dp, MyFinHubShapeSpec.smallRadius)
        assertEquals(16.dp, MyFinHubShapeSpec.mediumRadius)
        assertEquals(20.dp, MyFinHubShapeSpec.largeRadius)
        assertEquals(24.dp, MyFinHubShapeSpec.extraLargeRadius)
    }

    @Test
    fun typographyScale_matchesDocumentedHierarchy() {
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
        assertEquals(14.sp, MyFinHubTypographySpec.bodyMediumSize)
        assertEquals(20.sp, MyFinHubTypographySpec.bodyMediumLineHeight)
        assertEquals(15.sp, MyFinHubTypographySpec.labelLargeSize)
        assertEquals(20.sp, MyFinHubTypographySpec.labelLargeLineHeight)
        assertEquals(12.sp, MyFinHubTypographySpec.labelMediumSize)
        assertEquals(16.sp, MyFinHubTypographySpec.labelMediumLineHeight)
    }
}
