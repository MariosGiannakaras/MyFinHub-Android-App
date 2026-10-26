package app.myfinhub.android.feature.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Exact Web loans.ts loanVisualInstallmentProgress: up to 60 visual segments. */
data class CanonicalLoanInstallmentVisualProgress(
    val segments: Int,
    val paidSegments: Int,
)

fun canonicalLoanVisualInstallmentProgress(
    totalInstallments: Int,
    paidInstallments: Int,
    maxSegments: Int = 60,
): CanonicalLoanInstallmentVisualProgress {
    val total = max(0, totalInstallments)
    val maxVisible = max(1, maxSegments)
    if (total == 0) return CanonicalLoanInstallmentVisualProgress(0, 0)
    val paid = paidInstallments.coerceIn(0, total)
    val visible = min(total, maxVisible)
    val paidVisible = if (total <= maxVisible) paid else when {
        paid >= total -> visible
        else -> min(visible - 1, floor(
            paid.toDouble() / total.toDouble() * visible.toDouble(),
        ).toInt())
    }
    return CanonicalLoanInstallmentVisualProgress(visible, paidVisible)
}

/**
 * Render at most 60 tiny segments without writing anything. A textual
 * progress summary remains visible to accessibility services, even when
 * actual euro values are masked under the global privacy preference.
 */
@Composable
internal fun CanonicalLoanInstallmentSegments(
    loan: LoanItem,
    modifier: Modifier = Modifier,
) {
    val total = loan.totalInstallments
    if (total <= 0 || loan.isSelfLoan) return
    val paid = (total - loan.remainingInstallments).coerceIn(0, total)
    val progress = canonicalLoanVisualInstallmentProgress(total, paid)
    val filled = MaterialTheme.colorScheme.primary
    val unfilled = MaterialTheme.colorScheme.surfaceVariant
    Canvas(
        modifier = modifier.fillMaxWidth().height(10.dp)
            .semantics {
                contentDescription = "Πρόοδος δόσεων: $paid από $total πληρωμένες"
            }
            .testTag("loan_installment_segments_" + loan.id),
    ) {
        val gap = 2.dp.toPx()
        val width = (size.width - gap * (progress.segments - 1)) /
            progress.segments.toFloat()
        if (width <= 0f) return@Canvas
        for (index in 0 until progress.segments) {
            drawRoundRect(
                color = if (index < progress.paidSegments) filled else unfilled,
                topLeft = Offset(index * (width + gap), 0f),
                size = Size(width, size.height),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }
}
