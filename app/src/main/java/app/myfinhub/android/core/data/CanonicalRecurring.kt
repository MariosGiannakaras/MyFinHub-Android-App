package app.myfinhub.android.core.data

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
) {
    val effectiveStatus: String
        get() = if (!active) {
            if (status == "paused") "paused" else "stopped"
        } else status ?: "active"

    val recurrenceMonths: Int
        get() = if (recurrenceUnit == "year") recurrenceInterval * 12 else recurrenceInterval
    val monthlyEquivalent: Double get() = amount / recurrenceMonths
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
        return (payments.sumOf(LocalDate::getDayOfMonth).toDouble() / payments.size)
            .roundToInt().coerceIn(1,31)
    }
    return dateOnly(item.firstExpectedDate)?.dayOfMonth ?: item.day
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
        if (day == null || day !in 1..31) return first
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
