package app.myfinhub.android.designsystem

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Explicit numeric visual contracts consumed by the Material theme and unit tests. */
object MyFinHubShapeSpec {
    val extraSmallRadius = 8.dp
    val smallRadius = 12.dp
    val mediumRadius = 16.dp
    val largeRadius = 24.dp
    val extraLargeRadius = 28.dp
}

object MyFinHubTypographySpec {
    val headlineLargeSize = 30.sp
    val headlineLargeLineHeight = 36.sp
    val headlineLargeWeight = FontWeight.Bold
    val headlineLargeLetterSpacing = (-0.4).sp

    val headlineMediumSize = 25.sp
    val headlineMediumLineHeight = 31.sp
    val headlineMediumWeight = FontWeight.Bold
    val headlineMediumLetterSpacing = (-0.25).sp

    val headlineSmallSize = 21.sp
    val headlineSmallLineHeight = 27.sp
    val headlineSmallWeight = FontWeight.SemiBold
    val headlineSmallLetterSpacing = (-0.15).sp

    val titleLargeSize = 19.sp
    val titleLargeLineHeight = 25.sp
    val titleLargeWeight = FontWeight.SemiBold

    val titleMediumSize = 16.sp
    val titleMediumLineHeight = 22.sp
    val titleMediumWeight = FontWeight.SemiBold

    val bodyLargeSize = 16.sp
    val bodyLargeLineHeight = 23.sp
    val bodyLargeWeight = FontWeight.Normal

    val bodyMediumSize = 14.sp
    val bodyMediumLineHeight = 20.sp
    val bodyMediumWeight = FontWeight.Normal

    val labelLargeSize = 14.sp
    val labelLargeLineHeight = 18.sp
    val labelLargeWeight = FontWeight.SemiBold
}
