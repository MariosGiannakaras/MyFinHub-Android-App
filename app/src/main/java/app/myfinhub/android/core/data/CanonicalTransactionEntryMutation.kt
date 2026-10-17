package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class CanonicalTransactionSplitDraft(
    val id: String,
    val label: String = "",
    val category: String,
    val subcategory: String? = null,
    val amount: Double,
)

data class CanonicalTransactionEntryDraft(
    val kind: String,
    val date: String,
    val amount: Double? = null,
    val note: String = "",
    val category: String? = null,
    val subcategory: String? = null,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
    val person: String? = null,
    val expectedReturnDate: String? = null,
    val cardId: String? = null,
    val statementId: String? = null,
    // Web FinanceEvent loans.ts: linked payments keep the normal EventKind.
    val loanId: String? = null,
    val installmentCount: Int? = null,
    // Web recurring.ts and ContextualQuickAdd annotate existing expense events.
    val recurringId: String? = null,
    val actualBalance: Double? = null,
    val parts: List<CanonicalTransactionSplitDraft> = emptyList(),
)

/**
 * Canonical Android transaction-entry mutation matching the shared desktop accounting semantics.
 *
 * The UI chooses the intent, but this function remains the accounting boundary: it validates
 * account/card references, derives ledger legs and semantic deltas, normalizes money to cents and
 * returns a replayable [AppendCanonicalEvent] with a stable caller-provided id/timestamp.
 */
fun createCanonicalTransactionEntryMutation(
    document: CanonicalFinanceDocument,
    draft: CanonicalTransactionEntryDraft,
    eventId: String,
    nowIso: String,
): CanonicalFinanceMutation {
    require(eventId.isNotBlank()) { "Απαιτείται αναγνωριστικό κίνησης." }
    val eventDate = runCatching { LocalDate.parse(draft.date) }
        .getOrElse { throw IllegalArgumentException("Η ημερομηνία δεν είναι έγκυρη.") }
    require(draft.kind in SUPPORTED_TRANSACTION_ENTRY_KINDS) { "Ο τύπος κίνησης δεν υποστηρίζεται." }

    val eligibleAccounts = document.canonicalAccounts()
        .filter { it.kind != "credit" }
        .associateBy { it.id }
    val activeCreditCards = document.canonicalCards()
        .filter { it.active && it.kind == "credit" }
        .associateBy { it.id }

    val loan = draft.loanId?.let { id ->
        require(id.isNotBlank() && id == id.trim()) { "Διάλεξε υπαρκτό δάνειο." }
        document.canonicalLoanDefinition(id)
            ?: throw IllegalArgumentException("Το δάνειο δεν είναι πλέον διαθέσιμο.")
    }
    if (loan == null) {
        require(draft.installmentCount == null) { "Ο αριθμός δόσεων απαιτεί συνδεδεμένο δάνειο." }
    } else {
        val self = loan.isOwnLoan()
        val expectedKind = when {
            self -> "transfer"
            loan.string("accountingMode") == "liability-repayment" -> "card_payment"
            else -> "expense"
        }
        require(draft.kind == expectedKind) {
            "Ο τρόπος εξόφλησης δεν συμφωνεί με το συνδεδεμένο δάνειο."
        }
        val debt = document.canonicalLoanBalanceRows(draft.date).firstOrNull {
            it.id == draft.loanId
        }?.remaining ?: 0.0
        require(debt > 0.005) { "Το δάνειο δεν έχει υπόλοιπο προς πληρωμή." }
        val amount = draft.amount ?: throw IllegalArgumentException("Συμπλήρωσε ποσό δόσης.")
        val cents = moneyToCents(amount)
        require(cents > 0 && centsToMoney(cents) <= debt + 0.005) {
            "Η πληρωμή υπερβαίνει το υπόλοιπο του δανείου."
        }
        if (self) {
            require(draft.installmentCount == null) {
                "Η επιστροφή προσωπικού δανείου δεν μετρά δόσεις."
            }
            require(Regex("^(?:ΕΠΙΣΤΡΟΦΗ|RETURN)(?:\\s|:|$)", RegexOption.IGNORE_CASE)
                .containsMatchIn(draft.note.trim())) {
                "Η επιστροφή ΒΟΗΘΕΙΑΣ πρέπει να έχει αναγνωρίσιμη περιγραφή."
            }
        } else {
            val count = draft.installmentCount
            require(count != null && count > 0) { "Επίλεξε έγκυρο πλήθος δόσεων." }
            val plan = document.canonicalLoanInstallmentPaymentPlan(draft.loanId!!, count, draft.date)
                ?: throw IllegalArgumentException("Δεν υπάρχουν διαθέσιμες δόσεις.")
            require(count == plan.count && cents == moneyToCents(plan.amount)) {
                "Το ποσό πρέπει να ισούται με τις επιλεγμένες δόσεις."
            }
        }
    }

    draft.recurringId?.let { recurringId ->
        require(recurringId.isNotBlank() && recurringId == recurringId.trim()) {
            "Διάλεξε υπαρκτό πάγιο."
        }
        require(draft.loanId == null && draft.kind == "expense") {
            "Οι πληρωμές παγίων είναι έξοδα και δεν συνδέονται με δάνειο."
        }
        require(document.allCanonicalRecurringItems().any { it.id == recurringId }) {
            "Το πάγιο δεν είναι πλέον διαθέσιμο."
        }
    }

    fun requireAccount(id: String?, message: String): String {
        val normalized = id?.trim().orEmpty()
        require(normalized.isNotBlank() && normalized in eligibleAccounts) { message }
        return normalized
    }

    fun requireCard(id: String?): String {
        val normalized = id?.trim().orEmpty()
        require(normalized.isNotBlank() && normalized in activeCreditCards) { "Διάλεξε ενεργή πιστωτική κάρτα." }
        return normalized
    }

    fun positiveAmount(): Pair<Long, Double> {
        val raw = draft.amount ?: throw IllegalArgumentException("Συμπλήρωσε θετικό ποσό.")
        val cents = moneyToCents(raw)
        require(cents > 0L) { "Συμπλήρωσε θετικό ποσό." }
        return cents to centsToMoney(cents)
    }

    val legs = mutableListOf<JsonObject>()
    val normalizedParts = mutableListOf<JsonObject>()
    var savingAmount = 0.0
    var receivableDelta = 0.0
    var creditDelta = 0.0
    var normalizedAmount = 0.0
    var normalizedAccountId = draft.accountId?.trim()?.takeIf(String::isNotBlank)
    var normalizedFromId = draft.fromAccountId?.trim()?.takeIf(String::isNotBlank)
    var normalizedToId = draft.toAccountId?.trim()?.takeIf(String::isNotBlank)
    var normalizedCardId = draft.cardId?.trim()?.takeIf(String::isNotBlank)
    var selectedStatementId: String? = null

    when (draft.kind) {
        "expense" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό πληρωμής.")
            legs += transactionLedgerLeg(normalizedAccountId!!, -amount)
        }
        "income" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό πίστωσης.")
            legs += transactionLedgerLeg(normalizedAccountId!!, amount)
        }
        "transfer", "withdrawal" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedFromId = requireAccount(draft.fromAccountId, "Διάλεξε υπαρκτό λογαριασμό προέλευσης.")
            normalizedToId = requireAccount(draft.toAccountId, "Διάλεξε υπαρκτό λογαριασμό προορισμού.")
            require(normalizedFromId != normalizedToId) { "Οι λογαριασμοί πρέπει να είναι διαφορετικοί." }
            if (draft.kind == "withdrawal") {
                require(eligibleAccounts[normalizedToId]?.kind == "cash") { "Η ανάληψη πρέπει να καταλήγει σε λογαριασμό μετρητών." }
            }
            if (loan != null) {
                require(loan.isOwnLoan() && eligibleAccounts[normalizedToId]?.isSavingsAccount() == true) {
                    "Η επιστροφή ΒΟΗΘΕΙΑΣ πρέπει να καταλήγει σε αποταμιευτικό λογαριασμό."
                }
            }
            legs += transactionLedgerLeg(normalizedFromId!!, -amount)
            legs += transactionLedgerLeg(normalizedToId!!, amount)
        }
        "saving_cash_offset" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedFromId = requireAccount(draft.fromAccountId, "Διάλεξε υπαρκτό λογαριασμό προέλευσης.")
            normalizedToId = requireAccount(draft.toAccountId, "Διάλεξε υπαρκτό λογαριασμό αποταμίευσης.")
            require(normalizedFromId != normalizedToId) { "Οι λογαριασμοί αποταμίευσης πρέπει να είναι διαφορετικοί." }
            require(eligibleAccounts[normalizedToId]?.isSavingsAccount() == true) { "Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης." }
            require(eligibleAccounts[normalizedFromId]?.isSavingContributionSource() == true) {
                "Η αποταμίευση πρέπει να ξεκινά από τραπεζικό τρεχούμενο/μισθοδοσίας."
            }
            legs += transactionLedgerLeg(normalizedFromId!!, -amount)
            legs += transactionLedgerLeg(normalizedToId!!, amount)
            savingAmount = amount
        }
        "refund" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό επιστροφής.")
            legs += transactionLedgerLeg(normalizedAccountId!!, amount)
        }
        "lending" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό πληρωμής.")
            require(!draft.person.isNullOrBlank()) { "Συμπλήρωσε το πρόσωπο για τα δανεικά." }
            draft.expectedReturnDate?.trim()?.takeIf(String::isNotBlank)?.let { rawDate ->
                val due = runCatching { LocalDate.parse(rawDate) }
                    .getOrElse { throw IllegalArgumentException("Η αναμενόμενη επιστροφή δεν είναι έγκυρη.") }
                require(!due.isBefore(eventDate)) { "Η αναμενόμενη επιστροφή δεν μπορεί να είναι πριν από την ημερομηνία κίνησης." }
            }
            legs += transactionLedgerLeg(normalizedAccountId!!, -amount)
            receivableDelta = amount
        }
        "repayment" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό πίστωσης.")
            val person = draft.person?.trim().orEmpty()
            require(person.isNotBlank()) { "Συμπλήρωσε το πρόσωπο για τα δανεικά." }
            val outstanding = document.lendingOutstandingForPerson(person)
            require(outstanding > 0.005) { "Δεν υπάρχει καταγεγραμμένη οφειλή για αυτό το πρόσωπο." }
            require(amount <= outstanding + 0.01) { "Η επιστροφή δεν μπορεί να ξεπερνά την οφειλή του προσώπου." }
            legs += transactionLedgerLeg(normalizedAccountId!!, amount)
            receivableDelta = -amount
        }
        "card_purchase" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedCardId = requireCard(draft.cardId)
            legs += transactionLedgerLeg(CREDIT_ACCOUNT_ID, -amount)
            creditDelta = -amount
        }
        "card_payment" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            val sourceAccountId = requireAccount(draft.fromAccountId, "Διάλεξε υπαρκτό λογαριασμό πληρωμής.")
            normalizedFromId = sourceAccountId
            if (loan != null) {
                // Web ContextualQuickAdd liability-repayment uses card_payment with
                // loanId but intentionally no cardId or card statement allocation.
                require(draft.cardId == null && draft.statementId == null) {
                    "Η δόση υποχρέωσης δεν συνδέεται με ξεχωριστή πιστωτική."
                }
            } else {
                val cardId = requireCard(draft.cardId)
                normalizedCardId = cardId
                val debt = document.creditDebtForCardAt(cardId, draft.date)
                require(debt > 0.005) { "Η συγκεκριμένη κάρτα δεν έχει τρέχουσα οφειλή." }
                require(amount <= debt + 0.01) { "Η πληρωμή δεν μπορεί να ξεπερνά την οφειλή της κάρτας." }
                selectedStatementId = draft.statementId?.trim()?.takeIf(String::isNotBlank)
                selectedStatementId?.let { statementId ->
                    val statement = document.creditStatementViews(cardId, draft.date)
                        .firstOrNull { it.id == statementId }
                        ?: throw IllegalArgumentException("Η επιλεγμένη δήλωση δεν ανήκει στην πιστωτική κάρτα.")
                    require(statement.remaining > 0.005) { "Η επιλεγμένη δήλωση έχει ήδη εξοφληθεί." }
                    require(amount <= statement.remaining + 0.01) {
                        "Η πληρωμή δεν μπορεί να ξεπερνά το υπόλοιπο της επιλεγμένης δήλωσης."
                    }
                }
                val cardBankId = activeCreditCards[cardId]?.bankId.orEmpty()
                require(eligibleAccounts[sourceAccountId]?.matchesFinancialProvider(cardBankId) == true) {
                    "Η αποπληρωμή πρέπει να γίνει από διαθέσιμο λογαριασμό της ίδιας τράπεζας με την κάρτα."
                }
            }
            legs += transactionLedgerLeg(sourceAccountId, -amount)
            legs += transactionLedgerLeg(CREDIT_ACCOUNT_ID, amount)
            creditDelta = amount
        }
        "reconciliation" -> {
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό διόρθωσης.")
            val actual = draft.actualBalance
                ?: throw IllegalArgumentException("Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο.")
            require(actual.isFinite()) { "Συμπλήρωσε έγκυρο πραγματικό υπόλοιπο." }
            val current = document.accountBalances(draft.date)[normalizedAccountId] ?: 0.0
            val deltaCents = moneyToCents(actual - current)
            normalizedAmount = centsToMoney(abs(deltaCents))
            legs += transactionLedgerLeg(normalizedAccountId!!, centsToMoney(deltaCents))
        }
        "split" -> {
            normalizedAccountId = requireAccount(draft.accountId, "Διάλεξε υπαρκτό λογαριασμό πληρωμής.")
            require(draft.parts.size >= 2) { "Ο διαχωρισμός χρειάζεται τουλάχιστον δύο μέρη." }
            require(draft.parts.map { it.id }.all(String::isNotBlank)) { "Κάθε μέρος χρειάζεται αναγνωριστικό." }
            require(draft.parts.map { it.id }.distinct().size == draft.parts.size) { "Τα μέρη του διαχωρισμού πρέπει να είναι μοναδικά." }
            var totalCents = 0L
            draft.parts.forEach { part ->
                val cents = moneyToCents(part.amount)
                require(cents > 0L) { "Κάθε μέρος του διαχωρισμού πρέπει να έχει θετικό ποσό." }
                val category = part.category.trim()
                require(category.isNotBlank()) { "Κάθε μέρος του διαχωρισμού χρειάζεται κατηγορία." }
                val amount = centsToMoney(cents)
                // Web createEvent rejects split totals outside a safe JS cent integer.
                // Guard before adding so a malformed oversized split never overflows.
                require(totalCents <= MAX_SAFE_CANONICAL_CENTS - cents) {
                    "Το σύνολο των μερών είναι εκτός επιτρεπτού εύρους."
                }
                totalCents += cents
                normalizedParts += JsonObject(buildMap {
                    put("id", JsonPrimitive(part.id))
                    put("label", JsonPrimitive(part.label.trim()))
                    put("category", JsonPrimitive(category))
                    part.subcategory?.trim()?.takeIf(String::isNotBlank)?.let { put("subcategory", JsonPrimitive(it)) }
                    put("amount", JsonPrimitive(amount))
                    put("kind", JsonPrimitive("expense"))
                })
            }
            require(totalCents > 0L) { "Το σύνολο των μερών πρέπει να είναι θετικό." }
            normalizedAmount = centsToMoney(totalCents)
            legs += transactionLedgerLeg(normalizedAccountId!!, -normalizedAmount)
        }
    }

    val event = JsonObject(buildMap {
        put("id", JsonPrimitive(eventId))
        put("date", JsonPrimitive(draft.date))
        put("kind", JsonPrimitive(draft.kind))
        put("amount", JsonPrimitive(normalizedAmount))
        put("note", JsonPrimitive(draft.note.trim()))
        draft.category?.trim()?.takeIf(String::isNotBlank)?.let { put("category", JsonPrimitive(it)) }
        draft.subcategory?.trim()?.takeIf(String::isNotBlank)?.let { put("subcategory", JsonPrimitive(it)) }
        normalizedAccountId?.let { put("accountId", JsonPrimitive(it)) }
        normalizedFromId?.let { put("fromAccountId", JsonPrimitive(it)) }
        normalizedToId?.let { put("toAccountId", JsonPrimitive(it)) }
        draft.person?.trim()?.takeIf(String::isNotBlank)?.let { put("person", JsonPrimitive(it)) }
        draft.expectedReturnDate?.trim()?.takeIf(String::isNotBlank)?.let { put("expectedReturnDate", JsonPrimitive(it)) }
        normalizedCardId?.let { put("cardId", JsonPrimitive(it)) }
        selectedStatementId?.let { put("statementId", JsonPrimitive(it)) }
        draft.loanId?.let { put("loanId", JsonPrimitive(it)) }
        draft.installmentCount?.let { put("installmentCount", JsonPrimitive(it)) }
        draft.recurringId?.let { put("recurringId", JsonPrimitive(it)) }
        put("legs", JsonArray(legs))
        if (normalizedParts.isNotEmpty()) put("parts", JsonArray(normalizedParts))
        put("savingAmount", JsonPrimitive(savingAmount))
        put("receivableDelta", JsonPrimitive(receivableDelta))
        put("creditDelta", JsonPrimitive(creditDelta))
        put("source", JsonPrimitive("user"))
        put("createdAt", JsonPrimitive(nowIso))
        put("updatedAt", JsonPrimitive(nowIso))
    })

    // Apply Web rules once, when the caller builds a new stable event intent, never
    // during server replay or while projecting previously committed history.
    val ruled = document.applyCanonicalTransactionRules(event)
    val finalEvent = if (ruled.string("note").isNullOrBlank()) {
        JsonObject(ruled + ("note" to JsonPrimitive(defaultTransactionNote(draft.kind))))
    } else ruled
    return AppendCanonicalEvent(event = finalEvent, nowIso = nowIso)
}

private val SUPPORTED_TRANSACTION_ENTRY_KINDS = setOf(
    "expense",
    "income",
    "transfer",
    "saving_cash_offset",
    "withdrawal",
    "refund",
    "lending",
    "repayment",
    "card_purchase",
    "card_payment",
    "reconciliation",
    "split",
)

private fun transactionLedgerLeg(accountId: String, amount: Double): JsonObject = JsonObject(
    mapOf(
        "accountId" to JsonPrimitive(accountId),
        "amount" to JsonPrimitive(amount),
    ),
)

private fun defaultTransactionNote(kind: String): String = when (kind) {
    "expense" -> "Έξοδο"
    "income" -> "Έσοδο"
    "transfer" -> "Μεταφορά"
    "saving_cash_offset" -> "Αποταμίευση"
    "withdrawal" -> "Ανάληψη"
    "refund" -> "Επιστροφή χρημάτων"
    "lending" -> "Δανεικά προς άλλον"
    "repayment" -> "Επιστροφή δανεικών"
    "card_purchase" -> "Αγορά με κάρτα"
    "card_payment" -> "Πληρωμή κάρτας"
    "reconciliation" -> "Διόρθωση υπολοίπου"
    "split" -> "Σύνθετη αγορά"
    else -> "Κίνηση"
}
