package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.abs
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Exact non-posting Web lending.ts legacyLendingSnapshot contract.
 * Reconstruct a dated balance only when *all* imported history is valid,
 * cent-exact, nonnegative at every chronological step, and matches the
 * preserved final legacy outstanding amount. Otherwise keep the original
 * seed snapshot, never invent/mutate historical receivables.
 */
internal data class CanonicalLegacyLendingSnapshot(
    val outstanding: Double,
    val events: Int,
    val reconstructed: Boolean,
)

private const val LEGACY_SAFE_CENTS = 9_007_199_254_740_991L
private val LEGACY_DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

internal fun canonicalLegacyLendingSnapshot(
    record: JsonObject,
    asOf: String?,
): CanonicalLegacyLendingSnapshot {
    val fallback = record.number("outstanding")?.takeIf(Double::isFinite) ?: 0.0
    val entries = record.array("entries")
    val preserved = CanonicalLegacyLendingSnapshot(fallback, entries.size, false)
    if (asOf == null || runCatching { LocalDate.parse(asOf) }.isFailure ||
        entries.isEmpty()
    ) return preserved

    data class Move(val date: String, val cents: Long)
    val dated = mutableListOf<Move>()
    for (item in entries) {
        val row = item as? JsonObject ?: return preserved
        val date = row.string("date")?.trim().orEmpty()
        if (!LEGACY_DATE.matches(date) ||
            runCatching { LocalDate.parse(date) }.isFailure
        ) return preserved
        var delta = 0L
        for ((key, sign) in listOf("lent" to 1L, "repaid" to -1L, "haircut" to -1L)) {
            val raw = row[key] ?: continue
            if (raw is JsonNull) continue
            val primitive = raw as? JsonPrimitive ?: return preserved
            if (primitive.isString && primitive.content.isBlank()) return preserved
            val amount = primitive.content.toDoubleOrNull()
                ?.takeIf { it.isFinite() && it >= 0.0 } ?: return preserved
            val cents = runCatching { moneyToCents(amount) }.getOrNull()
                ?: return preserved
            if (abs(amount * 100.0 - cents.toDouble()) > 1e-6 ||
                cents !in 0L..LEGACY_SAFE_CENTS
            ) return preserved
            delta = runCatching { Math.addExact(delta, Math.multiplyExact(cents, sign)) }
                .getOrNull() ?: return preserved
            if (delta !in -LEGACY_SAFE_CENTS..LEGACY_SAFE_CENTS) return preserved
        }
        dated += Move(date, delta)
    }
    val expected = runCatching { moneyToCents(fallback) }.getOrNull()
        ?: return preserved
    if (expected !in 0L..LEGACY_SAFE_CENTS ||
        abs(fallback * 100.0 - expected.toDouble()) > 1e-6
    ) return preserved

    var running = 0L
    var at = 0L
    var count = 0
    for (row in dated.sortedBy { it.date }) {
        running = runCatching { Math.addExact(running, row.cents) }.getOrNull()
            ?: return preserved
        if (running !in 0L..LEGACY_SAFE_CENTS) return preserved
        if (row.date <= asOf) {
            at = running
            count += 1
        }
    }
    if (running != expected) return preserved
    return CanonicalLegacyLendingSnapshot(centsToMoney(at), count, true)
}
