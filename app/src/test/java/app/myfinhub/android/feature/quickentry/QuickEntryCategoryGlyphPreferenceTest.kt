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
    fun greekAccentedTaxonomyFallbacksRemainDeterministicAcrossClientSurfaces() {
        // Case/tonos of user-authored Greek labels must not make icon fallbacks disappear.
        assertEquals(
            MyFinHubIcons.Savings,
            quickEntryCategoryIcon("Αποταμίευση", null, false, emptyMap()),
        )
        assertEquals(
            MyFinHubIcons.Transport,
            quickEntryCategoryIcon("Καύσιμα", null, false, emptyMap()),
        )
        assertEquals(
            MyFinHubIcons.Home,
            quickEntryCategoryIcon("Ενοίκιο", null, false, emptyMap()),
        )
        assertEquals(
            MyFinHubIcons.Bills,
            quickEntryCategoryIcon("Λογαριασμοί", null, false, emptyMap()),
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
