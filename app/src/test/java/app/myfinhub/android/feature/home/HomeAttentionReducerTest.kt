package app.myfinhub.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HomeAttentionReducerTest {
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
