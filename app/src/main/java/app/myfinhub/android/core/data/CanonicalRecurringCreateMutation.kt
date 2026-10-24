package app.myfinhub.android.core.data

import java.time.LocalDate
import kotlin.math.abs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Web recurringDraftError / recurringVariableAmountError: no event is posted by a planned bill. */
data class CanonicalRecurringCreateDraft(
    val name: String,
    val amount: Double,
    val accountId: String,
    val category: String,
    val amountMode: String = "fixed",
    val estimateMin: Double? = null,
    val estimateMax: Double? = null,
    val recurrenceUnit: String = "month",
    val recurrenceInterval: Int = 1,
    val day: Int? = null,
    val firstExpectedDate: String? = null,
    val endDate: String? = null,
    /** Web recurring editor category-path leaf; optional for historical/uncategorized rows. */
    val subcategory: String? = null,
)

/** Stable IDs and full draft survive offline queue serialization and server revision replay. */
data class CreateCanonicalRecurring(
    val recurringId: String,
    val nowIso: String,
    val draft: CanonicalRecurringCreateDraft,
) : CanonicalFinanceMutation {
    override val description: String = "Προσθήκη παγίου"

    private fun cents(value: Double): Long {
        val result = moneyToCents(value)
        require(result > 0 && abs(value * 100.0 - result.toDouble()) < 0.000001) {
            "Το ποσό πρέπει να είναι θετικό με δύο δεκαδικά."
        }
        return result
    }

    fun newRow(): JsonObject {
        require(recurringId.isNotBlank()) { "Απαιτείται αναγνωριστικό παγίου." }
        val name = draft.name.trim()
        require(name.isNotEmpty() && name.length <= 500) { "Συμπλήρωσε όνομα παγίου." }
        val amountCents = cents(draft.amount)
        require(draft.amountMode in setOf("fixed", "variable")) { "Άγνωστος τύπος ποσού." }
        if (draft.amountMode == "fixed") {
            require(draft.estimateMin == null && draft.estimateMax == null) {
                "Μόνο τα μεταβλητά πάγια δέχονται εύρος εκτίμησης."
            }
        } else {
            require((draft.estimateMin == null) == (draft.estimateMax == null)) {
                "Συμπλήρωσε και τα δύο άκρα της εκτίμησης ή κανένα."
            }
            if (draft.estimateMin != null && draft.estimateMax != null) {
                require(cents(draft.estimateMin) <= amountCents &&
                    amountCents <= cents(draft.estimateMax)) {
                    "Η εκτίμηση πρέπει να περιλαμβάνεται στο εύρος."
                }
            }
        }
        require(draft.recurrenceUnit in setOf("month", "year")) { "Μη έγκυρη μονάδα επανάληψης." }
        require(draft.recurrenceInterval in 1..120) { "Η συχνότητα πρέπει να είναι από 1 έως 120." }
        require(draft.day == null || draft.day in 1..31) { "Η ημέρα πρέπει να είναι 1–31." }
        val anchor = draft.firstExpectedDate?.takeIf(String::isNotBlank)
        val end = draft.endDate?.takeIf(String::isNotBlank)
        for (date in listOfNotNull(anchor, end)) {
            require(runCatching { LocalDate.parse(date).toString() == date }.getOrDefault(false)) {
                "Μη έγκυρη ημερομηνία παγίου."
            }
        }
        require(anchor != null || (draft.recurrenceUnit == "month" && draft.recurrenceInterval == 1)) {
            "Χρειάζεται ημερομηνία αναφοράς για μη μηνιαίο πάγιο."
        }
        require(draft.accountId.isNotBlank()) { "Επίλεξε λογαριασμό." }
        return JsonObject(buildMap {
            put("id", JsonPrimitive(recurringId))
            put("name", JsonPrimitive(name))
            put("amount", JsonPrimitive(centsToMoney(amountCents)))
            put("amountMode", JsonPrimitive(draft.amountMode))
            if (draft.amountMode == "variable") {
                draft.estimateMin?.let { put("estimateMin", JsonPrimitive(centsToMoney(cents(it)))) }
                draft.estimateMax?.let { put("estimateMax", JsonPrimitive(centsToMoney(cents(it)))) }
            }
            put("accountId", JsonPrimitive(draft.accountId))
            put("category", JsonPrimitive(draft.category.trim()))
            draft.subcategory?.trim()?.takeIf(String::isNotEmpty)?.let {
                put("subcategory", JsonPrimitive(it))
            }
            put("active", JsonPrimitive(true))
            put("status", JsonPrimitive("active"))
            put("recurrenceUnit", JsonPrimitive(draft.recurrenceUnit))
            put("recurrenceInterval", JsonPrimitive(draft.recurrenceInterval))
            draft.day?.let { put("day", JsonPrimitive(it)) }
            anchor?.let { put("firstExpectedDate", JsonPrimitive(it)) }
            end?.let { put("endDate", JsonPrimitive(it)) }
            put("source", JsonPrimitive("user"))
        })
    }

    fun matchesExisting(record: JsonObject): Boolean = record == newRow()

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val created = newRow()
        // Web allRecurringItems includes BOTH seed and state; never shadow or edit a seeded ID.
        val identities = document.allCanonicalRecurringItems().filter { it.id == recurringId }
        require(identities.size <= 1) { "Διπλό ιστορικό αναγνωριστικό παγίου." }
        val custom = document.state.array("recurringCustom")
        val customMatches = custom.filterIsInstance<JsonObject>()
            .filter { it.string("id") == recurringId }
        if (identities.isNotEmpty()) {
            require(customMatches.size == 1 && matchesExisting(customMatches.single())) {
                "Το ID του παγίου έχει ήδη χρησιμοποιηθεί."
            }
            return document
        }
        val eligible = document.canonicalAccounts().any { it.id == draft.accountId && it.kind != "credit" }
        require(eligible) { "Ο λογαριασμός δεν είναι διαθέσιμος για πάγια." }
        return document.withMutableState(
            document.state.updated("recurringCustom", JsonArray(custom + created)), nowIso,
        )
    }
}
