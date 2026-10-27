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
        AppRoute.Insights -> true
        else -> false
    }

/** Reports are now a fifth root; accounts are reached from Home. */
internal val AppRoute.topLevelDestination: TopLevelDestination?
    get() = when (this) {
        AppRoute.Home -> TopLevelDestination.HOME
        AppRoute.Activity -> TopLevelDestination.ACTIVITY
        AppRoute.Money -> TopLevelDestination.MONEY
        AppRoute.Plan -> TopLevelDestination.PLAN
        AppRoute.Insights -> TopLevelDestination.INSIGHTS
        else -> null
    }