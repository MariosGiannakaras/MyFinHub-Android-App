package app.myfinhub.android.core.data

import kotlin.math.max
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
)

data class CanonicalLedgerLeg(
    val accountId: String,
    val amount: Double,
)

data class CanonicalSplitPart(
    val id: String,
    val label: String,
    val category: String,
    val subcategory: String?,
    val amount: Double,
)

data class CanonicalEvent(
    val id: String,
    val date: String,
    val kind: String,
    val amount: Double,
    val note: String,
    val category: String?,
    val accountId: String?,
    val fromAccountId: String?,
    val toAccountId: String?,
    val cardId: String?,
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

fun CanonicalFinanceDocument.canonicalAccounts(): List<CanonicalAccount> {
    val settings = state.obj("settings")
    val renamed = settings.obj("accountNames")
    val excluded = settings.array("excludedFromAvailable")
        .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        .toSet()

    return seed.array("accounts").mapNotNull { element ->
        val rawAccount = element as? JsonObject ?: return@mapNotNull null
        val id = rawAccount.string("id") ?: return@mapNotNull null
        CanonicalAccount(
            id = id,
            name = renamed.string(id) ?: rawAccount.string("name") ?: id,
            shortName = rawAccount.string("short"),
            kind = rawAccount.string("kind").orEmpty(),
            excludeFromAvailable = rawAccount.bool("excludeFromAvailable") == true || id in excluded,
        )
    }
}

/** Mirrors the web effectiveLegacyTransactions contract: seed - deleted + overrides + custom. */
fun CanonicalFinanceDocument.effectiveLegacyTransactions(): List<CanonicalLegacyTransaction> {
    val seedTransactions = seed.array("transactions")
        .mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)
    val deleted = deletedTransactionIds()
    val overrides = state.obj("overrides")

    val effectiveSeed = seedTransactions.values.mapNotNull { raw ->
        val id = raw.string("id") ?: return@mapNotNull null
        if (id in deleted) return@mapNotNull null
        val effective = overrides[id] as? JsonObject ?: raw
        effective.toLegacyTransaction()
    }
    val custom = state.array("customTransactions").mapNotNull { (it as? JsonObject)?.toLegacyTransaction() }
    return (effectiveSeed + custom).sortedBy(CanonicalLegacyTransaction::date)
}

fun CanonicalFinanceDocument.canonicalEvents(): List<CanonicalEvent> =
    state.array("events").mapNotNull { element ->
        val event = element as? JsonObject ?: return@mapNotNull null
        val id = event.string("id") ?: return@mapNotNull null
        CanonicalEvent(
            id = id,
            date = event.string("date").orEmpty(),
            kind = event.string("kind").orEmpty(),
            amount = event.number("amount") ?: 0.0,
            note = event.string("note").orEmpty(),
            category = event.string("category"),
            accountId = event.string("accountId"),
            fromAccountId = event.string("fromAccountId"),
            toAccountId = event.string("toAccountId"),
            cardId = event.string("cardId"),
            legs = event.array("legs").mapNotNull { legElement ->
                val leg = legElement as? JsonObject ?: return@mapNotNull null
                CanonicalLedgerLeg(
                    accountId = leg.string("accountId") ?: return@mapNotNull null,
                    amount = leg.number("amount") ?: return@mapNotNull null,
                )
            },
            parts = event.array("parts").mapNotNull { partElement ->
                val part = partElement as? JsonObject ?: return@mapNotNull null
                CanonicalSplitPart(
                    id = part.string("id") ?: return@mapNotNull null,
                    label = part.string("label").orEmpty(),
                    category = part.string("category") ?: "Άλλο",
                    subcategory = part.string("subcategory"),
                    amount = part.number("amount") ?: return@mapNotNull null,
                )
            },
            savingAmount = event.number("savingAmount") ?: 0.0,
            receivableDelta = event.number("receivableDelta") ?: 0.0,
            creditDelta = event.number("creditDelta") ?: 0.0,
        )
    }

fun CanonicalFinanceDocument.canonicalScheduled(): List<CanonicalScheduledItem> =
    state.array("scheduled").mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        CanonicalScheduledItem(
            id = item.string("id") ?: return@mapNotNull null,
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
    state.array("cards").mapNotNull { element ->
        val card = element as? JsonObject ?: return@mapNotNull null
        CanonicalCard(
            id = card.string("id") ?: return@mapNotNull null,
            nickname = card.string("nickname").orEmpty(),
            kind = card.string("kind").orEmpty(),
            network = card.string("network").orEmpty(),
            last4 = card.string("last4"),
            creditLimit = card.number("creditLimit"),
            active = card.bool("active") ?: false,
            vaultRef = card.string("vaultRef"),
        )
    }

/** Mirrors the current web balance algorithm: latest seed snapshot + mutable legacy deltas + event legs. */
fun CanonicalFinanceDocument.accountBalances(asOf: String): Map<String, Double> {
    val snapshots = seed.array("snapshots").mapNotNull { it as? JsonObject }
        .filter { it.string("date").orEmpty() <= asOf }
    val snapshot = snapshots.maxByOrNull { it.string("date").orEmpty() }
    val balances = snapshot?.obj("balances")?.mapValues { (_, value) ->
        (value as? JsonPrimitive)?.doubleOrNull ?: 0.0
    }?.toMutableMap() ?: canonicalAccounts().associate { it.id to 0.0 }.toMutableMap()
    balances.putIfAbsent(CREDIT_ACCOUNT_ID, 0.0)

    val seedTransactions = seed.array("transactions")
        .mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)
    val deleted = deletedTransactionIds()

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

    deleted.forEach { id -> seedTransactions[id]?.let { applyLegacy(it, -1.0) } }
    state.obj("overrides").forEach { (id, value) ->
        seedTransactions[id]?.let { applyLegacy(it, -1.0) }
        (value as? JsonObject)?.let { applyLegacy(it, 1.0) }
    }
    state.array("customTransactions").forEach { element ->
        (element as? JsonObject)?.let { applyLegacy(it, 1.0) }
    }
    canonicalEvents().filter { it.date <= asOf }.forEach { event ->
        event.legs.forEach { leg -> balances[leg.accountId] = (balances[leg.accountId] ?: 0.0) + leg.amount }
    }
    return balances
}

fun CanonicalFinanceDocument.availableMoney(asOf: String): Double {
    val balances = accountBalances(asOf)
    return canonicalAccounts()
        .filter { it.kind != "credit" && !it.excludeFromAvailable }
        .sumOf { balances[it.id] ?: 0.0 }
}

fun CanonicalFinanceDocument.monthlyFlow(month: String): CanonicalMonthlyFlow {
    val start = "$month-01"
    val end = "$month-31"
    var income = 0.0
    var expense = 0.0
    var saving = 0.0
    var refunds = 0.0

    effectiveLegacyTransactions().filter { it.date in start..end }.forEach { tx ->
        val semanticKind = confirmedReviewKind(tx.id)
        when (semanticKind) {
            "saving_cash_offset" -> saving += tx.amount
            "withdrawal", "transfer", "card_payment", "reconciliation" -> Unit
            "refund" -> {
                expense -= tx.amount
                refunds += tx.amount
            }
            else -> when (tx.type) {
                "income" -> income += tx.amount
                "expense" -> expense += tx.amount
            }
        }
    }

    canonicalEvents().filter { it.date in start..end }.forEach { event ->
        when (event.kind) {
            "income" -> income += event.amount
            "expense", "card_purchase" -> expense += event.amount
            "split" -> expense += event.parts.sumOf(CanonicalSplitPart::amount)
            "saving_cash_offset" -> saving += if (event.savingAmount > 0.0) event.savingAmount else event.amount
            "refund" -> {
                expense -= event.amount
                refunds += event.amount
            }
        }
    }
    return CanonicalMonthlyFlow(income, max(0.0, expense), saving, refunds)
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
        when (confirmedReviewKind(tx.id)) {
            "refund" -> add(tx.category, -tx.amount)
            "transfer", "withdrawal", "card_payment", "reconciliation", "saving_cash_offset" -> Unit
            else -> if (tx.type == "expense") add(tx.category, tx.amount)
        }
    }
    canonicalEvents().filter { it.date in start..end }.forEach { event ->
        when (event.kind) {
            "expense", "card_purchase" -> add(event.category, event.amount)
            "split" -> event.parts.forEach { add(it.category, it.amount) }
            "refund" -> add(event.category, -event.amount)
        }
    }
    return totals.mapValues { (_, amount) -> max(0.0, amount) }.filterValues { it > 0.0 }
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
    return CanonicalBudget(id = null, month = month, amount = settingsAmount, alertThreshold = null)
}

fun CanonicalFinanceDocument.loanOutstanding(): Double {
    val base = seed.array("loans").mapNotNull { it as? JsonObject }
        .associateBy { it.string("id").orEmpty() }
        .filterKeys(String::isNotBlank)
        .toMutableMap()
    state.obj("loanOverrides").forEach { (id, value) ->
        (value as? JsonObject)?.let { base[id] = it }
    }
    state.array("customLoans").mapNotNull { it as? JsonObject }.forEach { loan ->
        loan.string("id")?.let { base[it] = loan }
    }
    val extra = state.obj("loanExtra")
    return base.values.sumOf { loan ->
        val id = loan.string("id").orEmpty()
        val total = loan.number("total") ?: 0.0
        val installment = loan.number("installment") ?: 0.0
        val paidCount = loan.number("paidCount") ?: 0.0
        val forgiven = loan.number("forgivenAmount") ?: 0.0
        max(0.0, total - installment * paidCount - forgiven - (extra.number(id) ?: 0.0))
    }
}

fun CanonicalFinanceDocument.receivableOutstanding(): Double {
    val seedOutstanding = seed.array("lending").mapNotNull { it as? JsonObject }
        .sumOf { it.number("outstanding") ?: 0.0 }
    val customOutstanding = state.array("lendingCustom").mapNotNull { it as? JsonObject }
        .sumOf { it.number("outstanding") ?: 0.0 }
    return max(0.0, seedOutstanding + customOutstanding + canonicalEvents().sumOf(CanonicalEvent::receivableDelta))
}

fun CanonicalFinanceDocument.cardOutstanding(cardId: String, asOf: String): Double = max(
    0.0,
    -canonicalEvents().filter { it.cardId == cardId && it.date <= asOf }.sumOf(CanonicalEvent::creditDelta),
)

fun CanonicalFinanceDocument.settingsObject(): JsonObject = state.obj("settings")

private fun CanonicalFinanceDocument.deletedTransactionIds(): Set<String> = when (val deleted = state["deleted"]) {
    is JsonArray -> deleted.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet()
    is JsonObject -> deleted.filterValues { (it as? JsonPrimitive)?.booleanOrNull == true }.keys
    else -> emptySet()
}

private fun CanonicalFinanceDocument.confirmedReviewKind(transactionId: String): String? {
    val review = state.obj("reviewDecisions")[transactionId] as? JsonObject ?: return null
    if (review.string("status") != "confirmed") return null
    return review.string("semanticKind")
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
)

internal fun JsonObject.array(name: String): JsonArray = (this[name] as? JsonArray) ?: JsonArray(emptyList())
internal fun JsonObject.obj(name: String): JsonObject = (this[name] as? JsonObject) ?: JsonObject(emptyMap())
internal fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.number(name: String): Double? = (this[name] as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.updated(name: String, value: JsonElement?): JsonObject = JsonObject(
    toMutableMap().apply {
        if (value == null) remove(name) else put(name, value)
    },
)

internal const val CREDIT_ACCOUNT_ID = "credit-card"
