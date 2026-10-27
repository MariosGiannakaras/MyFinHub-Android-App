package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.myfinhub.android.core.data.CanonicalConfirmedLendingPlanView
import app.myfinhub.android.core.data.canonicalConfirmedSchedule
import app.myfinhub.android.designsystem.MyFinHubSpacing
import app.myfinhub.android.feature.utilities.amountVisibilityText
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

private fun confirmedLendingEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

/**
 * Source-confirmed schedules are NOT incoming financial movements.
 * Render at most six planned dates initially; expanding is explicit.
 */
@Composable
internal fun CanonicalConfirmedLendingPlanDetails(
    plan: CanonicalConfirmedLendingPlanView,
    currentOutstanding: Double?,
    amountsVisible: Boolean,
    asOf: String? = null,
) {
    var visibleCount by rememberSaveable(plan.id) { mutableIntStateOf(6) }
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

        val timeline = plan.canonicalConfirmedSchedule(asOf)
        if (timeline == null) {
            Text(
                "Το πρόγραμμα δεν μπορεί να παρουσιαστεί με ασφάλεια. " +
                    "Απαιτείται έλεγχος των αποθηκευμένων στοιχείων.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("lending_confirmed_schedule_invalid"),
            )
        } else {
            Text("Αναλυτικές προγραμματισμένες δόσεις",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.testTag("lending_confirmed_schedule_heading"))
            val today = timeline.asOfDate?.let(LocalDate::parse)
            timeline.installments.take(visibleCount).forEachIndexed { index, row ->
                val past = today != null && LocalDate.parse(row.dueDate).isBefore(today)
                Text(
                    "${index + 1}. ${row.dueDate} · " +
                        amountVisibilityText(confirmedLendingEuro(row.amount), amountsVisible) +
                        if (past) " · Η ημερομηνία παρήλθε" else "",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lending_confirmed_schedule_row_${index + 1}"),
                )
            }
            if (timeline.pastDueDates > 0) {
                Text(
                    "${timeline.pastDueDates} προγραμματισμένες ημερομηνίες έχουν παρέλθει. " +
                        "Αυτό δεν σημαίνει ότι εισπράχθηκαν οι αντίστοιχες δόσεις.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lending_confirmed_schedule_past_warning"),
                )
            }
            if (timeline.asOfDate == null) {
                Text(
                    "Δεν υπάρχει ημερομηνία ελέγχου· δεν αξιολογείται αν μια δόση έχει παρέλθει.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag("lending_confirmed_schedule_no_asof"),
                )
            }
            if (timeline.installments.size > visibleCount) {
                TextButton(
                    onClick = {
                        visibleCount = (visibleCount + 6).coerceAtMost(timeline.installments.size)
                    },
                    modifier = Modifier.fillMaxWidth()
                        .testTag("lending_confirmed_schedule_more"),
                ) {
                    Text("Περισσότερες προγραμματισμένες δόσεις " +
                        "(${timeline.installments.size - visibleCount})")
                }
            }
        }
        Text(
            "Η επιβεβαίωση προγράμματος δεν καταχωρίζει είσπραξη. " +
                "Οι προγραμματισμένες δόσεις δεν είναι εισπράξεις. " +
                "Μόνο καταγεγραμμένες επιστροφές μειώνουν την πραγματική απαίτηση.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
