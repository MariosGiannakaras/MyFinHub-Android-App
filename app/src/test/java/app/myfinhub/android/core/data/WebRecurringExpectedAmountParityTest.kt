package app.myfinhub.android.core.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Web recurringVariable.ts exact amount confidence / strict estimate validation. */
class WebRecurringExpectedAmountParityTest {
    private fun document(rawRows: String) = CanonicalFinanceDocument(
        Json.parseToJsonElement("""
            {"seed":{"recurring":[$rawRows]},"state":{"events":[]}}
        """.trimIndent()).jsonObject,
    )

    @Test fun fixedAndEstimatedAmountsMatchWebWithoutPostingRealExpenses() {
        val data = document("""
          {"id":"fixed","name":"Rent","amount":52.25,"active":true},
          {"id":"variable","name":"Electricity","amount":80,"active":true,
           "amountMode":"variable","estimateMin":60,"estimateMax":110},
          {"id":"variable-no-range","name":"Water","amount":25.12,"active":true,
           "amountMode":"variable"}
        """.trimIndent())
        val original = data.raw
        val entries = data.allCanonicalRecurringItems().map { it.canonicalExpectedAmount() }
        assertEquals(listOf("fixed", "estimated", "estimated"), entries.map { it.confidence })
        assertEquals(listOf("Σταθερό ποσό", "Εκτίμηση · όχι πραγματική χρέωση",
            "Εκτίμηση · όχι πραγματική χρέωση"), entries.map { it.label })
        assertEquals(listOf(52.25, 80.0, 25.12), entries.map { it.amount })
        assertEquals(60.0, entries[1].min!!, 0.001)
        assertEquals(110.0, entries[1].max!!, 0.001)
        assertEquals(null, entries[2].min)
        assertEquals(null, entries[2].max)
        assertEquals(0, data.canonicalEvents().size)
        assertEquals(original, data.raw)
    }

    @Test fun malformedHistoricalModesAndRangesStayVisibleButCannotMasqueradeAsValid() {
        val data = document("""
          {"id":"unknown","name":"Legacy mode","amount":20,"active":true,
           "amountMode":"unsupported"},
          {"id":"fixed-bounds","name":"Fixed with bounds","amount":40,"active":true,
           "amountMode":"fixed","estimateMin":30,"estimateMax":50},
          {"id":"negative-bound","name":"Negative bound","amount":40,"active":true,
           "amountMode":"variable","estimateMin":-5,"estimateMax":50},
          {"id":"missing-high","name":"Half range","amount":40,"active":true,
           "amountMode":"variable","estimateMin":30},
          {"id":"null-high","name":"Null bound","amount":40,"active":true,
           "amountMode":"variable","estimateMin":30,"estimateMax":null},
          {"id":"outside","name":"Outside range","amount":60,"active":true,
           "amountMode":"variable","estimateMin":30,"estimateMax":50},
          {"id":"fractional","name":"Fractional cents","amount":10.001,"active":true,
           "amountMode":"variable"},
          {"id":"zero","name":"Zero estimate","amount":0,"active":true,
           "amountMode":"variable"}
        """.trimIndent())
        val original = data.raw
        val values = data.allCanonicalRecurringItems().associateBy { it.id }
        assertEquals("unsupported", values.getValue("unknown").amountMode)
        assertFalse(values.getValue("unknown").isVariableEstimate)
        assertEquals(-5.0, values.getValue("negative-bound").estimateMin!!, 0.001)
        assertTrue(values.getValue("null-high").estimateMaxPresent)
        assertEquals(null, values.getValue("null-high").estimateMax)
        for ((id, fragment) in listOf(
            "unknown" to "έγκυρο τύπο",
            "fixed-bounds" to "μόνο σε μεταβλητά",
            "negative-bound" to "πρέπει να περιλαμβάνει",
            "missing-high" to "και τα δύο άκρα",
            "null-high" to "πρέπει να περιλαμβάνει",
            "outside" to "πρέπει να περιλαμβάνει",
            "fractional" to "δύο δεκαδικά",
            "zero" to "θετικό ποσό",
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) {
                values.getValue(id).canonicalExpectedAmount()
            }
            assertTrue("$id: ${error.message}", error.message!!.contains(fragment))
        }
        assertEquals(0, data.canonicalEvents().size)
        assertEquals(original, data.raw)
    }
}
