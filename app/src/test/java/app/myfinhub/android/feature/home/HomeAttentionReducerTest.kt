package app.myfinhub.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeAttentionReducerTest {
    @Test
    fun ambiguousScheduledSourcesCannotBeDismissedByOneSharedId() {
        val base = syntheticHomeUiState()
        val target = base.attentionItems.first()
        val duplicated = base.copy(
            attentionItems = listOf(target, target.copy(title = "Άλλη υποχρέωση")),
            attentionSourceIdCounts = mapOf(target.id to 2),
        )
        assertEquals(duplicated,
            reduceHomeState(duplicated, HomeAction.DismissAttention(target.id)))

        // The duplicate could be outside the visible Home alert cutoff.
        val hiddenCollision = base.copy(
            attentionSourceIdCounts = mapOf(target.id to 2),
        )
        assertEquals(hiddenCollision,
            reduceHomeState(hiddenCollision, HomeAction.DismissAttention(target.id)))

        val padded = base.copy(
            attentionItems = listOf(target.copy(id = " ${target.id} ")),
        )
        assertEquals(padded,
            reduceHomeState(padded, HomeAction.DismissAttention(" ${target.id} ")))
    }

    @Test
    fun dismissAttention_removesOnlyStableId() {
        val state = syntheticHomeUiState()
        val target = state.attentionItems.first()
        val untouched = state.attentionItems.last()
        val result = reduceHomeState(state, HomeAction.DismissAttention(target.id))
        assertFalse(result.attentionItems.any { it.id == target.id })
        assertEquals(untouched, result.attentionItems.single())
    }
}
