package app.myfinhub.android.core.data

import kotlin.math.max
import kotlin.math.min
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull

/** Read-only, lossless projections over the shared MyFinHub FinanceData JSON contract. */
data class CanonicalAccount(
    val id: String,
    val name: String,
    val shortName: String?,
    val kind: String,
    val excludeFromAvailable: Boolean,
)

data class CanonicalLegacyTransaction(
    val id: String,
    val date: String,
    val type: String,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val amount: Double,
    val note: String,
    val category: String?,
    val subcategory: String? = null,
)

data class CanonicalLedgerLeg(val accountId: String, val amount: Double)

data class CanonicalSplitPart(
    val id: String,
    val label: String,
    val category: String,
    val subcategory: String?,
    val amount: Double,
    val kind: String? = null,
)

data class CanonicalEvent(
    val id: String,
    val date: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val subcategory: String? = null,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val cardId: String?,
    val loanId: String?,
    val legs: List<CanonicalLedgerLeg>,
    val parts: List<CanonicalSplitPart>,
    val savingAmount: Double,
    val receivableDelta: Double,
    val creditDelta: Double,
)

data class CanonicalScheduledItem(
    val id: String,
    val dueDate: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val status: String,
)

data class CanonicalCard(
    val id: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val last4: String?,
    val creditLimit: Double?,
    val active: Boolean,
    val vaultRef: String?,
)

data class CanonicalMonthlyFlow(
    val income: Double,
    val expense: Double,
    val saving: Double,
    val refunds: Double,
) {
    val net: Double get() = income - expense
}

data class CanonicalBudget(
    val id: String?,
    val month: String,
    val amount: Double,
    val alertThreshold: Int?,
)

private data class FlowImpact(
    val income: Double = 0.0,
    val expense: Double = 0.0,
    val saving: Double = 0.0,
    val refund: Double = 0.0,
)

fun CanonicalFinanceDocument.canonicalAccounts(): List<CanonicalAccount> {
    val settings = state.obj("settings")
    val renamed = settings.obj("accountNames")
    val excluded = settings.array("excludedFromAvailable")
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .toSet()
    return seed.array("accounts").mapNotNull accounts@{ element ->
        val raw = element as? JsonObject ?: return@accounts null
        val id = raw.string("id") ?: return@accounts null
        CanonicalAccount(
            id = id,
            name = renamed.string(id) ?: raw.string("name") ?: id,
            shortName = raw.string("short"),
            kind = raw.string("kind").orEmpty(),
            excludeFromAvailable = raw.bool("excludeFromAvailable") == true || id in excluded,
        )
    }
}

/** Mirrors web effectiveLegacyTransactions: seed minus deleted, then overrides, then custom. */
fun CanonicalFinanceDocument.effectiveLegacyTransactions(): List<CanonicalLegacyTransaction> {
    val seedTransactions = seed.array("transactions").mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)
    val deleted = deletedTransactionIds()
    val overrides = state.obj("overrides")
    val base = seedTransactions.values.mapNotNull effective@{ raw ->
        val id = raw.string("id") ?: return@effective null
        if (id in deleted) return@effective null
        (overrides[id] as? JsonObject ?: raw).toLegacyTransaction()
    }
    val custom = state.array("customTransactions").mapNotNull { (it as? JsonObject)?.toLegacyTransaction() }
    return (base + custom).sortedBy(CanonicalLegacyTransaction::date)
}

fun CanonicalFinanceDocument.canonicalEvents(): List<CanonicalEvent> =
    state.array("events").mapNotNull events@{ element ->
        val event = element as? JsonObject ?: return@events null
        CanonicalEvent(
            id = event.string("id") ?: return@events null,
            date = event.string("date").orEmpty(),
            kind = event.string("kind").orEmpty(),
            amount = event.number("amount") ?: 0.0,
            note = event.string("note").orEmpty(),
            category = event.string("category"),
            subcategory = event.string("subcategory"),
            accountId = event.string("accountId"),
            fromAccountId = event.string("fromAccountId"),
            toAccountId = event.string("toAccountId"),
            cardId = event.string("cardId"),
            loanId = event.string("loanId"),
            legs = event.array("legs").mapNotNull legs@{ rawLeg ->
                val leg = rawLeg as? JsonObject ?: return@legs null
                CanonicalLedgerLeg(
                    accountId = leg.string("accountId") ?: return@legs null,
                    amount = leg.number("amount") ?: return@legs null,
                )
            },
            parts = event.array("parts").mapNotNull parts@{ rawPart -> rawPart.toSplitPart() },
            savingAmount = event.number("savingAmount") ?: 0.0,
            receivableDelta = event.number("receivableDelta") ?: 0.0,
            creditDelta = event.number("creditDelta") ?: 0.0,
        )
    }

fun CanonicalFinanceDocument.canonicalScheduled(): List<CanonicalScheduledItem> =
    state.array("scheduled").mapNotNull scheduled@{ element ->
        val item = element as? JsonObject ?: return@scheduled null
        CanonicalScheduledItem(
            id = item.string("id") ?: return@scheduled null,
            dueDate = item.string("dueDate").orEmpty(),
            kind = item.string("kind").orEmpty(),
            amount = item.number("amount") ?: 0.0,
            note = item.string("note").orEmpty(),
            category = item.string("category"),
            accountId = item.string("accountId"),
            fromAccountId = item.string("fromAccountId"),
            toAccountId = item.string("toAccountId"),
            status = item.string("status").orEmpty(),
        )
    }

fun CanonicalFinanceDocument.canonicalCards(): List<CanonicalCard> =
    state.array("cards").mapNotNull cards@{ element ->
        val card = element as? JsonObject ?: return@cards null
        CanonicalCard(
            id = card.string("id") ?: return@cards null,
            nickname = card.string("nickname").orEmpty(),
            kind = card.string("kind").orEmpty(),
            network = card.string("network").orEmpty(),
            last4 = card.string("last4"),
            creditLimit = card.number("creditLimit"),
            active = card.bool("active") ?: false,
            vaultRef = card.string("vaultRef"),
        )
    }

/** Mirrors web accountBalances: latest snapshot + mutable legacy deltas + event legs. */
fun CanonicalFinanceDocument.accountBalances(asOf: String): Map<String, Double> {
    val snapshot = seed.array("snapshots").mapNotNull { it as? JsonObject }
        .filter { it.string("date").orEmpty() <= asOf }
        .maxByOrNull { it.string("date").orEmpty() }
    val balances = snapshot?.obj("balances")?.mapValues { (_, value) ->
        (value as? JsonPrimitive)?.doubleOrNull ?: 0.0
    }?.toMutableMap() ?: canonicalAccounts().associate { it.id to 0.0 }.toMutableMap()
    balances.putIfAbsent(CREDIT_ACCOUNT_ID, 0.0)

    val seedTransactions = seed.array("transactions").mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)

    fun applyLegacy(raw: JsonObject, sign: Double) {
        val tx = raw.toLegacyTransaction() ?: return
        if (tx.date > asOf) return
        when (tx.type) {
            "income" -> tx.accountId?.let { balances[it] = (balances[it] ?: 0.0) + sign * tx.amount }
            "expense" -> tx.accountId?.let { balances[it] = (balances[it] ?: 0.0) - sign * tx.amount }
            "adjustment" -> tx.accountId?.let { balances[it] = (balances[it] ?: 0.0) + sign * tx.amount }
            "transfer" -> if (tx.fromAccountId != null && tx.toAccountId != null) {
                balances[tx.fromAccountId] = (balances[tx.fromAccountId] ?: 0.0) - sign * tx.amount
                balances[tx.toAccountId] = (balances[tx.toAccountId] ?: 0.0) + sign * tx.amount
            }
        }
    }

    deletedTransactionIds().forEach { id -> seedTransactions[id]?.let { applyLegacy(it, -1.0) } }
    state.obj("overrides").forEach { (id, value) ->
        seedTransactions[id]?.let { applyLegacy(it, -1.0) }
        (value as? JsonObject)?.let { applyLegacy(it, 1.0) }
    }
    state.array("customTransactions").forEach { (it as? JsonObject)?.let { raw -> applyLegacy(raw, 1.0) } }
    canonicalEvents().filter { it.date <= asOf }.forEach { event ->
        event.legs.forEach { leg -> balances[leg.accountId] = (balances[leg.accountId] ?: 0.0) + leg.amount }
    }
    return balances
}

fun CanonicalFinanceDocument.availableMoney(asOf: String): Double {
    val balances = accountBalances(asOf)
    return canonicalAccounts().filter { it.kind != "credit" && !it.excludeFromAvailable }
        .sumOf { balances[it.id] ?: 0.0 }
}

fun CanonicalFinanceDocument.monthlyFlow(month: String): CanonicalMonthlyFlow {
    val start = "$month-01"
    val end = "$month-31"
    var aggregate = FlowImpact()
    effectiveLegacyTransactions().filter { it.date in start..end }.forEach { tx ->
        aggregate += legacyFlowImpact(tx)
    }
    canonicalEvents().filter { it.date in start..end }.forEach { event ->
        aggregate += eventFlowImpact(event)
    }
    return CanonicalMonthlyFlow(
        income = aggregate.income,
        expense = max(0.0, aggregate.expense),
        saving = aggregate.saving,
        refunds = aggregate.refund,
    )
}

fun CanonicalFinanceDocument.categoryTotals(month: String): Map<String, Double> {
    val start = "$month-01"
    val end = "$month-31"
    val totals = linkedMapOf<String, Double>()
    fun add(category: String?, amount: Double) {
        val key = category?.takeIf(String::isNotBlank) ?: "Άλλο"
        totals[key] = (totals[key] ?: 0.0) + amount
    }

    effectiveLegacyTransactions().filter { it.date in start..end }.forEach { tx ->
        val decision = reviewDecision(tx.id)
        if (
            decision?.string("status") == "confirmed" &&
            decision.string("semanticKind") == "split" &&
            decision.array("parts").isNotEmpty()
        ) {
            decision.array("parts").forEach { rawPart ->
                val part = rawPart.toSplitPart() ?: return@forEach
                when (part.kind ?: "expense") {
                    "expense" -> add(part.category, part.amount)
                    "refund" -> add(part.category, -part.amount)
                }
            }
        } else {
            val impact = legacyFlowImpact(tx)
            if (impact.expense != 0.0) add(decision?.string("category") ?: tx.category, impact.expense)
        }
    }
    canonicalEvents().filter { it.date in start..end }.forEach { event ->
        if (event.kind == "split") {
            event.parts.forEach { part -> add(part.category, part.amount) }
        } else {
            val impact = eventFlowImpact(event)
            if (impact.expense != 0.0) add(event.category, impact.expense)
        }
    }
    return totals.mapValues { (_, amount) -> max(0.0, amount) }.filterValues { it > 0.005 }
}

fun CanonicalFinanceDocument.overallBudget(month: String): CanonicalBudget? {
    val specific = state.array("budgets").mapNotNull { it as? JsonObject }.firstOrNull {
        it.string("month") == month && it.string("scope") == "overall"
    }
    if (specific != null) {
        return CanonicalBudget(
            id = specific.string("id"),
            month = month,
            amount = specific.number("amount") ?: return null,
            alertThreshold = specific.number("alertThreshold")?.toInt(),
        )
    }
    val settingsAmount = state.obj("settings").number("monthlyBudget") ?: return null
    return CanonicalBudget(null, month, settingsAmount, null)
}

/** Sum of web-equivalent outstanding values across effective seed/custom loans. */
fun CanonicalFinanceDocument.loanOutstanding(): Double {
    val loans = seed.array("loans").mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)
        .toMutableMap()
    state.obj("loanOverrides").forEach { (id, value) -> (value as? JsonObject)?.let { loans[id] = it } }
    state.array("customLoans").mapNotNull { it as? JsonObject }.forEach { loan ->
        loan.string("id")?.let { loans[it] = loan }
    }
    val extraCounts = state.obj("loanExtra")
    val events = canonicalEvents()
    return loans.values.sumOf { loan ->
        val id = loan.string("id").orEmpty()
        val total = loan.number("total") ?: 0.0
        val installment = loan.number("installment") ?: 0.0
        val installments = loan.number("installments") ?: 0.0
        val baselinePaidCount = loan.number("paidCount") ?: 0.0
        val legacyExtraCount = extraCounts.number(id) ?: 0.0
        val baselineAmount = min(installments, baselinePaidCount + legacyExtraCount) * installment
        val linkedAmount = events.filter { event ->
            event.loanId == id && (!loan.isSelfLoan() || event.isSelfLoanReturn())
        }.sumOf { max(0.0, it.amount) }
        max(0.0, total - baselineAmount - linkedAmount - (loan.number("forgivenAmount") ?: 0.0))
    }
}

/** Mirrors web net-worth receivables: legacy seed outstanding + canonical event deltas. */
fun CanonicalFinanceDocument.receivableOutstanding(): Double {
    val legacy = seed.array("lending").mapNotNull { it as? JsonObject }.sumOf { it.number("outstanding") ?: 0.0 }
    return max(0.0, legacy + canonicalEvents().sumOf { it.receivableDelta })
}

fun CanonicalFinanceDocument.cardOutstanding(cardId: String, asOf: String): Double = max(
    0.0,
    -canonicalEvents().filter { it.cardId == cardId && it.date <= asOf }.sumOf { it.creditDelta },
)

fun CanonicalFinanceDocument.settingsObject(): JsonObject = state.obj("settings")

private fun CanonicalFinanceDocument.legacyFlowImpact(tx: CanonicalLegacyTransaction): FlowImpact {
    val decision = reviewDecision(tx.id)
    if (decision?.string("status") == "confirmed") {
        when (decision.string("semanticKind")) {
            "saving_cash_offset" -> return FlowImpact(saving = tx.amount)
            "withdrawal", "transfer", "card_payment", "reconciliation" -> return FlowImpact()
            "refund" -> return FlowImpact(expense = -tx.amount, refund = tx.amount)
            "split" -> return decision.array("parts").fold(FlowImpact()) { acc, rawPart ->
                val part = rawPart.toSplitPart() ?: return@fold acc
                when (part.kind ?: "expense") {
                    "income" -> acc + FlowImpact(income = part.amount)
                    "refund" -> acc + FlowImpact(expense = -part.amount, refund = part.amount)
                    "saving" -> acc + FlowImpact(saving = part.amount)
                    "expense" -> acc + FlowImpact(expense = part.amount)
                    else -> acc
                }
            }
        }
    }
    return when (tx.type) {
        "income" -> FlowImpact(income = tx.amount)
        "expense" -> FlowImpact(expense = tx.amount)
        else -> FlowImpact()
    }
}

private fun eventFlowImpact(event: CanonicalEvent): FlowImpact = when (event.kind) {
    "income" -> FlowImpact(income = event.amount)
    "expense", "card_purchase" -> FlowImpact(expense = event.amount)
    "split" -> FlowImpact(expense = event.parts.sumOf { it.amount })
    "saving_cash_offset" -> FlowImpact(saving = if (event.savingAmount != 0.0) event.savingAmount else event.amount)
    "refund" -> FlowImpact(expense = -event.amount, refund = event.amount)
    else -> FlowImpact()
}

private fun CanonicalFinanceDocument.reviewDecision(transactionId: String): JsonObject? =
    state.obj("reviewDecisions")[transactionId] as? JsonObject

private fun CanonicalFinanceDocument.deletedTransactionIds(): Set<String> = when (val deleted = state["deleted"]) {
    is JsonArray -> deleted.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    is JsonObject -> deleted.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys
    else -> emptySet()
}

private fun JsonElement.toSplitPart(): CanonicalSplitPart? {
    val part = this as? JsonObject ?: return null
    return CanonicalSplitPart(
        id = part.string("id") ?: return null,
        label = part.string("label").orEmpty(),
        category = part.string("category") ?: "Άλλο",
        subcategory = part.string("subcategory"),
        amount = part.number("amount") ?: return null,
        kind = part.string("kind"),
    )
}

private fun JsonObject.toLegacyTransaction(): CanonicalLegacyTransaction? = CanonicalLegacyTransaction(
    id = string("id") ?: return null,
    date = string("date").orEmpty(),
    type = string("type").orEmpty(),
    accountId = string("accountId"),
    fromAccountId = string("fromAccountId"),
    toAccountId = string("toAccountId"),
    amount = number("amount") ?: 0.0,
    note = string("note").orEmpty(),
    category = string("category"),
    subcategory = string("subcategory"),
)

private fun JsonObject.isSelfLoan(): Boolean {
    if (string("kind") == "self-loan" || string("source") == "self-loan") return true
    val text = "${string("name").orEmpty()} ${string("provider").orEmpty()}"
    return Regex("\\bHELP\\b|ΒΟΗΘΕΙΑ", RegexOption.IGNORE_CASE).containsMatchIn(text)
}

private fun CanonicalEvent.isSelfLoanReturn(): Boolean =
    kind == "transfer" && Regex("^(?:ΕΠΙΣΤΡΟΦΗ|RETURN)(?:\\s|:|$)", RegexOption.IGNORE_CASE).containsMatchIn(note.trim())

private operator fun FlowImpact.plus(other: FlowImpact): FlowImpact = FlowImpact(
    income = income + other.income,
    expense = expense + other.expense,
    saving = saving + other.saving,
    refund = refund + other.refund,
)

internal fun JsonObject.array(name: String): JsonArray = (this[name] as? JsonArray) ?: JsonArray(emptyList())
internal fun JsonObject.obj(name: String): JsonObject = (this[name] as? JsonObject) ?: JsonObject(emptyMap())
internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.updated(name: String, value: JsonElement?): JsonObject = JsonObject(
    toMutableMap().apply { if (value == null) remove(name) else put(name, value) },
)

internal const val CREDIT_ACCOUNT_ID = "credit-card"
