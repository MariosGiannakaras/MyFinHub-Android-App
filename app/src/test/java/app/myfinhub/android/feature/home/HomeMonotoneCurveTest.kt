package app.myfinhub.android.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeMonotoneCurveTest {
    @Test fun monotoneAndFlatDailyHistoryDoesNotOvershootObservedValues() {
        val points = listOf(
            HomeCurvePoint(0f, 10f), HomeCurvePoint(10f, 10f),
            HomeCurvePoint(20f, 5f), HomeCurvePoint(30f, 3f),
            HomeCurvePoint(40f, 3f), HomeCurvePoint(50f, 8f),
        )
        val curves = homeMonotoneSegments(points)
        assertEquals(points.size - 1, curves.size)
        for (index in curves.indices) {
            val a = points[index]
            val b = points[index + 1]
            val c = curves[index]
            // Cubic is a convex combination of its four controls; if controls
            // are inside endpoint bounds, the rendered path has no overshoot.
            val lower = minOf(a.y, b.y) - 0.0001f
            val upper = maxOf(a.y, b.y) + 0.0001f
            assertTrue(c.control1.y in lower..upper)
            assertTrue(c.control2.y in lower..upper)
            assertEquals(b, c.end)
        }
        assertEquals(emptyList<HomeCurveSegment>(), homeMonotoneSegments(listOf(points[0])))
    }
    @Test fun subpathUsesSameControlPointsAsWholeHistory() {
        val points = listOf(HomeCurvePoint(0f, 0f), HomeCurvePoint(10f, 4f),
            HomeCurvePoint(20f, 7f), HomeCurvePoint(30f, 8f))
        assertEquals(homeMonotoneSegments(points)[2],
            homeMonotoneSegments(points, 2, 3).single())
        assertEquals(10f*.8f/3f,
            homeMonotoneSegments(points).first().control1.x, .0001f)
    }
}
