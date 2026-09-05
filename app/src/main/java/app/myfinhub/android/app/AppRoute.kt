package app.myfinhub.android.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Home : AppRoute
    @Serializable data class HomeAttention(val attentionId: String) : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data object ChangeHistory : AppRoute
    @Serializable data object NoticeHistory : AppRoute
    @Serializable data object Activity : AppRoute
    @Serializable data class ActivityDetail(val eventId: String) : AppRoute
    @Serializable data object QuickEntry : AppRoute
    @Serializable data object Money : AppRoute
    @Serializable data class AccountDetail(val accountId: String) : AppRoute
    @Serializable data object CardCreate : AppRoute
    @Serializable data class CardDetail(val cardId: String) : AppRoute
    @Serializable data object Savings : AppRoute
    @Serializable data object Loans : AppRoute
    @Serializable data class LoanDetail(val loanId: String) : AppRoute
    @Serializable data object Lending : AppRoute
    @Serializable data class LendingDetail(val lendingId: String) : AppRoute
    @Serializable data object Plan : AppRoute
    @Serializable data class PlanItem(val itemId: String) : AppRoute
    @Serializable data object PlanBudgets : AppRoute
    @Serializable data object Insights : AppRoute
}
