package app.myfinhub.android.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationPolicyTest {
    @Test
    fun topLevelContract_hasExactlyFourPermanentDestinations() {
        assertEquals(
            listOf(
                TopLevelDestination.HOME,
                TopLevelDestination.ACTIVITY,
                TopLevelDestination.MONEY,
                TopLevelDestination.PLAN,
            ),
            TopLevelDestination.entries,
        )
    }

    @Test
    fun insights_isActivitySibling_notFifthRoot() {
        assertEquals(TopLevelDestination.ACTIVITY, AppRoute.Insights.topLevelDestination)
        assertEquals(TopLevelDestination.ACTIVITY, AppRoute.Activity.topLevelDestination)
        assertTrue(AppRoute.Insights.showsGlobalNavigation)
    }

    @Test
    fun onlyRootAndActivitySiblingSurfaces_showGlobalNavigation() {
        listOf(
            AppRoute.Home,
            AppRoute.Activity,
            AppRoute.Insights,
            AppRoute.Money,
            AppRoute.Plan,
        ).forEach { route -> assertTrue("$route should show global navigation", route.showsGlobalNavigation) }

        listOf(
            AppRoute.Settings,
            AppRoute.QuickEntry,
            AppRoute.CardCreate,
            AppRoute.CardDetail("card-1"),
            AppRoute.ActivityDetail("event-1"),
            AppRoute.CategoryActivity("food", "2026-09-01", "2026-09-12"),
            AppRoute.PlanBudgets,
        ).forEach { route ->
            assertFalse("$route must hide global navigation", route.showsGlobalNavigation)
            assertNull(route.topLevelDestination)
        }
    }
}