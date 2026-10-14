package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalAccountMetadataParityTest {
    @Test
    fun appliesSeedOverridesCustomAccountsAndLegacyAvailabilityWithoutChangingIdentity() {
        val doc = document("""
          {"seed":{"accounts":[
            {"id":"wallet","name":"Wallet","kind":"cash","cashRole":"reserve"},
            {"id":"payroll","name":"Old payroll","kind":"bank","bankAccountCategory":"current",
             "providerId":"old","excludeFromAvailable":false},
            {"id":"save","name":"Savings","kind":"bank","bankAccountCategory":"savings"}
          ]},
          "state":{"settings":{
            "accountOverrides":{
              "wallet":{"cashRole":"daily","name":"Cash","id":"malicious-id"},
              "payroll":{"bankAccountCategory":"payroll","providerId":"piraeus","name":"Salary"}
            },
            "customAccounts":[
              {"id":"other","name":"Reserve","kind":"cash","cashRole":"reserve","showInQuickChoices":false},
              {"id":"wallet","name":"duplicate","kind":"bank"}
            ],
            "excludedFromAvailable":["other"]
          }}}
        """)
        val accounts = doc.canonicalAccounts()
        assertEquals(listOf("wallet","payroll","save","other"),accounts.map { it.id })
        assertEquals("Cash",accounts[0].name)
        assertEquals("daily",accounts[0].cashRole)
        assertEquals("piraeus",accounts[1].providerId)
        assertEquals("payroll",accounts[1].bankAccountCategory)
        assertTrue(accounts[2].isSavingsAccount())
        assertTrue(accounts[3].excludeFromAvailable)
        assertFalse(accounts[3].showInQuickChoices)
    }

    @Test
    fun savingsContributionSourceIsOnlyBankCurrentPayrollOrLegacyUnknown() {
        val accounts = listOf(
            CanonicalAccount("cash","Cash",null,"cash",false,cashRole="daily"),
            CanonicalAccount("payroll","Salary",null,"bank",false,bankAccountCategory="payroll"),
            CanonicalAccount("current","Current",null,"bank",false,bankAccountCategory="current"),
            CanonicalAccount("legacy","Legacy",null,"bank",false),
            CanonicalAccount("savings","Savings",null,"bank",false,bankAccountCategory="savings"),
            CanonicalAccount("payment","Payment",null,"bank",false,bankAccountCategory="payment"),
            CanonicalAccount("term","Term",null,"bank",false,bankAccountCategory="term")
        )
        assertEquals(listOf(false,true,true,true,false,false,false),
            accounts.map { it.isSavingContributionSource() })
        assertEquals(listOf(false,false,false,false,true,false,false),
            accounts.map { it.isSavingsAccount() })
    }

    private fun document(json: String): CanonicalFinanceDocument =
        CanonicalFinanceDocument(Json.parseToJsonElement(json.trimIndent()).jsonObject)
}
