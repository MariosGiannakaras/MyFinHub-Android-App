package app.myfinhub.android.app

/** The two sibling surfaces owned by the Κινήσεις top-level destination. */
internal enum class ActivitySection {
    HISTORY,
    ANALYSIS,
}

/**
 * S2 navigation contract. Only these root/sibling surfaces show global navigation; secondary,
 * detail and editor routes keep the user in the originating stack and rely on explicit/system Back.
 */
internal val AppRoute.showsGlobalNavigation: Boolean
    get() = when (this) {
        AppRoute.Home,
        AppRoute.Activity,
        AppRoute.Insights,
        AppRoute.Money,
        AppRoute.Plan,
        -> true
        else -> false
    }

/** Legacy Insights is deliberately mapped to the Κινήσεις root instead of being a fifth tab. */
internal val AppRoute.topLevelDestination: TopLevelDestination?
    get() = when (this) {
        AppRoute.Home -> TopLevelDestination.HOME
        AppRoute.Activity,
        AppRoute.Insights,
        -> TopLevelDestination.ACTIVITY
        AppRoute.Money -> TopLevelDestination.MONEY
        AppRoute.Plan -> TopLevelDestination.PLAN
        else -> null
    }