package app.myfinhub.android.feature.insights

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TrendPoint(
    val label: String,
    val income: Double,
    val expense: Double,
)

data class InsightCategory(
    val name: String,
    val amount: Double,
    val share: Float,
)

data class InsightsUiState(
    val monthlyTrend: List<TrendPoint> = syntheticTrend(),
    val categories: List<InsightCategory> = syntheticCategories(),
    val averageMonthlySpend: Double = 1_040.0,
    val savingsRate: Int = 24,
)

class InsightsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = mutableState.asStateFlow()
}

fun syntheticTrend() = listOf(
    TrendPoint("Μάι", 1_840.0, 1_120.0),
    TrendPoint("Ιουν", 1_840.0, 980.0),
    TrendPoint("Ιουλ", 1_920.0, 1_260.0),
    TrendPoint("Αυγ", 1_840.0, 910.0),
)

fun syntheticCategories() = listOf(
    InsightCategory("Στέγαση", 680.0, 0.42f),
    InsightCategory("Τρόφιμα", 248.0, 0.15f),
    InsightCategory("Μετακινήσεις", 142.0, 0.09f),
    InsightCategory("Έξοδος", 118.0, 0.07f),
)
