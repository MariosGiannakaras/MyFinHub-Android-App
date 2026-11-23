package app.myfinhub.android.feature.money

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupedCardPanVisualTransformationTest {
    @Test
    fun maskedEntryShowsFourDigitGroupsWithoutExposingInput() {
        val original = "4242424242424242"
        val result = GroupedCardPanVisualTransformation(masked = true)
            .filter(AnnotatedString(original))
        assertEquals("\u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022 \u2022\u2022\u2022\u2022", result.text.text)
        assertFalse(result.text.text.contains(original))
        assertFalse(result.text.text.contains("4242"))
    }

    @Test
    fun secureEditorDisplaysFullLegacyPanWithoutTruncationOrRewrite() {
        val original = "12345678901234567890123456789012"
        val result = GroupedCardPanVisualTransformation(masked = false)
            .filter(AnnotatedString(original))
        assertEquals("1234 5678 9012 3456 7890 1234 5678 9012", result.text.text)
        assertEquals(original, result.text.text.replace(" ", ""))
    }

    @Test
    fun offsetMappingRoundTripsEveryDigitCursorAtGroupBoundaries() {
        val transform = GroupedCardPanVisualTransformation(masked = false)
        for (length in listOf(0, 1, 4, 5, 8, 9, 16, 17, 19, 32, 33)) {
            val result = transform.filter(AnnotatedString("1".repeat(length)))
            val map = result.offsetMapping
            for (offset in 0..length) {
                assertEquals("length=$length offset=$offset", offset,
                    map.transformedToOriginal(map.originalToTransformed(offset)))
            }
            for (offset in 0..result.text.length) {
                assertTrue(map.transformedToOriginal(offset) in 0..length)
            }
        }
    }

    @Test
    fun insertedSeparatorsMapBackToPreviousDigitBoundary() {
        val map = GroupedCardPanVisualTransformation(masked = true)
            .filter(AnnotatedString("123456789")).offsetMapping
        assertEquals(4, map.transformedToOriginal(5))
        assertEquals(8, map.transformedToOriginal(10))
    }
}
