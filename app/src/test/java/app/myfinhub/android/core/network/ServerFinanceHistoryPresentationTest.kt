package app.myfinhub.android.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerFinanceHistoryPresentationTest {
    private fun history(points: Int): ServerFinanceHistorySummary =
        ServerFinanceHistorySummary(
            available=true,generation="7",financeRevision="64",currentPointId="$points",
            canUndo=true,canRedo=false,undoDepth=3,redoDepth=0,
            points=(1..points).map { i ->
                ServerFinanceHistoryPoint(
                    id="$i",parentId=(i-1).takeIf { it>0 }?.toString(),
                    label="private label $i",createdAt="2026-10-19T09:00:00Z",
                    current=i==points,
                )
            },
        )

    @Test fun exactTwentyFourThenFortyEightLatestFirstWithoutHistoryMutation() {
        val source=history(51)
        val original=source.points.toList()
        val first=visibleServerFinanceHistoryPoints(source,24)
        assertEquals(24,first.size)
        assertEquals("51",first.first().id)
        assertEquals("28",first.last().id)
        assertEquals(48,visibleServerFinanceHistoryPoints(source,48).size)
        assertEquals("4",visibleServerFinanceHistoryPoints(source,48).last().id)
        assertEquals(listOf("51","50"),visibleServerFinanceHistoryPoints(source,2).map { it.id })
        assertEquals(original,source.points)
    }

    @Test fun emptyAndInvalidBoundsFailClosedWithoutSyntheticPoints() {
        val source=history(3)
        assertEquals(emptyList<ServerFinanceHistoryPoint>(),visibleServerFinanceHistoryPoints(source,-1))
        assertEquals(listOf("3","2","1"),visibleServerFinanceHistoryPoints(source,500).map { it.id })
        assertEquals(emptyList<ServerFinanceHistoryPoint>(),
            visibleServerFinanceHistoryPoints(history(0),24))
        assertFalse(visibleServerFinanceHistoryPoints(source,1).any { it.id=="unknown" })
    }
}
