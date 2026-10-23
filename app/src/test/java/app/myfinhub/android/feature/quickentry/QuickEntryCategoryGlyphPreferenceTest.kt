package app.myfinhub.android.feature.quickentry

import app.myfinhub.android.designsystem.MyFinHubIcons
import org.junit.Assert.assertEquals
import org.junit.Test

/** Web pack choice affects only presentation and must not change the finance draft. */
class QuickEntryCategoryGlyphPreferenceTest {
    @Test
    fun splitSubcategoryPreferenceWinsAndFallsBackToParent() {
        val preferences = mapOf(
            "expense:Φαγητό" to "shopping",
            "expense:Φαγητό:Καφές" to "coffee",
        )

        assertEquals(
            MyFinHubIcons.Dining,
            quickEntryCategoryIcon("Φαγητό", "Καφές", false, preferences),
        )
        assertEquals(
            MyFinHubIcons.Shopping,
            quickEntryCategoryIcon("Φαγητό", "Εστιατόριο", false, preferences),
        )
        assertEquals(
            MyFinHubIcons.Shopping,
            quickEntryCategoryIcon("Φαγητό", null, false, preferences),
        )
    }

    @Test
    fun incomeAndExpensePreferencesAreIndependent() {
        val preferences = mapOf(
            "income:Άλλο" to "salary",
            "expense:Άλλο" to "fuel",
        )
        assertEquals(
            MyFinHubIcons.Work,
            quickEntryCategoryIcon("Άλλο", null, true, preferences),
        )
        assertEquals(
            MyFinHubIcons.Transport,
            quickEntryCategoryIcon("Άλλο", null, false, preferences),
        )
    }

    @Test
    fun unsupportedWebSvgKeyNeverLoadsRemoteAssetOrHidesDefault() {
        assertEquals(
            MyFinHubIcons.Savings,
            quickEntryCategoryIcon(
                "Αποταμίευση", "Στόχος", false,
                mapOf("expense:Αποταμίευση:Στόχος" to "unbundled-web-svg"),
            ),
        )
        assertEquals(
            MyFinHubIcons.All,
            quickEntryCategoryIcon("Άγνωστη", null, false, emptyMap()),
        )
    }
}
