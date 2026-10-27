package app.myfinhub.android.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.myfinhub.android.R
import app.myfinhub.android.designsystem.MyFinHubIcons

/** Five owner-approved native mobile roots. */
enum class TopLevelDestination(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val route: AppRoute,
) {
    HOME(R.string.nav_home, MyFinHubIcons.Home, AppRoute.Home),
    ACTIVITY(R.string.nav_activity, MyFinHubIcons.Activity, AppRoute.Activity),
    MONEY(R.string.nav_cards, MyFinHubIcons.Card, AppRoute.Money),
    PLAN(R.string.nav_plan, MyFinHubIcons.Plan, AppRoute.Plan),
    INSIGHTS(R.string.nav_reports, MyFinHubIcons.Insights, AppRoute.Insights),
}