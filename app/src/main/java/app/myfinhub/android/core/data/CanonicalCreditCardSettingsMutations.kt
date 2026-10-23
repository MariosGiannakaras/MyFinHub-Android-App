package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Future card purchases use these billing settings. Existing persisted creditStatements
 * and historical event statementId relationships are never migrated or rewritten.
 */
data class SetCanonicalCreditBillingSettings(
    val cardId: String,
    val closingDay: Int,
    val dueDay: Int,
    val boundaryRule: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Ρύθμιση κύκλου πιστωτικής"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val rows = document.state.array("cards").filterIsInstance<JsonObject>()
            .filter { it.string("id") == cardId }
        val actual = rows.singleOrNull() ?: return false
        return cardId.isNotBlank() && actual.string("kind") == "credit" &&
            actual.bool("active") != false &&
            actual.number("statementClosingDay") == closingDay.toDouble() &&
            actual.number("statementDueDay") == dueDay.toDouble() &&
            actual.string("statementBoundaryRule") == boundaryRule
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank() && expected.string("id") == cardId) {
            "Μη έγκυρο αναγνωριστικό πιστωτικής."
        }
        require(closingDay in 1..31 && dueDay in 1..31 &&
            boundaryRule in setOf("include-closing-day", "next-cycle")) {
            "Οι ημέρες πρέπει να είναι 1–31 και ο κανόνας κύκλου έγκυρος."
        }
        val rows = document.state.array("cards")
        val matches = rows.filterIsInstance<JsonObject>().filter { it.string("id") == cardId }
        require(matches.size == 1) { "Η πιστωτική λείπει ή το ID είναι διπλό." }
        val current = matches.single()
        require(current.string("kind") == "credit" && current.bool("active") != false) {
            "Η ρύθμιση αφορά μόνο ενεργή πιστωτική."
        }
        if (satisfiedBy(document)) return document
        require(current == expected) {
            "Τα στοιχεία της κάρτας άλλαξαν στον server. Έλεγξε την ενημερωμένη κάρτα."
        }
        val updated = JsonObject(current + mapOf(
            "statementClosingDay" to JsonPrimitive(closingDay),
            "statementDueDay" to JsonPrimitive(dueDay),
            "statementBoundaryRule" to JsonPrimitive(boundaryRule),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
        return document.withMutableState(
            document.state.updated("cards", JsonArray(rows.map {
                if ((it as? JsonObject)?.string("id") == cardId) updated else it
            })), nowIso,
        )
    }
}

/** Immutable disclosed bank-rate history. No backdated effective date and no interest postings. */
data class AddCanonicalCreditRateVersion(
    val cardId: String,
    val annualPercent: Double,
    val effectiveFrom: String,
    val earliestAllowedDate: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Καταχώριση νέου επιτοκίου"

    private fun rateEquals(row: JsonObject): Boolean =
        row.string("effectiveFrom") == effectiveFrom &&
            row.number("annualPercent") == annualPercent

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val current = document.state.array("cards").filterIsInstance<JsonObject>()
            .filter { it.string("id") == cardId }.singleOrNull() ?: return false
        val matching = current.array("creditRateHistory")
            .filterIsInstance<JsonObject>().filter { it.string("effectiveFrom") == effectiveFrom }
        return cardId.isNotBlank() && current.string("kind") == "credit" &&
            matching.size == 1 && rateEquals(matching.single())
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(cardId.isNotBlank() && expected.string("id") == cardId) {
            "Μη έγκυρο αναγνωριστικό πιστωτικής."
        }
        val start = runCatching { LocalDate.parse(effectiveFrom) }.getOrNull()
        val earliest = runCatching { LocalDate.parse(earliestAllowedDate) }.getOrNull()
        require(start != null && earliest != null && !start.isBefore(earliest)) {
            "Το νέο επιτόκιο δεν μπορεί να ισχύει πριν από την ημερομηνία καταχώρισης."
        }
        val scaled = annualPercent * 10_000.0
        require(annualPercent.isFinite() && annualPercent in 0.0..100.0 &&
            scaled.isFinite() && kotlin.math.abs(kotlin.math.round(scaled) - scaled) < 0.00001) {
            "Το ετήσιο ονομαστικό επιτόκιο πρέπει να είναι 0–100% με έως 4 δεκαδικά."
        }
        val rows = document.state.array("cards")
        val matches = rows.filterIsInstance<JsonObject>().filter { it.string("id") == cardId }
        require(matches.size == 1) { "Η κάρτα λείπει ή έχει διπλό ID." }
        val current = matches.single()
        require(current.string("kind") == "credit" && current.bool("active") != false) {
            "Η καταχώριση αφορά μόνο ενεργή πιστωτική."
        }
        val previous = current.array("creditRateHistory").filterIsInstance<JsonObject>()
        val dated = previous.filter { it.string("effectiveFrom") == effectiveFrom }
        require(dated.size <= 1) { "Βρέθηκαν διπλά επιτόκια ίδιας ημερομηνίας." }
        if (satisfiedBy(document)) return document
        require(current == expected) {
            "Τα επιτόκια άλλαξαν σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        require(dated.isEmpty()) { "Υπάρχει ήδη επιτόκιο για αυτή την ημέρα." }
        require(previous.size == current.array("creditRateHistory").size) {
            "Το ιστορικό περιέχει μη έγκυρες εγγραφές."
        }
        val next = JsonObject(mapOf(
            "effectiveFrom" to JsonPrimitive(effectiveFrom),
            "annualPercent" to JsonPrimitive(annualPercent),
        ))
        val updated = JsonObject(current + mapOf(
            "creditRateHistory" to JsonArray(
                (previous + next).sortedBy { it.string("effectiveFrom").orEmpty() }
            ),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
        return document.withMutableState(
            document.state.updated("cards", JsonArray(rows.map {
                if ((it as? JsonObject)?.string("id") == cardId) updated else it
            })), nowIso,
        )
    }
}
