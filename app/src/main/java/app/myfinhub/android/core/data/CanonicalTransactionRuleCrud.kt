package app.myfinhub.android.core.data

import java.text.Collator
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull

/** Web transactionRuleEditor.ts uses priority then localized ID for first-match order. */
private val canonicalRuleComparator = Comparator<JsonObject> { a, b ->
    val priority = (a.number("priority") ?: Double.POSITIVE_INFINITY)
        .compareTo(b.number("priority") ?: Double.POSITIVE_INFINITY)
    if (priority != 0) priority
    else Collator.getInstance(Locale.forLanguageTag("el-GR"))
        .compare(a.string("id").orEmpty(), b.string("id").orEmpty())
}

private fun sortedCanonicalRules(rows: List<JsonObject>): List<JsonObject> =
    rows.sortedWith(canonicalRuleComparator)

private fun JsonObject.rulePayloadWithoutOrdering(): Map<String, JsonElement> =
    filterKeys { it != "priority" && it != "updatedAt" }

/**
 * Create/edit a single Web TransactionRule. Mutation IDs, timestamps, and source snapshots
 * are durable. Existing rule identity, enabled state, and unrecognized Web fields survive editing.
 * Historical transactions are never recategorized.
 */
data class UpsertCanonicalTransactionRule(
    val rule: JsonObject,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Αποθήκευση κανόνα ταξινόμησης"

    private val ruleId get() = rule.string("id").orEmpty()
    private fun matches(document: CanonicalFinanceDocument) =
        document.state.array("transactionRules").filterIsInstance<JsonObject>()
            .filter { it.string("id") == ruleId }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val actual = matches(document).singleOrNull() ?: return false
        return ruleId.isNotBlank() &&
            actual.rulePayloadWithoutOrdering() == rule.rulePayloadWithoutOrdering()
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(ruleId.isNotBlank()) { "Ο κανόνας χρειάζεται αναγνωριστικό." }
        require(rule.string("name")?.trim()?.isNotEmpty() == true) { "Δώσε όνομα στον κανόνα." }
        val priority = rule.number("priority")
        require(priority != null && priority.isFinite() && priority >= 0.0 &&
            priority <= 1_000_000.0 && priority % 1.0 == 0.0) {
            "Μη έγκυρη προτεραιότητα κανόνα."
        }
        val match = rule.obj("match")
        require(listOf("description", "merchant", "accountId").any {
            !match.string(it).isNullOrBlank()
        }) { "Ο κανόνας χρειάζεται τουλάχιστον μία συνθήκη." }
        val action = rule.obj("action")
        require(listOf("category", "subcategory", "note").any {
            !action.string(it).isNullOrBlank()
        }) { "Ο κανόνας χρειάζεται τουλάχιστον μία ενέργεια." }
        val scopes = rule.array("scopes").mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }.distinct()
        require(scopes.isNotEmpty() && scopes.all {
            it == "manual" || it == "imported" || it == "review"
        }) { "Διάλεξε έγκυρο πεδίο εφαρμογής." }
        require(rule.bool("enabled") != null) { "Ο κανόνας δεν έχει κατάσταση." }

        val rows = document.state.array("transactionRules").toMutableList()
        val found = matches(document)
        require(found.size <= 1) { "Διπλό αναγνωριστικό κανόνα: απαιτείται έλεγχος." }
        val before = found.singleOrNull()
        if (satisfiedBy(document)) return document
        if (expected == null) {
            require(before == null) { "Ο κανόνας υπάρχει ήδη. Ανανέωσε τη λίστα." }
            require(rows.size < 10_000) { "Έχει συμπληρωθεί το όριο των 10.000 κανόνων." }
            val existing = rows.filterIsInstance<JsonObject>()
            val max = existing.maxOfOrNull { it.number("priority") ?: 0.0 } ?: 0.0
            if (priority > max && priority <= 1_000_000.0) {
                rows += rule
            } else {
                // Web appendTransactionRule compacts ONLY when sparse priorities have no room.
                val ordered = sortedCanonicalRules(existing) + rule
                val compacted = ordered.mapIndexed { index, item ->
                    val p = (index + 1) * 100
                    if (item.number("priority") == p.toDouble()) item
                    else JsonObject(item + mapOf(
                        "priority" to JsonPrimitive(p),
                        "updatedAt" to JsonPrimitive(nowIso),
                    ))
                }
                rows.clear()
                rows.addAll(compacted)
            }
        } else {
            require(before == expected) {
                "Ο κανόνας τροποποιήθηκε σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
            }
            require(before != null) { "Ο κανόνας διαγράφηκε σε άλλη συσκευή." }
            val index = rows.indexOfFirst { (it as? JsonObject)?.string("id") == ruleId }
            require(index >= 0)
            // Never let editing a description silently change the first-match precedence.
            require(before.number("priority") == priority) {
                "Η σειρά του κανόνα άλλαξε. Ανανέωσε τη λίστα."
            }
            rows[index] = JsonObject(before + rule)
        }
        return document.withMutableState(
            document.state.updated("transactionRules", JsonArray(rows)), nowIso,
        )
    }
}

/** Web Settings delete: remove a rule, not historical events or their categories. */
data class DeleteCanonicalTransactionRule(
    val ruleId: String,
    val expected: JsonObject,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Διαγραφή κανόνα ταξινόμησης"

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean =
        ruleId.isNotBlank() && document.state.array("transactionRules").none {
            (it as? JsonObject)?.string("id") == ruleId
        }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(ruleId.isNotBlank()) { "Δεν επιλέχθηκε κανόνας." }
        val rows = document.state.array("transactionRules")
        val found = rows.filterIsInstance<JsonObject>().filter { it.string("id") == ruleId }
        require(found.size <= 1) { "Ο κανόνας έχει διπλό αναγνωριστικό." }
        val row = found.singleOrNull() ?: return document
        require(row == expected) {
            "Ο κανόνας άλλαξε σε άλλη συσκευή. Έλεγξε τη νέα έκδοση."
        }
        return document.withMutableState(
            document.state.updated("transactionRules",
                JsonArray(rows.filterNot { (it as? JsonObject)?.string("id") == ruleId })),
            nowIso,
        )
    }
}

/**
 * Web reorderTransactionRules: preserve sparse integer priorities where possible;
 * otherwise compact to 100,200,... in a single atomic revision. The complete source
 * snapshot guards against accidentally changing a remote first-match priority order.
 */
data class ReorderCanonicalTransactionRule(
    val ruleId: String,
    val direction: Int,
    val expected: JsonArray,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description = "Αλλαγή σειράς κανόνα"

    private fun ordered(rows: List<JsonObject>): List<JsonObject> =
        sortedCanonicalRules(rows)

    private fun desiredIdOrder(): List<String> {
        val ids = ordered(expected.filterIsInstance<JsonObject>())
            .map { it.string("id").orEmpty() }.toMutableList()
        val index = ids.indexOf(ruleId)
        val target = index + direction
        if (index >= 0 && target in ids.indices) {
            val moved = ids.removeAt(index)
            ids.add(target, moved)
        }
        return ids
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val source = expected.filterIsInstance<JsonObject>()
        val actual = document.state.array("transactionRules").filterIsInstance<JsonObject>()
        if (source.size != actual.size || ruleId.isBlank() ||
            source.map { it.string("id") }.distinct().size != source.size ||
            actual.map { it.string("id") }.distinct().size != actual.size) return false
        val sourcesById = source.associateBy { it.string("id") }
        // An unrelated remote rule edit must never be mistaken for our successful reorder.
        val identicalContent = actual.all { row ->
            val before = sourcesById[row.string("id")] ?: return@all false
            row.rulePayloadWithoutOrdering() == before.rulePayloadWithoutOrdering()
        }
        return identicalContent &&
            actual.map { it.string("id") }.toSet() == source.map { it.string("id") }.toSet() &&
            ordered(actual).map { it.string("id") } == desiredIdOrder()
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        require(ruleId.isNotBlank() && direction in setOf(-1, 1)) {
            "Μη έγκυρη αλλαγή σειράς κανόνων."
        }
        val current = document.state.array("transactionRules")
        if (satisfiedBy(document)) return document
        require(JsonArray(current) == expected) {
            "Η σειρά κανόνων άλλαξε σε άλλη συσκευή. Ανανέωσε τη λίστα."
        }
        val rules = ordered(current.filterIsInstance<JsonObject>()).toMutableList()
        require(rules.map { it.string("id") }.distinct().size == rules.size) {
            "Διπλά αναγνωριστικά κανόνων: απαιτείται έλεγχος."
        }
        val index = rules.indexOfFirst { it.string("id") == ruleId }
        val target = index + direction
        if (index < 0 || target !in rules.indices) return document
        val moving = rules.removeAt(index)
        rules.add(target, moving)
        val lower = rules.getOrNull(target - 1)?.number("priority")
        val upper = rules.getOrNull(target + 1)?.number("priority")
        val priority = when {
            lower == null && upper == null -> 100.0
            lower == null -> upper!! - 100.0
            upper == null -> lower + 100.0
            else -> kotlin.math.floor((lower + upper) / 2.0)
        }
        val sparse = priority.isFinite() && priority >= 0.0 &&
            priority <= 1_000_000.0 && priority % 1.0 == 0.0 &&
            (lower == null || priority > lower) &&
            (upper == null || priority < upper)
        val replacements = if (sparse) {
            mapOf(ruleId to JsonObject(moving + mapOf(
                "priority" to JsonPrimitive(priority.toInt()),
                "updatedAt" to JsonPrimitive(nowIso),
            )))
        } else {
            rules.mapIndexed { i, row ->
                val p = (i + 1) * 100
                row.string("id").orEmpty() to if (row.number("priority") == p.toDouble())
                    row else JsonObject(row + mapOf(
                        "priority" to JsonPrimitive(p),
                        "updatedAt" to JsonPrimitive(nowIso),
                    ))
            }.toMap()
        }
        val updated = current.map { raw ->
            val id = (raw as? JsonObject)?.string("id")
            replacements[id] ?: raw
        }
        return document.withMutableState(
            document.state.updated("transactionRules", JsonArray(updated)), nowIso,
        )
    }
}
