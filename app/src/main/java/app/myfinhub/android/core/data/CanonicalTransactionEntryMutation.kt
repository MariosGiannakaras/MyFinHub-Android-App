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
            legs += transactionLedgerLeg(normalizedFromId!!, -amount)
            legs += transactionLedgerLeg(normalizedToId!!, amount)
        }
        "saving_cash_offset" -> {
            val (_, amount) = positiveAmount()
            normalizedAmount = amount
            normalizedFromId = requireAccount(draft.fromAccountId, "Διάλεξε υπαρκτό λογαριασμό προέλευσης.")
            normalizedToId = requireAccount(draft.toAccountId, "Διάλεξε υπαρκτό λογαριασμό αποταμίευσης.")
            require(normalizedFromId != normalizedToId) { "Οι λογαριασμοί αποταμίευσης πρέπει να είναι διαφορετικοί." }
            require(eligibleAccounts[normalizedToId]?.kind == "savings") { "Η αποταμίευση πρέπει να καταλήγει σε λογαριασμό αποταμίευσης." }
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
            require(!draft.person.isNullOrBlank()) { "Συμπλήρωσε το πρόσωπο για τα δανεικά." }
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
            normalizedFromId = requireAccount(draft.fromAccountId, "Διάλεξε υπαρκτό λογαριασμό πληρωμής.")
            normalizedCardId = requireCard(draft.cardId)
            legs += transactionLedgerLeg(normalizedFromId!!, -amount)
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
        put("note", JsonPrimitive(draft.note.trim().ifBlank { defaultTransactionNote(draft.kind) }))
        draft.category?.trim()?.takeIf(String::isNotBlank)?.let { put("category", JsonPrimitive(it)) }
        draft.subcategory?.trim()?.takeIf(String::isNotBlank)?.let { put("subcategory", JsonPrimitive(it)) }
        normalizedAccountId?.let { put("accountId", JsonPrimitive(it)) }
        normalizedFromId?.let { put("fromAccountId", JsonPrimitive(it)) }
        normalizedToId?.let { put("toAccountId", JsonPrimitive(it)) }
        draft.person?.trim()?.takeIf(String::isNotBlank)?.let { put("person", JsonPrimitive(it)) }
        draft.expectedReturnDate?.trim()?.takeIf(String::isNotBlank)?.let { put("expectedReturnDate", JsonPrimitive(it)) }
        normalizedCardId?.let { put("cardId", JsonPrimitive(it)) }
        put("legs", JsonArray(legs))
        if (normalizedParts.isNotEmpty()) put("parts", JsonArray(normalizedParts))
        put("savingAmount", JsonPrimitive(savingAmount))
        put("receivableDelta", JsonPrimitive(receivableDelta))
        put("creditDelta", JsonPrimitive(creditDelta))
        put("source", JsonPrimitive("user"))
        put("createdAt", JsonPrimitive(nowIso))
        put("updatedAt", JsonPrimitive(nowIso))
    })

    return AppendCanonicalEvent(event = event, nowIso = nowIso)
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
