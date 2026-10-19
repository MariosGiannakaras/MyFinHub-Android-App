package app.myfinhub.android.feature.insights

import androidx.lifecycle.ViewModel
import kotlin.math.abs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

const val INSIGHTS_PERIOD_MONTH = "month_to_date"
const val INSIGHTS_PERIOD_30_DAYS = "last_30_days"
const val INSIGHTS_PERIOD_90_DAYS = "last_90_days"

data class TrendPoint(
    val label: String,
    val income: Double,
    val expense: Double,
    val isPartial: Boolean = false,
    val periodDetail: String? = null,
    /** Web reporting calendar identity, including the year boundary. */
    val monthIso: String = "",
    val saving: Double = 0.0,
    /** Cumulative reportFlowSeries income minus displayed expense. */
    val cumulativeNet: Double = 0.0,
)

data class InsightCategory(
    val name: String,
    val amount: Double,
    val share: Float,
    /** Exact canonical categories represented by this row. Usually one; "Λοιπά" may contain several. */
    val sourceCategories: List<String> = listOf(name),
)

data class InsightCategoryMomentum(
    val name: String,
    val currentAmount: Double,
    val previousAmount: Double,
    /** Web returns null for a new nonzero baseline, 0.0 for two zero values. */
    val relativeChange: Double?,
)

/** Web reports.ts reportInsightModel month-specific signals, not rolling intervals. */
data class InsightMonthlyReportSignals(
    val incomeChange: Double?,
    val expenseChange: Double?,
    val savingChange: Double?,
    val netFlow: Double,
    val savingsRate: Double?,
    val previousSavingsRate: Double?,
    val trailingExpenseAverage: Double,
    val expenseVsTrailingAverage: Double?,
    val recurringMonthlyTotal: Double,
    val recurringBurden: Double?,
    val topCategoryName: String?,
    val topCategoryAmount: Double?,
    val topCategoryShare: Double?,
    val topCategoryChange: Double?,
    val sufficientExpenseHistory: Boolean,
)

data class InsightCounterparty(
    val title: String,
    val category: String,
    val amount: Double,
    val share: Double?,
    val count: Int,
    val lastDateIso: String,
)

data class InsightsLoanObligation(
    val id: String,
    val name: String,
    val nextAmount: Double,
    val outstanding: Double,
    val remainingInstallments: Int,
)
data class InsightsLoanBurden(
    val total: Double,
    val count: Int,
    val rows: List<InsightsLoanObligation>,
)

data class InsightsComparison(
    val currentLabel: String,
    val previousLabel: String,
    val currentIncome: Double,
    val currentExpense: Double,
    val previousIncome: Double,
    val previousExpense: Double,
) {
    val currentNet: Double get() = currentIncome - currentExpense
    val previousNet: Double get() = previousIncome - previousExpense
    val expenseChangePercent: Double?
        get() = previousExpense.takeIf { abs(it) > 0.005 }?.let { base ->
            ((currentExpense - base) / abs(base)) * 100.0
        }
}

data class InsightPeriodScope(
    val id: String,
    val label: String,
    val startDate: String,
    val endDate: String,
    val contextLabel: String,
    val comparison: InsightsComparison,
    val categories: List<InsightCategory>,
    /** Web Reports counterparties are monthly, not sliding-window summaries. */
    val counterparties: List<InsightCounterparty> = emptyList(),
    /** Web Reports categoryMomentum compares complete calendar months. */
    val categoryMomentum: List<InsightCategoryMomentum> = emptyList(),
    /** Web reportLoanBurden is an as-of liability, not a per-period cash flow. */
    val loanBurden: InsightsLoanBurden? = null,
    val monthlyReportSignals: InsightMonthlyReportSignals? = null,
)

data class InsightsUiState(
    val monthlyTrend: List<TrendPoint> = syntheticTrend(),
    val categories: List<InsightCategory> = syntheticCategories(),
    val averageMonthlySpend: Double = 1_040.0,
    val comparison: InsightsComparison = syntheticComparison(),
    val categoryStartDate: String = "2026-08-01",
    val categoryEndDate: String = "2026-08-31",
    val periods: List<InsightPeriodScope> = emptyList(),
) {
    val defaultPeriodId: String
        get() = periods.firstOrNull()?.id ?: INSIGHTS_PERIOD_MONTH

    fun periodScope(id: String): InsightPeriodScope = periods.firstOrNull { it.id == id }
        ?: periods.firstOrNull()
        ?: InsightPeriodScope(
            id = INSIGHTS_PERIOD_MONTH,
            label = "Μήνας",
            startDate = categoryStartDate,
            endDate = categoryEndDate,
            contextLabel = comparison.currentLabel,
            comparison = comparison,
            categories = categories,
        )
}

class InsightsViewModel : ViewModel() {
    private val mutableState = MutableStateFlow(InsightsUiState())
    val state: StateFlow<InsightsUiState> = mutableState.asStateFlow()
}

fun syntheticTrend() = listOf(
    TrendPoint("Ιουν", 1_840.0, 980.0),
    TrendPoint("Ιουλ", 1_920.0, 1_260.0),
    TrendPoint("Αυγ", 1_840.0, 910.0),
    TrendPoint("Σεπ", 920.0, 455.0, isPartial = true, periodDetail = "έως 10 Σεπ"),
)

fun syntheticCategories() = listOf(
    InsightCategory("Στέγαση", 340.0, 0.42f),
    InsightCategory("Τρόφιμα", 124.0, 0.15f),
    InsightCategory("Μετακινήσεις", 71.0, 0.09f),
    InsightCategory("Έξοδος", 59.0, 0.07f),
    InsightCategory("Λοιπά", 219.0, 0.27f, listOf("Υγεία", "Αγορές", "Άλλο")),
)

fun syntheticComparison() = InsightsComparison(
    currentLabel = "1–10 Σεπ 2026",
    previousLabel = "1–10 Αυγ 2026",
    currentIncome = 920.0,
    currentExpense = 455.0,
    previousIncome = 920.0,
    previousExpense = 510.0,
)
