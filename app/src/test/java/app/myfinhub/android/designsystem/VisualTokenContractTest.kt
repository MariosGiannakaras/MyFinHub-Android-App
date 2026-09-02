package app.myfinhub.android.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualTokenContractTest {
    @Test
    fun shapeScale_matchesDocumentedRoles() {
        assertEquals(8.dp, MyFinHubShapeSpec.extraSmallRadius)
        assertEquals(12.dp, MyFinHubShapeSpec.smallRadius)
        assertEquals(16.dp, MyFinHubShapeSpec.mediumRadius)
        assertEquals(24.dp, MyFinHubShapeSpec.largeRadius)
        assertEquals(28.dp, MyFinHubShapeSpec.extraLargeRadius)
    }

    @Test
    fun typographyScale_matchesDocumentedHierarchy() {
        assertEquals(30.sp, MyFinHubTypographySpec.headlineLargeSize)
        assertEquals(36.sp, MyFinHubTypographySpec.headlineLargeLineHeight)
        assertEquals(FontWeight.Bold, MyFinHubTypographySpec.headlineLargeWeight)
        assertEquals(25.sp, MyFinHubTypographySpec.headlineMediumSize)
        assertEquals(31.sp, MyFinHubTypographySpec.headlineMediumLineHeight)
        assertEquals(21.sp, MyFinHubTypographySpec.headlineSmallSize)
        assertEquals(27.sp, MyFinHubTypographySpec.headlineSmallLineHeight)
        assertEquals(FontWeight.SemiBold, MyFinHubTypographySpec.headlineSmallWeight)
        assertEquals(19.sp, MyFinHubTypographySpec.titleLargeSize)
        assertEquals(25.sp, MyFinHubTypographySpec.titleLargeLineHeight)
        assertEquals(16.sp, MyFinHubTypographySpec.titleMediumSize)
        assertEquals(22.sp, MyFinHubTypographySpec.titleMediumLineHeight)
        assertEquals(16.sp, MyFinHubTypographySpec.bodyLargeSize)
        assertEquals(23.sp, MyFinHubTypographySpec.bodyLargeLineHeight)
        assertEquals(14.sp, MyFinHubTypographySpec.bodyMediumSize)
        assertEquals(20.sp, MyFinHubTypographySpec.bodyMediumLineHeight)
        assertEquals(14.sp, MyFinHubTypographySpec.labelLargeSize)
        assertEquals(18.sp, MyFinHubTypographySpec.labelLargeLineHeight)
    }
}
