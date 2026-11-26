package app.myfinhub.android.feature.receipt

import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.activity.ActivityCategoryOption
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Locale

/** OCR is advisory: only reviewed fields are passed to the normal Quick Entry reducer. */
internal data class ReceiptLine(val label: String, val cents: Long)
internal data class ReceiptProposal(
    val merchant: String = "",
    val date: LocalDate? = null,
    val totalCents: Long? = null,
    val currency: ReceiptCurrency = ReceiptCurrency.UNKNOWN,
    val items: List<ReceiptLine> = emptyList(),
    val qrReview: ReceiptQrReview? = null,
    val ocrAmbiguous: Boolean = false,
)
internal enum class ReceiptCurrency { EUR, FOREIGN, UNKNOWN }
data class ReceiptPrefill(
    val amountText: String,
    val dateText: String,
    val merchant: String,
    val splitLines: List<ReceiptPrefillLine> = emptyList(),
    val categorySuggestion: ReceiptCategorySuggestion? = null,
)
data class ReceiptPrefillLine(val label: String, val amountText: String)
data class ReceiptCategorySuggestion(val category: String, val subcategory: String = "")

private val totalWords = listOf("συνολο", "πληρωτεο", "τελικο", "total", "amount due", "payable")
private val excludedWords = listOf(
    "μερικο", "υποσυνολο", "subtotal", "ρεστα", "change", "μετρητα", "cash", "φπα", "vat", "tax",
    "εκπτωση", "discount", "καθαρη αξια", "net amount", "τεμ", "quantity",
)
private val merchantNoise = listOf("αποδειξη", "λιανικης", "receipt", "ευχαριστ", "thank you", "τηλ", "tel", "αφμ", "vat", "δ.ο.υ", "ημερομηνια", "date", "ωρα", "time")
private val foreignCurrency = Regex("""(?i)(?<!\p{L})(USD|GBP|CAD|AUD|CHF|JPY|TRY|BGN|PLN|RON|SEK|NOK|DKK|CZK|HUF|RSD|AED|CNY|INR|RUB)(?!\p{L})|[$£¥₹₽₺]""")
private val euroCurrency = Regex("""(?iu)€|(?<!\p{L})EUR(?!\p{L})|ΕΥΡ[ΩΏ]""")
private val moneyToken = Regex("""(?<![\d.,-])(?:\d{1,3}(?:[. ,]\d{3})+|\d+)[,.]\d{2}(?![\d.,])""")
private val receiptDate = Regex("""(?<!\d)([0-3]?\d)[./-]([01]?\d)[./-](\d{4}|\d{2})(?!\d)""")

private fun String.foldReceipt(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("""\p{M}+"""), "").lowercase(Locale.ROOT).replace('ς', 'σ')

internal fun receiptAmountText(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

internal fun receiptReviewedCents(value: String): Long? = runCatching {
    val normalized = value.trim().replace(',', '.')
    if (!Regex("""\d{1,6}(?:\.\d{1,2})?""").matches(normalized)) return null
    BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it in 1..99_999_999 }
}.getOrNull()

private fun tokenCents(token: String): Long? {
    val separator = maxOf(token.lastIndexOf(','), token.lastIndexOf('.'))
    if (separator < 1) return null
    val whole = token.substring(0, separator)
    val fraction = token.substring(separator + 1)
    // Reject mixed/invalid thousands separators rather than silently inflating an amount.
    if (!whole.matches(Regex("""\d+|\d{1,3}([., ])\d{3}(?:\1\d{3})*"""))) return null
    return receiptReviewedCents(whole.replace(Regex("""[., ]"""), "") + "." + fraction)
}

private fun parseDate(text: String): LocalDate? = receiptDate.find(text)?.let { match ->
    val (d, m, rawYear) = match.destructured
    val year = rawYear.toInt().let { if (it < 100) 2000 + it else it }
    if (year !in 2000..2100) return null
    runCatching { LocalDate.of(year, m.toInt(), d.toInt()) }.getOrNull()
}

internal fun parseReceiptText(rawText: String): ReceiptProposal {
    val lines = rawText.take(24_000).lineSequence().map {
        it.replace(Regex("""[\t\u00a0 ]+"""), " ").trim().take(220)
    }.filter { it.isNotBlank() }.take(240).toList()
    val text = lines.joinToString("\n")
    val currency = when {
        foreignCurrency.containsMatchIn(text) -> ReceiptCurrency.FOREIGN
        euroCurrency.containsMatchIn(text) -> ReceiptCurrency.EUR
        else -> ReceiptCurrency.UNKNOWN
    }
    val dates = lines.mapNotNull(::parseDate).distinct()
    val candidates = lines.mapIndexedNotNull { index, line ->
        val folded = line.foldReceipt()
        // Only explicit totals: never choose the largest item, cash tender or VAT.
        if (totalWords.none { folded.contains(it.foldReceipt()) } ||
            excludedWords.any { folded.contains(it.foldReceipt()) } ||
            folded.contains("επιμερουσ")) return@mapIndexedNotNull null
        val tokens = moneyToken.findAll(line).mapNotNull { tokenCents(it.value) }.toList()
        val next = lines.getOrNull(index + 1)?.let { nextLine ->
            // Common wrapped total, only on a standalone numeric currency line.
            if (nextLine.replace(euroCurrency, "").trim().matches(moneyToken))
                tokenCents(moneyToken.find(nextLine)?.value.orEmpty()) else null
        }
        (tokens.singleOrNull() ?: next?.takeIf { tokens.isEmpty() })?.let { index to it }
    }
    val distinctTotals = candidates.map { it.second }.distinct()
    val total = distinctTotals.singleOrNull()
    val firstTotal = candidates.firstOrNull()?.first ?: lines.size
    val merchant = lines.take(8).firstOrNull { line ->
        val key = line.foldReceipt()
        line.count(Char::isLetter) >= 3 && merchantNoise.none { key.contains(it.foldReceipt()) } &&
            totalWords.none { key.contains(it.foldReceipt()) } &&
            excludedWords.none { key.contains(it.foldReceipt()) } &&
            parseDate(line) == null && moneyToken.find(line) == null
    }.orEmpty().take(90)
    val items = lines.take(firstTotal).mapNotNull { line ->
        val key = line.foldReceipt()
        if (excludedWords.any { key.contains(it.foldReceipt()) } ||
            merchantNoise.any { key.contains(it.foldReceipt()) } || parseDate(line) != null) return@mapNotNull null
        val matches = moneyToken.findAll(line).toList()
        if (matches.size != 1) return@mapNotNull null
        val match = matches.single()
        val label = line.substring(0, match.range.first).trim().take(90)
        val remainder = line.substring(match.range.last + 1).replace(euroCurrency, "").trim()
        if (label.count(Char::isLetter) < 2 || remainder.isNotBlank()) return@mapNotNull null
        val cents = tokenCents(match.value) ?: return@mapNotNull null
        ReceiptLine(label, cents)
    }
    return ReceiptProposal(merchant, dates.singleOrNull(), total, currency, items.take(40),
        ocrAmbiguous = dates.size > 1 || distinctTotals.size > 1)
}

internal fun reviewedReceiptSplit(
    lines: List<ReceiptLine>,
    totalCents: Long?,
    currencyConfirmed: Boolean,
): List<ReceiptPrefillLine> {
    if (!currencyConfirmed || totalCents == null || lines.size !in 2..12 ||
        lines.any { it.label.isBlank() || it.label.length > 90 || it.cents !in 1..99_999_999 } ||
        lines.sumOf { it.cents } != totalCents) return emptyList()
    return lines.map { ReceiptPrefillLine(it.label, receiptAmountText(it.cents)) }
}

private fun merchantKey(text: String): String =
    text.foldReceipt().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()

/** Advisory match on exact canonical purchase cents/date; no automatic link or mutation. */
internal fun receiptPurchaseMatches(
    items: List<ActivityItem>,
    cents: Long?,
    date: LocalDate?,
    merchant: String,
): List<ActivityItem> {
    if (cents == null || cents <= 0 || date == null) return emptyList()
    val key = merchantKey(merchant)
    fun merchantMatches(item: ActivityItem): Boolean {
        val other = merchantKey(item.note)
        return key.length >= 4 && other.length >= 4 && (other.contains(key) || key.contains(other))
    }
    fun day(item: ActivityItem) = runCatching { LocalDate.parse(item.rawDate) }.getOrNull()
    fun distance(item: ActivityItem) = day(item)?.let { kotlin.math.abs(ChronoUnit.DAYS.between(it, date)) } ?: Long.MAX_VALUE
    return items.filter { item ->
        item.canonicalKind in setOf("expense", "card_purchase", "split") &&
            distance(item) <= 3 &&
            item.financeExpenseContribution?.let { amount ->
                amount.isFinite() && amount > 0 &&
                    kotlin.math.abs(amount - cents / 100.0) < 1e-8
            } == true
    }.distinctBy { it.isLegacyTransaction to it.id }
        .sortedWith(compareByDescending<ActivityItem> { merchantMatches(it) }.thenBy { distance(it) }.thenBy { it.id })
        .take(6)
}


/**
 * Advisory only: exact normalized merchant history, one unambiguous current
 * taxonomy pair, no pending, split, refund, transfer or zero-value rows.
 */
internal fun receiptCategorySuggestion(
    items: List<ActivityItem>,
    categories: List<ActivityCategoryOption>,
    merchant: String,
): ReceiptCategorySuggestion? {
    val key = merchantKey(merchant)
    if (key.length < 4) return null
    val candidates = items.filter { item ->
        !item.pendingSync && item.canonicalKind in setOf("expense", "card_purchase") &&
            item.financeExpenseContribution?.let { it.isFinite() && it > 0 } == true &&
            merchantKey(item.note.lineSequence().firstOrNull().orEmpty()) == key
    }.mapNotNull { item ->
        val category = item.category?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val option = categories.singleOrNull { it.name == category } ?: return@mapNotNull null
        val subcategory = item.subcategory.orEmpty()
        if (subcategory.isNotBlank() && subcategory !in option.subcategories) return@mapNotNull null
        ReceiptCategorySuggestion(category, subcategory)
    }.distinct()
    return candidates.singleOrNull()
}
