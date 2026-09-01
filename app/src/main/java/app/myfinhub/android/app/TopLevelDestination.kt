package app.myfinhub.android.app

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import app.myfinhub.android.R
import app.myfinhub.android.designsystem.MyFinHubIcons

enum class TopLevelDestination(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val route: AppRoute,
) {
    HOME(R.string.nav_home, MyFinHubIcons.Home, AppRoute.Home),
    ACTIVITY(R.string.nav_activity, MyFinHubIcons.Activity, AppRoute.Activity),
    MONEY(R.string.nav_money, MyFinHubIcons.Money, AppRoute.Money),
    PLAN(R.string.nav_plan, MyFinHubIcons.Plan, AppRoute.Plan),
    INSIGHTS(R.string.nav_insights, MyFinHubIcons.Insights, AppRoute.Insights),
}
