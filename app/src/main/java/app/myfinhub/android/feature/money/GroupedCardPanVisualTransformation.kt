package app.myfinhub.android.feature.money

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Visual-only PAN grouping. Backing editor values remain ungrouped; paste,
 * selection, deletion, strict server validation and legacy reads are unchanged.
 * Mask new-card entry and keep its explicit password accessibility semantics.
 */
internal class GroupedCardPanVisualTransformation(
    private val masked: Boolean,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val shown = if (masked) "\u2022".repeat(raw.length) else raw
        val grouped = shown.chunked(4).joinToString(" ")
        val length = raw.length
        val renderedLength = grouped.length
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val safe = offset.coerceIn(0, length)
                return (safe + if (safe == 0) 0 else (safe - 1) / 4)
                    .coerceAtMost(renderedLength)
            }
            override fun transformedToOriginal(offset: Int): Int {
                val safe = offset.coerceIn(0, renderedLength)
                return (safe - safe / 5).coerceIn(0, length)
            }
        }
        return TransformedText(AnnotatedString(grouped), mapping)
    }
}
