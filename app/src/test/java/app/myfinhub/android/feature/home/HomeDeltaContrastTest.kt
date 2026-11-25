package app.myfinhub.android.feature.home

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.myfinhub.android.designsystem.MyFinHubPalette
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeDeltaContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
    }

    @Test fun roleDeltaTextMeetsSmallTextContrastInBothThemes() {
        for (dark in listOf(false, true)) {
            val surface = if (dark) MyFinHubPalette.darkSurface else MyFinHubPalette.lightSurface
            for (role in listOf(HomePrimaryRole.CASH, HomePrimaryRole.PAYROLL, HomePrimaryRole.SAVINGS, null)) {
                val ratio = contrast(homeAccountDeltaTextColor(role, dark), surface)
                assertTrue("$role dark=$dark contrast=$ratio", ratio >= 4.5f)
            }
        }
    }
}
