package app.myfinhub.android.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.graphics.vector.ImageVector
import app.myfinhub.android.R

enum class TopLevelDestination(
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val route: AppRoute,
) {
    HOME(R.string.nav_home, Icons.Default.Home, AppRoute.Home),
    ACTIVITY(R.string.nav_activity, Icons.AutoMirrored.Filled.List, AppRoute.Activity),
    MONEY(R.string.nav_money, Icons.Default.AccountCircle, AppRoute.Money),
    PLAN(R.string.nav_plan, Icons.Default.DateRange, AppRoute.Plan),
    INSIGHTS(R.string.nav_insights, Icons.Default.Info, AppRoute.Insights),
}
