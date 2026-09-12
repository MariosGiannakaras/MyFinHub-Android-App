package app.myfinhub.android.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Explicit numeric visual contracts consumed by the Material theme and unit tests. */
object MyFinHubSpacingSpec {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
}

object MyFinHubShapeSpec {
    val extraSmallRadius = 12.dp
    val smallRadius = 12.dp
    val mediumRadius = 16.dp
    val largeRadius = 20.dp
    val extraLargeRadius = 24.dp
}

object MyFinHubTypographySpec {
    // S2 roles: primary money, detail money, page, section, row, body, metadata, button, navigation.
    val headlineLargeSize = 34.sp
    val headlineLargeLineHeight = 40.sp
    val headlineLargeWeight = FontWeight.SemiBold

    val headlineMediumSize = 28.sp
    val headlineMediumLineHeight = 34.sp
    val headlineMediumWeight = FontWeight.SemiBold

    val headlineSmallSize = 24.sp
    val headlineSmallLineHeight = 30.sp
    val headlineSmallWeight = FontWeight.SemiBold

    val titleLargeSize = 18.sp
    val titleLargeLineHeight = 24.sp
    val titleLargeWeight = FontWeight.SemiBold

    val titleMediumSize = 16.sp
    val titleMediumLineHeight = 22.sp
    val titleMediumWeight = FontWeight.Medium

    val bodyLargeSize = 16.sp
    val bodyLargeLineHeight = 24.sp
    val bodyLargeWeight = FontWeight.Normal

    val bodyMediumSize = 14.sp
    val bodyMediumLineHeight = 20.sp
    val bodyMediumWeight = FontWeight.Normal

    val labelLargeSize = 15.sp
    val labelLargeLineHeight = 20.sp
    val labelLargeWeight = FontWeight.SemiBold

    val labelMediumSize = 12.sp
    val labelMediumLineHeight = 16.sp
    val labelMediumWeight = FontWeight.Medium
}