package app.myfinhub.android.core.data

import kotlinx.serialization.json.JsonObject

/**
 * Web transactionRules.ts read-only priority order and enabled/scope overview.
 * Names and match criteria may contain merchant details; never persist or expose in logs.
 */
data class CanonicalTransactionRuleSummary(
    val id: String,
    val name: String,
    val priority: Double,
    val enabled: Boolean,
    val scopes: List<String>,
) {
    override fun toString(): String = "CanonicalTransactionRuleSummary(<redacted>)"
}

fun CanonicalFinanceDocument.transactionRuleSummaries(): List<CanonicalTransactionRuleSummary> =
    state.array("transactionRules")
        .mapNotNull { raw ->
            val rule = raw as? JsonObject ?: return@mapNotNull null
            val id = rule.string("id")?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            CanonicalTransactionRuleSummary(
                id=id,
                name=rule.string("name")?.trim().orEmpty().ifBlank { "Κανόνας κατηγοριοποίησης" },
                priority=rule.number("priority")?.takeIf(Double::isFinite)
                    ?: Double.POSITIVE_INFINITY,
                enabled=rule.bool("enabled")==true,
                scopes=rule.array("scopes").mapNotNull {
                    (it as? kotlinx.serialization.json.JsonPrimitive)?.content
                }.filter { it in setOf("manual","imported","review") }.distinct(),
            )
        }
        .sortedWith(compareBy<CanonicalTransactionRuleSummary> { it.priority }.thenBy { it.id })
