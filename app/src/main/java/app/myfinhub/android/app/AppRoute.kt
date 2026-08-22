package app.myfinhub.android.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Activity : AppRoute

    @Serializable
    data object Money : AppRoute

    @Serializable
    data object Plan : AppRoute

    @Serializable
    data object Insights : AppRoute
}
