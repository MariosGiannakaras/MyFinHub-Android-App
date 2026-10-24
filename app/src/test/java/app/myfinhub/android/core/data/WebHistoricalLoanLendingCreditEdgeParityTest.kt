package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** Historical compatibility goldens grounded in Web loans.ts, lending.ts and cards.ts. */
class WebHistoricalLoanLendingCreditEdgeParityTest {
    private val doc = CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{
        "loans":[
          {"id":"day-label","name":"Επαγγελματικό","provider":"Τράπεζα",
            "total":360,"installments":12,"installment":30,"day":"15η"},
          {"id":"first-precedence","name":"Προτεραιότητα","provider":"Τράπεζα",
            "total":120,"installments":4,"installment":30,
            "firstExpectedDate":"2026-02-XX","day":"12"},
          {"id":"day-invalid","name":"Άκυρο","total":120,"installments":4,
            "installment":30,"day":"32η"}
        ],
        "lending":[{"person":"Ελένη","outstanding":50,"entries":[]}],
        "accounts":[],"transactions":[]
      },"state":{
        "cards":[
          {"id":"a-credit","kind":"credit","createdAt":"2025-01-01T00:00:00Z"},
          {"id":"b-credit","kind":"credit","createdAt":"2025-07-01T00:00:00Z"}
        ],
        "events":[
          {"id":"no-date","person":"Ελένη","receivableDelta":15,
            "kind":"lending","amount":15},
          {"id":"bad-date","person":"Ελένη","date":"not-a-date",
            "receivableDelta":10,"kind":"lending","amount":10},
          {"id":"future","person":"Ελένη","date":"2026-11-05",
            "receivableDelta":-20,"kind":"repayment","amount":20},
          {"id":"current","person":"Ελένη","date":"2026-10-10",
            "receivableDelta":5,"kind":"lending","amount":5},
          {"id":"white-card","kind":"card_purchase","date":"2026-10-10",
            "cardId":" ","amount":400},
          {"id":"missing-card","kind":"card_purchase","date":"2026-10-11",
            "amount":45},
          {"id":"empty-card","kind":"card_purchase","date":"2026-10-12",
            "cardId":"","amount":5},
          {"id":"other-card","kind":"card_purchase","date":"2026-10-12",
            "cardId":"b-credit","amount":30},
          {"id":"legacy-payment","kind":"card_payment","date":"2026-10-13",
            "amount":15},
          {"id":"future-payment","kind":"card_payment","date":"2026-11-01",
            "amount":10}
        ]
      }}
    """.trimIndent()).jsonObject)

    @Test fun legacyLoanDayUsesWebParseIntWithGreekSuffix() {
        val loans = doc.canonicalLoanBalanceRows("2026-10-16").associateBy { it.id }
        assertEquals(15, loans["day-label"]?.typicalPaymentDay)
        assertEquals(null, loans["first-precedence"]?.typicalPaymentDay)
        assertEquals(null, loans["day-invalid"]?.typicalPaymentDay)
        assertEquals(360.0, loans["day-label"]!!.remaining, 0.001)
        assertEquals(12, loans["day-label"]!!.remainingInstallments)
    }

    @Test fun allTimeLendingIncludesUndatedLegacyDeltasButAsOfCannot() {
        val all = doc.canonicalLendingPersonRows().single()
        assertEquals("Ελένη", all.person)
        assertEquals(60.0, all.outstanding, 0.001)
        assertEquals(4, all.events)
        val asOf = doc.canonicalLendingPersonRows("2026-10-16").single()
        assertEquals(55.0, asOf.outstanding, 0.001)
        assertEquals(1, asOf.events)
        val later = doc.canonicalLendingPersonRows("2026-11-30").single()
        assertEquals(35.0, later.outstanding, 0.001)
        assertEquals(2, later.events)
    }

    @Test fun whitespaceExplicitCreditIdCannotStealLegacyDebtFromOldestCard() {
        assertEquals(35.0, doc.creditDebtForCardAt("a-credit", "2026-10-16"), 0.001)
        assertEquals(30.0, doc.creditDebtForCardAt("b-credit", "2026-10-16"), 0.001)
        assertEquals(25.0, doc.creditDebtForCardAt("a-credit", "2026-11-02"), 0.001)
        assertEquals(0.0, doc.creditDebtForCardAt("non-existent", "2026-11-02"), 0.001)
    }

    @Test fun malformedHistoricalDatesDoNotLeakIntoDateScopedLoanOrLendingSnapshots() {
        assertEquals(55.0, doc.canonicalLendingPersonRows("2026-10-16").single().outstanding, 0.001)
        assertEquals(360.0, doc.canonicalLoanBalanceRows("2026-10-16")
            .first { it.id == "day-label" }.remaining, 0.001)
    }
    @Test fun mixedFundingExpenseDebtIncludesOnlyCreditLegAndPreservesOwnerAttribution() {
        val mixed = CanonicalFinanceDocument(Json.parseToJsonElement("""
          {"seed":{"accounts":[]},"state":{
            "cards":[
              {"id":"first","kind":"credit","createdAt":"2025-01-01T00:00:00Z"},
              {"id":"second","kind":"credit","createdAt":"2025-06-01T00:00:00Z"}
            ],
            "events":[
              {"id":"mixed","date":"2026-10-10","kind":"expense","cardId":"second",
               "amount":125,"funding":[
                 {"kind":"cash","amount":85},{"kind":"credit","amount":40}
               ],"creditDelta":-40},
              {"id":"legacy-mixed","date":"2026-10-10","kind":"expense",
               "amount":70,"funding":[
                 {"kind":"cash","amount":50},{"kind":"credit","amount":20}
               ],"creditDelta":-20},
              {"id":"not-funded","date":"2026-10-11","kind":"expense","cardId":"second",
               "amount":90,"creditDelta":-90},
              {"id":"payment","date":"2026-10-12","kind":"card_payment","cardId":"second",
               "amount":15},
              {"id":"future","date":"2026-11-11","kind":"expense","cardId":"second",
               "amount":60,"funding":[{"kind":"credit","amount":60}],"creditDelta":-60}
            ]
          }}
        """.trimIndent()).jsonObject)
        assertEquals(20.0, mixed.creditDebtForCardAt("first", "2026-10-20"), 0.00001)
        assertEquals(25.0, mixed.creditDebtForCardAt("second", "2026-10-20"), 0.00001)
        assertEquals(85.0, mixed.creditDebtForCardAt("second", "2026-11-20"), 0.00001)
    }

}
