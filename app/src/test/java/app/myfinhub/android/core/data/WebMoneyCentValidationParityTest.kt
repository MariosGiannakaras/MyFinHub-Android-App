package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden vectors from Web src/lib/money.ts (JS Math.round + signed EPSILON),
 * src/lib/domain.ts createEvent and src/lib/ledgerFoundations.ts splitAllocation.
 */
class WebMoneyCentValidationParityTest {
    private val now = "2026-10-16T11:00:00Z"
    private val document = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "accounts":[{"id":"payroll","kind":"bank","bankAccountCategory":"payroll"}],
        "snapshots":[{"date":"2026-10-01","balances":{"payroll":1000}}],
        "transactions":[]
      },"state":{"events":[],"settings":{
        "expenseCategories":["Άλλο"],"incomeCategories":["Μισθός"]
      }}}
    """.trimIndent()).jsonObject)

    @Test fun javascriptSignedEpsilonAndHalfCentRoundingAreIdentical() {
        val cases = listOf(
            1.005 to 101L,
            2.675 to 268L,
            10.075 to 1007L,
            1.335 to 134L,
            -1.005 to -101L,
            -2.675 to -267L,
            0.0 to 0L,
        )
        cases.forEach { (amount, expected) ->
            assertEquals("Web moneyToCents($amount)", expected, moneyToCents(amount))
        }
    }

    @Test fun safeJavascriptCentIntegerBoundaryIsEnforcedNotLongMaxValue() {
        // Web Number.isSafeInteger accepts the exact upper bound, not its successor.
        assertEquals(MAX_SAFE_CANONICAL_CENTS, moneyToCents(90_071_992_547_409.9))
        assertEquals(-MAX_SAFE_CANONICAL_CENTS, moneyToCents(-90_071_992_547_409.9))
        for (invalid in listOf(90_071_992_547_410.0, -90_071_992_547_410.0,
            100_000_000_000_000.0, Double.POSITIVE_INFINITY, Double.NaN)) {
            assertTrue("Must reject unsafe ${invalid}",
                runCatching { moneyToCents(invalid) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun normalEntryRoundsOnceAndReplayKeepsTheSameLedgerAndCategoryBudget() {
        val intent = createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "expense", date = "2026-10-16", amount = 1.005,
                note = "Web cents", category = "Τρόφιμα", accountId = "payroll",
            ), "stable-web-cent-1", now,
        )
        val once = intent.apply(document)
        val event = once.canonicalEvents().single()
        assertEquals(1.01, event.amount, 0.00000001)
        assertEquals(-1.01, event.legs.single().amount, 0.00000001)
        assertEquals(1.01, once.categoryBudgetSpending("2026-10")["Τρόφιμα"]!!, 0.00000001)
        assertEquals(998.99, once.accountBalances("2026-10-16")["payroll"]!!, 0.00000001)
        assertEquals(once.raw, intent.apply(once).raw)
    }

    @Test fun splitsUseWebRoundedPartsForExactParentAndRejectUnsafeAggregate() {
        val split = createCanonicalTransactionEntryMutation(
            document,
            CanonicalTransactionEntryDraft(
                kind = "split", date = "2026-10-16", accountId = "payroll",
                parts = listOf(
                    CanonicalTransactionSplitDraft("food", category = "Τρόφιμα", amount = 1.005),
                    CanonicalTransactionSplitDraft("travel", category = "Μετακινήσεις", amount = 2.675),
                ),
            ), "stable-split-cent-1", now,
        )
        val applied = split.apply(document)
        val event = applied.canonicalEvents().single()
        assertEquals(3.69, event.amount, 0.00000001)
        assertEquals(-3.69, event.legs.single().amount, 0.00000001)
        assertEquals(listOf(1.01, 2.68), event.parts.map { it.amount })
        assertEquals(1.01, applied.categoryBudgetSpending("2026-10")["Τρόφιμα"]!!, 0.00000001)
        assertEquals(2.68, applied.categoryBudgetSpending("2026-10")["Μετακινήσεις"]!!, 0.00000001)
        assertEquals(applied.raw, split.apply(applied).raw)

        val rejected = runCatching {
            createCanonicalTransactionEntryMutation(
                document,
                CanonicalTransactionEntryDraft(
                    kind = "split", date = "2026-10-16", accountId = "payroll",
                    parts = listOf(
                        CanonicalTransactionSplitDraft("a", category = "Άλλο",
                            amount = 50_000_000_000_000.0),
                        CanonicalTransactionSplitDraft("b", category = "Άλλο",
                            amount = 50_000_000_000_000.0),
                    ),
                ), "unsafe-split", now,
            )
        }
        assertTrue("Web would reject the parent via isSafeMoneyValue",
            rejected.exceptionOrNull() is IllegalArgumentException)
    }
}
