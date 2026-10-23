package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Web LoansPage upsert of installment/ordinary-loan definitions only. Existing
 * transfer-funded self-loans must go through their separate atomic transfer workflow.
 *
 * This mutation does NOT create any payment, refund, loan funding, or ledger event.
 * The original loan identity, paidCount, forgivenAmount and unrelated metadata
 * survive expected-row optimistic replay.
 */
data class UpsertCanonicalLoanMetadata(
    val source: String,
    val loan: JsonObject,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση στοιχείων δανείου"

    private val loanId: String get() = loan.string("id").orEmpty()

    private fun sourceRows(document: CanonicalFinanceDocument): List<JsonObject> {
        val seeded = document.seed.array("loans").filterIsInstance<JsonObject>()
        val customs = document.state.array("customLoans").filterIsInstance<JsonObject>()
        val overrides = document.state.obj("loanOverrides")
        return when (source) {
            "seed" -> seeded.filter { it.string("id") == loanId }.map {
                (overrides[loanId] as? JsonObject) ?: it
            }
            "custom" -> customs.filter { it.string("id") == loanId }
            else -> emptyList()
        }
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        loanId.isNotBlank() && sourceRows(document).singleOrNull() == loan

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(source == "seed" || source == "custom") { "Μη έγκυρη πηγή δανείου." }
        require(loanId.isNotBlank() && loanId.length <= 120) { "Μη έγκυρο ID δανείου." }
        val name = loan.string("name").orEmpty().trim()
        require(name.isNotEmpty() && name.length <= 500) { "Δώσε όνομα δανείου." }
        val kind = loan.string("kind")
        require(kind in setOf("loan", "installment") &&
            loan.string("source") != "self-loan" &&
            !loan.isOwnLoan()) {
            "Η ΒΟΗΘΕΙΑ χρειάζεται ξεχωριστή λογιστική μεταφορά."
        }
        val total = loan.number("total") ?: Double.NaN
        val installment = loan.number("installment") ?: Double.NaN
        val count = loan.number("installments") ?: Double.NaN
        require(total.isFinite() && total > 0.0 &&
            installment.isFinite() && installment > 0.0 &&
            count.isFinite() && count % 1.0 == 0.0 && count in 1.0..1200.0) {
            "Το ποσό, η δόση και ο αριθμός δόσεων πρέπει να είναι θετικά."
        }
        val date = loan.string("firstExpectedDate").orEmpty()
        require(date.isBlank() || runCatching { LocalDate.parse(date) }.isSuccess) {
            "Μη έγκυρη πρώτη ημερομηνία δόσης."
        }
        val accountId = loan.string("defaultAccountId").orEmpty()
        require(accountId.isNotBlank() &&
            document.canonicalAccounts().count { it.id == accountId && it.kind != "credit" } == 1) {
            "Διάλεξε έναν υπαρκτό μη πιστωτικό λογαριασμό."
        }
        require(loan.string("accountingMode") in
            setOf("expense-per-installment", "liability-repayment")) {
            "Μη έγκυρος λογιστικός τρόπος δανείου."
        }
        val seeded = document.seed.array("loans").filterIsInstance<JsonObject>()
        val customs = document.state.array("customLoans").filterIsInstance<JsonObject>()
        require(seeded.count { it.string("id") == loanId } +
            customs.count { it.string("id") == loanId } <= 1) {
            "Διπλό ιστορικό ID δανείου: δεν επιτρέπονται αλλαγές."
        }
        val rows = sourceRows(document)
        require(rows.size <= 1) { "Ασαφής ταυτότητα δανείου." }
        if (satisfiedBy(document)) return document
        if (expected == null) {
            require(source == "custom" && rows.isEmpty() &&
                seeded.none { it.string("id") == loanId }) {
                "Το νέο δάνειο υπάρχει ήδη."
            }
        } else {
            require(rows.singleOrNull() == expected &&
                expected.string("id") == loanId &&
                !expected.isOwnLoan()) {
                "Το δάνειο άλλαξε σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
            }
            // Never reset financial progress or change its accounting identity.
            require(expected.string("kind") == kind &&
                expected.number("paidCount") == loan.number("paidCount") &&
                expected.number("forgivenAmount") == loan.number("forgivenAmount") &&
                expected.string("accountingMode") == loan.string("accountingMode")) {
                "Η λογιστική ταυτότητα του δανείου δεν τροποποιείται εδώ."
            }
        }
        val nextState = if (source == "seed") {
            require(expected != null && seeded.count { it.string("id") == loanId } == 1)
            val overrides = document.state.obj("loanOverrides")
            document.state.updated("loanOverrides",
                JsonObject(overrides + (loanId to loan)))
        } else {
            val rowsRaw = document.state.array("customLoans")
            val nextRows = if (expected == null) rowsRaw + loan
                else rowsRaw.map { raw ->
                    if ((raw as? JsonObject)?.string("id") == loanId) loan else raw
                }
            document.state.updated("customLoans", JsonArray(nextRows))
        }
        return document.withMutableState(nextState, nowIso)
    }
}
