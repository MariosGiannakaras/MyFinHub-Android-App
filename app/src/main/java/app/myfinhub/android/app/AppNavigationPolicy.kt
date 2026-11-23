package app.myfinhub.android.app

/** Legacy activity sibling identity, retained for existing navigation state. */
internal enum class ActivitySection {
    HISTORY,
    ANALYSIS,
}

/**
 * Mobile companion navigation. Only the three daily-use roots show global navigation; secondary,
 * detail and editor routes keep the user in the originating stack and rely on explicit/system Back.
 */
internal val AppRoute.showsGlobalNavigation: Boolean
    get() = when (this) {
        AppRoute.Home,
        AppRoute.Activity,
        AppRoute.Money -> true
        else -> false
    }

/** Legacy reporting and planning routes are still recognized but not shown as primary tabs. */
internal val AppRoute.topLevelDestination: TopLevelDestination?
    get() = when (this) {
        AppRoute.Home -> TopLevelDestination.HOME
        AppRoute.Activity -> TopLevelDestination.ACTIVITY
        AppRoute.Money -> TopLevelDestination.MONEY
        AppRoute.Plan -> TopLevelDestination.PLAN
        AppRoute.Insights -> TopLevelDestination.INSIGHTS
        else -> null
    }

/**
 * Full card secrets belong only to the visible wallet card face or secure-card detail route.
 * Other destinations (including card profile, editors and different primary tabs) must revoke
 * already-loaded PAN/CVV and invalidate any in-flight response on route transition.
 * The secure-details route owns its separate on-open/on-close read lifecycle.
 */
internal fun shouldHideCardSecretsForRoute(
    destination: TopLevelDestination,
    activeRoute: AppRoute?,
): Boolean =
    destination != TopLevelDestination.MONEY ||
        (activeRoute != AppRoute.Money && activeRoute !is AppRoute.CardSecureDetails)
