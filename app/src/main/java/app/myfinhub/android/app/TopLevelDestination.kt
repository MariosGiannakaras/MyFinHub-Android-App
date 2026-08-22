package app.myfinhub.android.app

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.ui.graphics.vector.ImageVector
import app.myfinhub.android.R

enum class TopLevelDestination(
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Default.Home),
    ACTIVITY(R.string.nav_activity, Icons.Default.List),
    MONEY(R.string.nav_money, Icons.Default.AccountCircle),
    PLAN(R.string.nav_plan, Icons.Default.DateRange),
    INSIGHTS(R.string.nav_insights, Icons.Default.Info),
}
