package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountRouteIdentityTest {
    private val payroll = MoneyAccount(
        id = "payroll", name = "Μισθοδοσία", balance = 300.0,
        kind = "Τράπεζα", canonicalKind = "bank",
    )
    private val cash = MoneyAccount(
        id = "cash", name = "Μετρητά", balance = 20.0,
        kind = "Μετρητά", canonicalKind = "cash",
    )

    @Test
    fun uniqueCanonicalAccountStillOpensNormally() {
        val state = MoneyUiState(
            accounts = listOf(payroll, cash),
            accountSourceIdCounts = mapOf("payroll" to 1, "cash" to 1),
        )
        assertEquals(payroll, state.uniqueAccountForRoute("payroll"))
        assertEquals(cash, state.uniqueAccountForRoute("cash"))
    }

    @Test
    fun duplicateVisibleAccountCannotOpenFirstMatch() {
        val state = MoneyUiState(
            accounts = listOf(payroll, payroll.copy(name = "Άλλος λογαριασμός")),
            accountSourceIdCounts = mapOf("payroll" to 2),
        )
        assertNull(state.uniqueAccountForRoute("payroll"))
        // Legacy fixtures without raw source counts still reject duplicated rows.
        assertNull(state.copy(accountSourceIdCounts = null).uniqueAccountForRoute("payroll"))
    }

    @Test
    fun hiddenCreditKindRowCollisionCannotMakeVisibleAccountUnique() {
        // Only the bank account is presented by MoneyUiState.accounts, but
        // the canonical source also contains a credit-kind account with this ID.
        val state = MoneyUiState(
            accounts = listOf(payroll),
            accountSourceIdCounts = mapOf("payroll" to 2),
        )
        assertNull(state.uniqueAccountForRoute("payroll"))
        assertNull(state.copy(accountSourceIdCounts = emptyMap()).uniqueAccountForRoute("payroll"))
    }

    @Test
    fun staleNoncanonicalOrMissingAccountIdCannotBeResolved() {
        val state = MoneyUiState(accounts = listOf(payroll))
        listOf("", " ", "payroll ", " payroll", "missing").forEach { id ->
            assertNull(state.uniqueAccountForRoute(id))
        }
    }

    @Test
    fun sourceRefreshRevokesPreviouslyValidAccountRoute() {
        val original = MoneyUiState(accounts = listOf(payroll), accountSourceIdCounts = mapOf("payroll" to 1))
        val refreshed = original.copy(accountSourceIdCounts = mapOf("payroll" to 2))
        assertEquals(payroll, original.uniqueAccountForRoute("payroll"))
        assertNull(refreshed.uniqueAccountForRoute("payroll"))
    }
}
