package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

/** Web loans.ts row-level debt; all values are projected, never persisted. */
data class CanonicalLoanBalanceRow(
    val id: String, val name: String, val provider: String, val total: Double,
    val installment: Double, val remaining: Double, val remainingInstallments: Int,
    val typicalPaymentDay: Int?,
    // Per-row Web effectiveLoans metadata: duplicate historical IDs must not
    // inherit the first matching seed record's accounting mode or bank route.
    val isSelfLoan: Boolean = false,
    val isLiabilityRepayment: Boolean = false,
    val defaultAccountId: String = "",
)

internal fun JsonObject.isOwnLoan(): Boolean =
    string("kind") == "self-loan" || string("source") == "self-loan" ||
        Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ", RegexOption.IGNORE_CASE)
            .containsMatchIn(string("name").orEmpty() + " " + string("provider").orEmpty())

fun CanonicalFinanceDocument.canonicalLoanBalanceRows(asOf: String? = null): List<CanonicalLoanBalanceRow> {
    // Web effectiveLoans / activeLongTermLoanObligations map each seeded row
    // through its override and then append all custom rows. An associateBy(id)
    // silently loses duplicate historic rows and can invent orphan overrides.
    val overrides = state.obj("loanOverrides")
    val seeded = seed.array("loans").mapNotNull { it as? JsonObject }
        .map { loan -> (overrides[loan.string("id").orEmpty()] as? JsonObject) ?: loan }
    val customs = state.array("customLoans").mapNotNull { it as? JsonObject }
    val loans = seeded + customs
    val extras = state.obj("loanExtra")
    val allEvents = state.array("events").mapNotNull { it as? JsonObject }
    return loans.map { loan ->
        val id = loan.string("id").orEmpty()
        val self = loan.isOwnLoan()
        val events = allEvents.filter { e ->
            e.string("loanId") == id && (asOf == null || e.string("date")?.let { it <= asOf } == true) &&
                (!self || (e.string("kind") == "transfer" &&
                    Regex("^(?:ΕΠΙΣΤΡΟΦΗ|RETURN)(?:\\s|:|$)", RegexOption.IGNORE_CASE)
                        .containsMatchIn(e.string("note").orEmpty().trim())))
        }
        val installments = (loan.number("installments") ?: 0.0).toInt().coerceAtLeast(0)
        val installment = (loan.number("installment") ?: 0.0).coerceAtLeast(0.0)
        val baseline = (loan.number("paidCount") ?: 0.0) + (extras.number(id) ?: 0.0)
        val linkedCount = events.sumOf { e ->
            val count = e.number("installmentCount") ?: 1.0
            if (count > 0 && count.isFinite() && count % 1.0 == 0.0) count.toInt() else 1
        }
        val remainingCount = (installments - min(installments.toDouble(),baseline + linkedCount)).toInt().coerceAtLeast(0)
        val paid = min(installments.toDouble(), baseline) * installment +
            events.sumOf { (it.number("amount") ?: 0.0).coerceAtLeast(0.0) }
        val outstanding = max(0.0,(loan.number("total") ?: 0.0) - paid -
            (loan.number("forgivenAmount") ?: 0.0))
        val eventDays = events.mapNotNull { e ->
            e.string("date")?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?.takeLast(2)?.toIntOrNull()
        }
        val typical = if (eventDays.isNotEmpty()) {
            (eventDays.sum().toDouble() / eventDays.size).roundToInt().coerceIn(1,31)
        } else {
            // Web loans.ts: a present firstExpectedDate takes precedence even
            // when its day cannot be parsed; don't silently fall back to day.
            val first = loan.string("firstExpectedDate")
            if (!first.isNullOrEmpty()) {
                first.drop(8).take(2).toIntOrNull()?.takeIf { it != 0 }
            } else {
                // JS parseInt(day, 10) accepts legacy labels such as "15η".
                loan.string("day")?.trimStart()
                    ?.takeWhile { it in '0'..'9' }
                    ?.toIntOrNull()?.takeIf { it in 1..31 }
            }
        }
        CanonicalLoanBalanceRow(
            id,loan.string("name").orEmpty().ifBlank { id },loan.string("provider").orEmpty(),
            loan.number("total") ?: 0.0,installment,outstanding,remainingCount,typical,
            self,loan.string("accountingMode") == "liability-repayment",
            loan.string("defaultAccountId").orEmpty(),
        )
    }
}

/** Web lendingRows per-person including legacy baseline and as-of canonical receivableDelta. */
data class CanonicalLendingPersonRow(
    val person: String, val outstanding: Double, val events: Int,
)

fun CanonicalFinanceDocument.canonicalLendingPersonRows(asOf: String? = null): List<CanonicalLendingPersonRow> {
    val rows = linkedMapOf<String,CanonicalLendingPersonRow>()
    seed.array("lending").mapNotNull { it as? JsonObject }.forEach { legacy ->
        val person=legacy.string("person").orEmpty().trim()
        if (person.isBlank()) return@forEach
        val current=rows[person] ?: CanonicalLendingPersonRow(person,0.0,0)
        rows[person]=current.copy(
            outstanding=current.outstanding+(legacy.number("outstanding") ?: 0.0),
            events=current.events+legacy.array("entries").size,
        )
    }
    state.array("events").mapNotNull { it as? JsonObject }.forEach { event ->
        val person=event.string("person").orEmpty().trim()
        val date=event.string("date").orEmpty().trim()
        val delta=event.number("receivableDelta") ?: 0.0
        // Web lendingRows ignores malformed dates only for dated snapshots.
        // The all-time view still includes a historic receivableDelta with no date.
        if (person.isEmpty() || delta==0.0 ||
            (asOf!=null && (!date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) || date>asOf))
        ) return@forEach
        val current=rows[person] ?: CanonicalLendingPersonRow(person,0.0,0)
        rows[person]=current.copy(outstanding=current.outstanding+delta,events=current.events+1)
    }
    val collator=Collator.getInstance(Locale.forLanguageTag("el-GR"))
    return rows.values.sortedWith(Comparator { a,b ->
        val amount=b.outstanding.compareTo(a.outstanding)
        if (amount!=0) amount else collator.compare(a.person,b.person)
    })
}
