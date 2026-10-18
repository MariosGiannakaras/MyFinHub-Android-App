package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Golden vectors derived from Web reports.ts subcategoryTotals/categoryMomentum. */
class WebReportCategoryMomentumParityTest {
    private val doc=CanonicalFinanceDocument(Json.parseToJsonElement("""
      {"seed":{"transactions":[
        {"id":"old","date":"2026-09-05","type":"expense","amount":60,
         "category":"Τρόφιμα","subcategory":"Σούπερ"}
      ]},"state":{"settings":{},"events":[
        {"id":"prev-health","kind":"expense","date":"2026-09-12","amount":75,
         "category":"Υγεία"},
        {"id":"prev-car","kind":"expense","date":"2026-09-14","amount":30,
         "category":"Μεταφορές","subcategory":"Καύσιμα"},
        {"id":"now-food-a","kind":"expense","date":"2026-10-06","amount":120,
         "category":"Τρόφιμα","subcategory":"Σούπερ"},
        {"id":"now-food-b","kind":"expense","date":"2026-10-08","amount":60,
         "category":"Τρόφιμα","subcategory":"Σούπερ"},
        {"id":"food-refund","kind":"refund","date":"2026-10-10","amount":30,
         "category":"Τρόφιμα","subcategory":"Σούπερ"},
        {"id":"health","kind":"expense","date":"2026-10-09","amount":75,
         "category":"Υγεία"},
        {"id":"transport-split","kind":"split","date":"2026-10-11",
         "parts":[
          {"id":"p1","kind":"expense","category":"Μεταφορές",
           "subcategory":"Καύσιμα","amount":40},
          {"id":"p2","kind":"refund","category":"Μεταφορές",
           "subcategory":"Καύσιμα","amount":10}
         ]},
        {"id":"new","kind":"expense","date":"2026-10-13","amount":20,
         "category":"Αγορές"},
        {"id":"future","kind":"expense","date":"2026-10-27","amount":900,
         "category":"Αγορές"},
        {"id":"internal","kind":"transfer","date":"2026-10-09","amount":500,
         "category":"Άλλο"}
      ]}}
    """.trimIndent()).jsonObject)

    @Test fun refundImpactsSplitPortionsAndFutureCutoffMatchWeb() {
        val before=doc.raw
        val rows=doc.canonicalReportSubcategoryTotals("2026-10","2026-10-19")
        assertEquals(listOf("Τρόφιμα › Σούπερ","Υγεία","Μεταφορές › Καύσιμα","Αγορές"),
            rows.map { it.name })
        // Web's expense and refund impacts both apply to unsplit refund events.
        assertEquals(listOf(120.0,75.0,30.0,20.0),rows.map { it.value })
        assertEquals(before,doc.raw)
    }

    @Test fun currentVsFullPriorMonthRelativeChangeAndNewBaseline() {
        val values=doc.canonicalReportCategoryMomentum("2026-10",asOf="2026-10-19")
        assertEquals(4,values.size)
        assertEquals(60.0,values[0].previous,0.001)
        assertEquals(1.0,values[0].change!!,0.001)
        assertEquals(75.0,values[1].previous,0.001)
        assertEquals(0.0,values[1].change!!,0.001)
        assertEquals(30.0,values[2].previous,0.001)
        assertEquals(0.0,values[2].change!!,0.001)
        assertEquals(0.0,values[3].previous,0.001)
        assertNull(values[3].change)
        assertEquals(listOf("Τρόφιμα › Σούπερ","Υγεία"),
            doc.canonicalReportCategoryMomentum("2026-10",limit=2,asOf="2026-10-19")
                .map { it.name })
        assertTrue(doc.canonicalReportCategoryMomentum("2026-10",limit=0).isEmpty())
    }
}
