package app.myfinhub.android.designsystem

import androidx.compose.ui.unit.dp

/**
 * Numeric geometry contract for the S2 MyFinHub Android foundation.
 *
 * Material owns platform behavior; these values own product spacing, minimum geometry and the
 * documented 2026 finance hierarchy. Heights are minimums so Greek labels may expand at 150% font.
 */
object MyFinHubDesignMetrics {
    val minimumTouchTarget = 48.dp

    val screenHorizontalPadding = 20.dp
    val screenTopPadding = 16.dp
    val screenBottomPadding = 8.dp
    val sectionGap = 24.dp
    val groupGap = 12.dp
    val labelValueGap = 4.dp
    val iconTextGap = 12.dp

    val cardContentPadding = 16.dp
    val cardBorderWidth = 1.dp
    val cardElevation = 0.dp
    val rowHorizontalPadding = 16.dp
    val rowVerticalPadding = 12.dp
    val rowMinHeight = 64.dp

    val brandMarkDefaultSize = 36.dp
    val authBrandMarkSize = 40.dp
    val authContentMaxWidth = 480.dp
    val iconBadgeSize = 40.dp
    val iconBadgeIconSize = 24.dp
    val standardIconSize = 24.dp
    val compactIconSize = 18.dp
    val navigationIconSize = 24.dp
    val secretValueLabelWidth = 56.dp

    val primaryActionMinHeight = 52.dp
    val primaryActionHorizontalPadding = 20.dp
    val primaryActionVerticalPadding = 12.dp
    val buttonIconGap = 8.dp

    val textFieldMinHeight = 56.dp
    val textFieldUnfocusedBorder = 1.dp
    val textFieldFocusedBorder = 2.dp
    val fieldLabelGap = 4.dp

    val contentRadius = 16.dp
    val smallRadius = 12.dp
    val sheetRadius = 24.dp
    val cardArtRadius = 20.dp

    // Navigation remains platform-owned; labels stay visible at large fonts.
    val navigationBarHeight = 80.dp
    val navigationActiveIndicatorWidth = 64.dp
    val navigationActiveIndicatorHeight = 32.dp
    val navigationIndicatorLabelGap = 4.dp

    val navigationContentBottomClearance = navigationBarHeight + 16.dp
    val productSnackbarBottomClearance = navigationBarHeight + primaryActionMinHeight + 24.dp
}