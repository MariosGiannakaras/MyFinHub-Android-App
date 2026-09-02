package app.myfinhub.android.designsystem

import androidx.compose.ui.unit.dp

/**
 * Numeric geometry contract for retained MyFinHub Android surfaces.
 *
 * These values intentionally own only product-level decisions. Component behavior that is already
 * correct in Material 3 should inherit Material defaults instead of duplicating framework tokens.
 */
object MyFinHubDesignMetrics {
    val minimumTouchTarget = 48.dp

    val screenHorizontalPadding = 20.dp
    val screenTopPadding = 16.dp
    val screenBottomPadding = 8.dp
    val sectionGap = 20.dp

    val cardContentPadding = 16.dp
    val cardBorderWidth = 1.dp
    val cardElevation = 0.dp
    val rowHorizontalPadding = 16.dp
    val rowVerticalPadding = 12.dp

    val brandMarkDefaultSize = 36.dp
    val iconBadgeSize = 40.dp
    val iconBadgeIconSize = 20.dp
    val standardIconSize = 20.dp
    val compactIconSize = 18.dp
    val navigationIconSize = 24.dp

    // Material 3 baseline buttons are 40dp high; MyFinHub keeps primary finance actions visibly
    // 48dp high so the visual affordance and the minimum touch target are the same size.
    val primaryActionMinHeight = 48.dp
    val primaryActionHorizontalPadding = 20.dp
    val primaryActionVerticalPadding = 12.dp
    val buttonIconGap = 8.dp

    // Material 3 text-field baseline: 56dp min height, 1dp unfocused and 2dp focused border.
    val textFieldMinHeight = 56.dp
    val textFieldUnfocusedBorder = 1.dp
    val textFieldFocusedBorder = 2.dp

    // Material 3 navigation baseline. NavigationSuiteScaffold owns the adaptive container itself.
    val navigationBarHeight = 80.dp
    val navigationActiveIndicatorWidth = 64.dp
    val navigationActiveIndicatorHeight = 32.dp
    val navigationIndicatorLabelGap = 4.dp
}
