package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalConfirmedLendingPlanView
import app.myfinhub.android.feature.utilities.amountVisibilityText
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

private fun confirmedLendingEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

/** Saved plans are planned dates, NOT balance-changing collections. */
@Composable
internal fun CanonicalConfirmedLendingPlanDetails(
    plan: CanonicalConfirmedLendingPlanView,
    currentOutstanding: Double?,
    amountsVisible: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("lending_confirmed_plan_details"),
        verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
    ) {
        val error = plan.validationError
        if (error != null) {
            Text(error, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("lending_confirmed_plan_invalid"))
            return@Column
        }
        Text("Επιβεβαιωμένο πρόγραμμα επιστροφής",
            style = MaterialTheme.typography.titleSmall)
        plan.total?.let { total ->
            Text("Προγραμματισμένο συνολικό ποσό: " +
                amountVisibilityText(confirmedLendingEuro(total), amountsVisible),
                modifier = Modifier.testTag("lending_confirmed_plan_total"))
        }
        Text("Πρώτη προγραμματισμένη ημερομηνία: ${plan.firstDueDate} · " +
            "${plan.installments} δόσεις · κάθε ${plan.everyMonths} μήνα/μήνες.",
            style = MaterialTheme.typography.bodySmall)
        currentOutstanding?.let { amount ->
            Text("Πραγματική απαίτηση σήμερα: " +
                amountVisibilityText(confirmedLendingEuro(amount), amountsVisible),
                modifier = Modifier.testTag("lending_confirmed_plan_actual"))
        }
        Text("Η επιβεβαίωση προγράμματος δεν καταχωρίζει είσπραξη, " +
            "ούτε μειώνει το πραγματικό υπόλοιπο. Οι καταγεγραμμένες επιστροφές " +
            "εμφανίζονται ξεχωριστά στο ιστορικό.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
