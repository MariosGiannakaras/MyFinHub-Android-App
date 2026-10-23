package app.myfinhub.android.core.data

import java.text.Normalizer
import java.util.Locale
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Web counterparties.ts: directory identity, not auto-inferred from past notes or OCR. */
data class CanonicalCounterpartyRow(
    val id: String,
    val kind: String,
    val displayName: String,
    val aliases: List<String>,
    val createdAt: String,
)

fun CanonicalFinanceDocument.canonicalCounterpartyDirectory(): List<CanonicalCounterpartyRow> =
    state.array("counterparties").mapNotNull { raw ->
        val row = raw as? JsonObject ?: return@mapNotNull null
        val id = row.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
        val kind = row.string("kind")?.takeIf { it == "merchant" || it == "beneficiary" }
            ?: return@mapNotNull null
        CanonicalCounterpartyRow(
            id = id, kind = kind,
            displayName = row.string("displayName").orEmpty(),
            aliases = row.array("aliases").mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull
            },
            createdAt = row.string("createdAt").orEmpty(),
        )
    }

/** Mirrors Web counterpartyNameKey Unicode accent and punctuation folding. */
fun canonicalCounterpartyNameKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .lowercase(Locale.forLanguageTag("el-GR"))
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

/**
 * Web findCounterpartySuggestion: match exact accent-folded name or alias, never fuzzy
 * and never write an identity into a new event without user selection.
 * Ambiguity (including duplicate source rows) deliberately returns no suggestion.
 */
fun canonicalCounterpartySuggestion(
    records: List<CanonicalCounterpartyRow>,
    label: String,
    kind: String,
): CanonicalCounterpartyRow? {
    if (kind !in setOf("merchant", "beneficiary")) return null
    val key = canonicalCounterpartyNameKey(label)
    if (key.length < 2) return null
    return records.filter { row ->
        row.kind == kind &&
            (listOf(row.displayName) + row.aliases).any {
                canonicalCounterpartyNameKey(it) == key
            }
    }.singleOrNull()
}

/** Web withCounterparty, guarded by expected canonical row for offline revision replay. */
data class UpsertCanonicalCounterparty(
    val counterpartyId: String,
    val kind: String,
    val displayName: String,
    val aliases: List<String>,
    val createdAt: String,
    val expected: JsonObject?,
    val nowIso: String,
) : CanonicalFinanceMutation {
    override val description: String = "Αποθήκευση εμπόρου ή δικαιούχου"

    private fun normalizedAliases() =
        aliases.map(String::trim).filter(String::isNotEmpty).distinct()

    private fun desiredRow(): JsonObject {
        val name = displayName.trim()
        val aliasNames = normalizedAliases()
        require(Regex("^[A-Za-z0-9][A-Za-z0-9._:-]{0,159}$").matches(counterpartyId)) {
            "Μη έγκυρο αναγνωριστικό ταυτότητας."
        }
        require(kind == "merchant" || kind == "beneficiary") {
            "Μη έγκυρος τύπος αντισυμβαλλομένου."
        }
        require(name.length in 2..120) { "Το όνομα πρέπει να έχει 2–120 χαρακτήρες." }
        require(aliasNames.size <= 16 && aliasNames.all { it.length <= 120 }) {
            "Επιτρέπονται έως 16 εναλλακτικές ονομασίες 120 χαρακτήρων."
        }
        val keys = (listOf(name) + aliasNames).map(::canonicalCounterpartyNameKey)
        require(keys.all { it.length >= 2 } && keys.distinct().size == keys.size) {
            "Οι ονομασίες πρέπει να είναι μοναδικές και κατανοητές."
        }
        require(createdAt.isNotBlank() && nowIso.isNotBlank()) {
            "Απαιτούνται έγκυρες ημερομηνίες εγγραφής."
        }
        val base = expected ?: JsonObject(emptyMap())
        return JsonObject(base + mapOf(
            "id" to JsonPrimitive(counterpartyId),
            "kind" to JsonPrimitive(kind),
            "displayName" to JsonPrimitive(name),
            "aliases" to JsonArray(aliasNames.map(::JsonPrimitive)),
            "createdAt" to JsonPrimitive(createdAt),
            "updatedAt" to JsonPrimitive(nowIso),
        ))
    }

    fun satisfiedBy(document: CanonicalFinanceDocument): Boolean {
        val existing = document.state.array("counterparties").filterIsInstance<JsonObject>()
            .filter { it.string("id") == counterpartyId }.singleOrNull() ?: return false
        return runCatching { existing == desiredRow() }.getOrDefault(false)
    }

    override fun apply(document: CanonicalFinanceDocument): CanonicalFinanceDocument {
        val desired = desiredRow()
        val rows = document.state.array("counterparties")
        val parsed = rows.filterIsInstance<JsonObject>()
        require(parsed.size == rows.size) { "Το αρχείο αντισυμβαλλομένων έχει μη έγκυρα στοιχεία." }
        val matches = parsed.filter { it.string("id") == counterpartyId }
        require(matches.size <= 1) { "Διπλό αναγνωριστικό αντισυμβαλλομένου." }
        val current = matches.singleOrNull()
        if (satisfiedBy(document)) return document
        if (expected == null) {
            require(current == null) { "Η ταυτότητα υπάρχει ήδη. Ανανέωσε τα στοιχεία." }
            require(parsed.size < 2000) { "Ξεπεράστηκε το όριο των 2000 ταυτοτήτων." }
        } else {
            require(expected.string("id") == counterpartyId &&
                expected.string("kind") == kind &&
                expected.string("createdAt") == createdAt) {
                "Ο αρχικός τύπος και η ταυτότητα δεν αλλάζουν."
            }
            require(current == expected) {
                "Η ταυτότητα άλλαξε στον server. Έλεγξε τη νέα έκδοση."
            }
        }
        val requestedKeys = (listOf(displayName.trim()) + normalizedAliases())
            .map(::canonicalCounterpartyNameKey).toSet()
        require(parsed.none { row ->
            row.string("id") != counterpartyId && row.string("kind") == kind &&
                (listOf(row.string("displayName").orEmpty()) +
                    row.array("aliases").mapNotNull {
                        (it as? JsonPrimitive)?.contentOrNull
                    }).any { canonicalCounterpartyNameKey(it) in requestedKeys }
        }) { "Υπάρχει ήδη όνομα ή ψευδώνυμο αυτού του τύπου." }
        val result = if (current == null) rows + desired else rows.map { old ->
            if ((old as? JsonObject)?.string("id") == counterpartyId) desired else old
        }
        return document.withMutableState(
            document.state.updated("counterparties", JsonArray(result)), nowIso,
        )
    }
}
