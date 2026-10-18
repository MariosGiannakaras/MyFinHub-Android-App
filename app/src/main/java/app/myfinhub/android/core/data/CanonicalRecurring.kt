package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.json.JsonObject

/** Web recurring.ts/recurringCadence.ts identity and cadence, without a second finance store. */
data class CanonicalRecurringItem(
    val id: String,
    val name: String,
    val amount: Double,
    val accountId: String,
    val category: String,
    val note: String,
    val active: Boolean,
    val status: String?,
    val day: Int?,
    val firstExpectedDate: String?,
    val recurrenceUnit: String?,
    val recurrenceInterval: Int,
    val logoAssetKey: String? = null,
    val source: String? = null,
    val endDate: String? = null,
) {
    val effectiveStatus: String
        get() = if (!active) {
            if (status == "paused") "paused" else "stopped"
        } else status ?: "active"

    val recurrenceMonths: Int
        get() = if (recurrenceUnit == "year") recurrenceInterval * 12 else recurrenceInterval
    val monthlyEquivalent: Double get() = amount / recurrenceMonths

    // Exact Web recurringCadenceLabel wording and fallback interval semantics.
    val cadenceLabel: String get() = if (recurrenceUnit == "year") {
        if (recurrenceInterval == 1) "Κάθε χρόνο" else "Κάθε $recurrenceInterval χρόνια"
    } else when (recurrenceInterval) {
        1 -> "Κάθε μήνα"
        6 -> "Κάθε 6 μήνες"
        else -> "Κάθε $recurrenceInterval μήνες"
    }
}

private fun JsonObject.toCanonicalRecurring(): CanonicalRecurringItem? {
    val id = string("id")?.takeIf(String::isNotBlank) ?: return null
    val rawInterval = number("recurrenceInterval")
    val interval = if (rawInterval != null && rawInterval in 1.0..120.0 &&
        rawInterval % 1.0 == 0.0) rawInterval.toInt() else 1
    return CanonicalRecurringItem(
        id = id,
        name = string("name") ?: id,
        amount = number("amount") ?: 0.0,
        accountId = string("accountId").orEmpty(),
        category = string("category").orEmpty(),
        note = string("note").orEmpty(),
        active = bool("active") == true,
        status = string("status"),
        day = number("day")?.toInt(),
        firstExpectedDate = string("firstExpectedDate"),
        recurrenceUnit = string("recurrenceUnit"),
        recurrenceInterval = interval,
        logoAssetKey = string("logoAssetKey"),
        source = string("source"),
        endDate = string("endDate"),
    )
}

/** Seed replacement by full canonical override, followed by custom recurring records. */
fun CanonicalFinanceDocument.allCanonicalRecurringItems(): List<CanonicalRecurringItem> {
    val overrides = state.obj("recurringOverrides")
    val seeded = seed.array("recurring").mapNotNull { raw ->
        val original = raw as? JsonObject ?: return@mapNotNull null
        val id = original.string("id") ?: return@mapNotNull null
        ((overrides[id] as? JsonObject) ?: original).toCanonicalRecurring()
    }
    return seeded + state.array("recurringCustom")
        .mapNotNull { (it as? JsonObject)?.toCanonicalRecurring() }
}

fun CanonicalFinanceDocument.activeCanonicalRecurringItems(): List<CanonicalRecurringItem> =
    allCanonicalRecurringItems().filter { it.effectiveStatus == "active" }

fun CanonicalFinanceDocument.canonicalRecurringMonthlyTotal(): Double =
    activeCanonicalRecurringItems().sumOf(CanonicalRecurringItem::monthlyEquivalent)

private fun LocalDate.addRecurringMonths(months: Int): LocalDate {
    val destination = YearMonth.from(this).plusMonths(months.toLong())
    return destination.atDay(min(dayOfMonth, destination.lengthOfMonth()))
}

private fun dateOnly(raw: String?): LocalDate? =
    raw?.takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

/** Web recurringPayments(data, item.id): all linked events, latest-first.
 * Invalid dates cannot enter canonical date calculations; retain all valid
 * payments and never rewrite or delete original historical event records.
 */
data class CanonicalRecurringPaymentHistory(
    val paymentCount: Int,
    val lastPaymentDateIso: String?,
)

fun CanonicalFinanceDocument.canonicalRecurringPaymentHistory(id: String): CanonicalRecurringPaymentHistory {
    val dates = recurringPaymentDates(id)
    return CanonicalRecurringPaymentHistory(dates.size, dates.firstOrNull()?.toString())
}

/**
 * Web recurringUpcoming sorts active entries by next due date and then by
 * localized name; inactive entries follow their historical stored order.
 * Return the original identity-bearing objects, including duplicate IDs:
 * display stays independent and existing mutation guards remain in effect.
 */
fun CanonicalFinanceDocument.canonicalWebRecurringManagementOrder(
    asOf: LocalDate,
): List<CanonicalRecurringItem> {
    val indexed = allCanonicalRecurringItems().withIndex().toList()
    val collator = Collator.getInstance(Locale.forLanguageTag("el-GR"))
    val active = indexed.filter { it.value.effectiveStatus == "active" }
        .map { indexedItem ->
            Triple(
                indexedItem,
                nextCanonicalRecurringDate(indexedItem.value, asOf)?.toString() ?: "9999",
                indexedItem.value.name,
            )
        }
        .sortedWith(Comparator { a, b ->
            val due = a.second.compareTo(b.second)
            if (due != 0) due
            else {
                val name = collator.compare(a.third, b.third)
                if (name != 0) name else a.first.index.compareTo(b.first.index)
            }
        })
        .map { it.first.value }
    val inactive = indexed.filter { it.value.effectiveStatus != "active" }.map { it.value }
    return active + inactive
}

private fun CanonicalFinanceDocument.recurringPaymentDates(id: String): List<LocalDate> =
    state.array("events").mapNotNull { raw ->
        val event = raw as? JsonObject ?: return@mapNotNull null
        if (event.string("recurringId") != id) return@mapNotNull null
        val date = dateOnly(event.string("date")) ?: return@mapNotNull null
        Triple(date,event.string("createdAt").orEmpty(),event.string("id").orEmpty())
    }.sortedWith(compareByDescending<Triple<LocalDate,String,String>> { it.first }
        .thenByDescending { it.second }.thenByDescending { it.third }).map { it.first }

/** Web typicalPaymentDay uses recorded payment day(s) before first anchor/day. */
fun CanonicalFinanceDocument.canonicalTypicalRecurringPaymentDay(item: CanonicalRecurringItem): Int? {
    val payments = recurringPaymentDates(item.id)
    if (payments.isNotEmpty()) {
        return (payments.sumOf { it.dayOfMonth }.toDouble() / payments.size)
            .roundToInt().coerceIn(1,31)
    }
    // Web recurring.ts: a nonempty firstExpectedDate is authoritative for the
    // typical day, even when it is malformed. Don't silently switch to day.
    val first = item.firstExpectedDate
    return if (!first.isNullOrEmpty()) {
        first.drop(8).take(2).toIntOrNull()?.takeIf { it != 0 }
    } else item.day
}

/** Current-month linked payment advances billing, and month/year intervals clamp short months. */
fun CanonicalFinanceDocument.nextCanonicalRecurringDate(
    item: CanonicalRecurringItem,
    asOf: LocalDate,
): LocalDate? {
    if (item.effectiveStatus != "active") return null
    val first = dateOnly(item.firstExpectedDate)
    val payments = recurringPaymentDates(item.id)
    if (item.recurrenceMonths == 1) {
        if (first != null && first.isAfter(asOf)) return first
        val day = canonicalTypicalRecurringPaymentDay(item)
        // Web recurring.ts uses buildDate(Math.min(day, lastDay)): imported
        // days 32+ intentionally settle on month-end, not disappear from plans.
        if (day == null || day < 1) return first
        var month = YearMonth.from(asOf)
        var next = month.atDay(min(day,month.lengthOfMonth()))
        if (next.isBefore(asOf) || payments.any {
                !it.isAfter(asOf) && YearMonth.from(it) == YearMonth.from(asOf)
            }) {
            month = month.plusMonths(1)
            next = month.atDay(min(day,month.lengthOfMonth()))
        }
        return next
    }
    var next = first ?: payments.firstOrNull()?.addRecurringMonths(item.recurrenceMonths) ?: return null
    var count = 0
    while (next.isBefore(asOf) && count < 240) {
        val later = next.addRecurringMonths(item.recurrenceMonths)
        if (later == next) return null
        next = later
        count += 1
    }
    return next
}
