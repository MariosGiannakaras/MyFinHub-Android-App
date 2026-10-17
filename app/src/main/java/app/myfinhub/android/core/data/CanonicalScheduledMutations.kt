package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Web scheduled.ts: scheduled records do not affect the ledger until completed. */
data class CanonicalScheduledDraft(
    val kind: String,
    val dueDate: String,
    val amount: Double,
    val note: String = "",
    val category: String? = null,
    val subcategory: String? = null,
    val accountId: String? = null,
    val fromAccountId: String? = null,
    val toAccountId: String? = null,
)

data class CreateCanonicalScheduledTransaction(
    val scheduledId: String, val draft: CanonicalScheduledDraft, val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Προγραμματισμός κίνησης"

    /** Only the *same* durable intent may reuse an existing scheduled ID. */
    internal fun matchesExisting(row: JsonObject): Boolean {
        val expectedNote = draft.note.trim().ifEmpty { when (draft.kind) {
            "income" -> "Προγραμματισμένο έσοδο"
            "transfer" -> "Προγραμματισμένη μεταφορά"
            else -> "Προγραμματισμένη πληρωμή"
        } }
        val sameAmount = row.number("amount")?.let { stored ->
            runCatching { moneyToCents(stored) == moneyToCents(draft.amount) }.getOrDefault(false)
        } ?: false
        return row.string("id") == scheduledId &&
            row.string("createdAt") == nowIso &&
            row.string("kind") == draft.kind &&
            row.string("dueDate") == draft.dueDate &&
            sameAmount && row.string("note") == expectedNote &&
            row.string("category").orEmpty() == draft.category.orEmpty().trim() &&
            row.string("subcategory").orEmpty() == draft.subcategory.orEmpty().trim() &&
            (if (draft.kind == "transfer") {
                row.string("fromAccountId") == draft.fromAccountId &&
                    row.string("toAccountId") == draft.toAccountId &&
                    row.string("accountId") == null
            } else {
                row.string("accountId") == draft.accountId &&
                    row.string("fromAccountId") == null &&
                    row.string("toAccountId") == null
            })
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(scheduledId.isNotBlank()) { "Απαιτείται αναγνωριστικό προγραμματισμένης κίνησης." }
        val before = document.state.array("scheduled")
        val matches = before.filterIsInstance<JsonObject>().filter { it.string("id") == scheduledId }
        require(matches.size <= 1) { "Το ID ανήκει σε πολλές ιστορικές κινήσεις." }
        val existing = matches.singleOrNull()
        if (existing != null) {
            require(matchesExisting(existing)) {
                "Το αναγνωριστικό προγραμματισμένης κίνησης ανήκει σε διαφορετική καταχώριση."
            }
            return document
        }
        require(draft.kind in setOf("income","expense","transfer")) { "Άγνωστος τύπος προγραμματισμένης κίνησης." }
        require(runCatching { LocalDate.parse(draft.dueDate) }.isSuccess) { "Μη έγκυρη ημερομηνία." }
        val cents = moneyToCents(draft.amount)
        require(cents > 0L) { "Συμπλήρωσε θετικό ποσό." }
        val ids = document.canonicalAccounts().filter { it.kind != "credit" }.mapTo(mutableSetOf(), CanonicalAccount::id)
        if (draft.kind == "transfer") {
            require(draft.fromAccountId in ids && draft.toAccountId in ids &&
                draft.fromAccountId != draft.toAccountId) { "Επίλεξε δύο διαφορετικούς διαθέσιμους λογαριασμούς." }
        } else require(draft.accountId in ids) { "Ο λογαριασμός δεν είναι διαθέσιμος." }
        val note = draft.note.trim().ifEmpty { when (draft.kind) {
            "income" -> "Προγραμματισμένο έσοδο"
            "transfer" -> "Προγραμματισμένη μεταφορά"
            else -> "Προγραμματισμένη πληρωμή"
        } }
        val row = JsonObject(buildMap {
            put("id",JsonPrimitive(scheduledId))
            put("kind",JsonPrimitive(draft.kind))
            put("dueDate",JsonPrimitive(draft.dueDate))
            put("amount",JsonPrimitive(centsToMoney(cents)))
            put("note",JsonPrimitive(note))
            draft.category?.trim()?.takeIf(String::isNotEmpty)?.let { put("category",JsonPrimitive(it)) }
            draft.subcategory?.trim()?.takeIf(String::isNotEmpty)?.let { put("subcategory",JsonPrimitive(it)) }
            if(draft.kind=="transfer"){
                put("fromAccountId",JsonPrimitive(draft.fromAccountId!!))
                put("toAccountId",JsonPrimitive(draft.toAccountId!!))
            } else put("accountId",JsonPrimitive(draft.accountId!!))
            put("status",JsonPrimitive("pending"))
            put("createdAt",JsonPrimitive(nowIso))
            put("updatedAt",JsonPrimitive(nowIso))
        })
        return document.withMutableState(document.state.updated("scheduled",JsonArray(before+row)),nowIso)
    }
}

/** Atomic replay-safe scheduledToEvent + completed transition, using the common strict entry validator. */
data class CompleteCanonicalScheduledTransaction(
    val scheduledId: String, val eventId: String, val date: String, val nowIso: String,
    val amount: Double? = null, val accountId: String? = null,
    val fromAccountId: String? = null, val toAccountId: String? = null,
) : CanonicalFinanceMutation {
    override val description: String = "Ολοκλήρωση προγραμματισμένης κίνησης"
    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(scheduledId.isNotBlank() && eventId.isNotBlank()) { "Απαιτούνται αναγνωριστικά κίνησης." }
        val rows = document.state.array("scheduled")
        val indices=rows.indices.filter { (rows[it] as? JsonObject)?.string("id")==scheduledId }
        require(indices.size==1) { "Η κίνηση λείπει ή έχει αμφίσημο ID." }
        val index=indices.single()
        val row=rows[index] as JsonObject
        if (row.string("status")=="completed" && row.string("completedEventId")==eventId &&
            document.state.array("events").any { (it as? JsonObject)?.string("id")==eventId }) return document
        require(row.string("status")=="pending") { "Η κίνηση έχει ήδη κλείσει." }
        require(document.state.array("events").none { (it as? JsonObject)?.string("id")==eventId }) {
            "Το αναγνωριστικό κίνησης χρησιμοποιείται ήδη."
        }
        val kind=row.string("kind").orEmpty()
        require(kind in setOf("expense","income","transfer")) { "Άγνωστος τύπος κίνησης." }
        val draft=CanonicalTransactionEntryDraft(
            kind=kind,date=date,amount=amount ?: row.number("amount"),
            note=row.string("note").orEmpty(),category=row.string("category"),subcategory=row.string("subcategory"),
            accountId=if(kind=="transfer")null else accountId ?: row.string("accountId"),
            fromAccountId=if(kind=="transfer")fromAccountId ?: row.string("fromAccountId") else null,
            toAccountId=if(kind=="transfer")toAccountId ?: row.string("toAccountId") else null,
        )
        val appended=createCanonicalTransactionEntryMutation(document,draft,eventId,nowIso).apply(document)
        val result=appended.state.array("scheduled").toMutableList()
        result[index]=JsonObject(row+mapOf("status" to JsonPrimitive("completed"),
            "completedEventId" to JsonPrimitive(eventId),
            "completedAt" to JsonPrimitive(nowIso),"updatedAt" to JsonPrimitive(nowIso)))
        return appended.withMutableState(appended.state.updated("scheduled",JsonArray(result)),nowIso)
    }
}

/** Skip/cancel never append money and reject transitions from already closed records. */
data class CloseCanonicalScheduledTransaction(
    val scheduledId: String, val status: String, val nowIso: String,
): CanonicalFinanceMutation {
    override val description: String = "Κλείσιμο προγραμματισμένης κίνησης"
    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(status in setOf("skipped","cancelled")) { "Μη επιτρεπτή κατάσταση." }
        val rows=document.state.array("scheduled")
        val indices=rows.indices.filter { (rows[it] as? JsonObject)?.string("id")==scheduledId }
        require(indices.size==1) { "Η κίνηση λείπει ή έχει αμφίσημο ID." }
        val index=indices.single()
        val original=rows[index] as JsonObject
        if(original.string("status")==status) return document
        require(original.string("status")=="pending") { "Η κίνηση έχει ήδη κλείσει." }
        val changed=rows.toMutableList()
        changed[index]=JsonObject(original+mapOf(
            "status" to JsonPrimitive(status),
            (if(status=="skipped")"skippedAt" else "cancelledAt") to JsonPrimitive(nowIso),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
        return document.withMutableState(document.state.updated("scheduled",JsonArray(changed)),nowIso)
    }
}
fun CanonicalScheduledItem.canonicalLifecycle(asOf: String): String = when(status) {
    "completed","skipped","cancelled" -> status
    else -> if(dueDate<=asOf) "due" else "upcoming"
}
