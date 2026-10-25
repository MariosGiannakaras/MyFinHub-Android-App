package app.myfinhub.android.feature.home

/** Web monotoneChartPath.ts: fixed 80%-strength, no-overshoot cubic handles. */
internal data class HomeCurvePoint(val x: Float, val y: Float)
internal data class HomeCurveSegment(
    val control1: HomeCurvePoint,
    val control2: HomeCurvePoint,
    val end: HomeCurvePoint,
)

/** Uses full-history tangents even when rendering a period sub-path. */
internal fun homeMonotoneSegments(
    points: List<HomeCurvePoint>,
    firstIndex: Int = 0,
    lastIndex: Int = points.lastIndex,
): List<HomeCurveSegment> {
    if (points.size < 2) return emptyList()
    val first = firstIndex.coerceIn(0, points.lastIndex)
    val last = lastIndex.coerceIn(first, points.lastIndex)
    val slopes = points.zipWithNext { a, b ->
        val dx = b.x - a.x
        if (dx > 0f) (b.y - a.y) / dx else 0f
    }
    val tangents = points.indices.map { index ->
        when (index) {
            0 -> slopes.first()
            points.lastIndex -> slopes.last()
            else -> {
                val left = slopes[index - 1]
                val right = slopes[index]
                if (left * right <= 0f) 0f else 2f * left * right / (left + right)
            }
        }
    }
    return (first until last).map { index ->
        val a = points[index]
        val b = points[index + 1]
        val width = b.x - a.x
        val handle = if (width > 0f) width * .8f / 3f else 0f
        HomeCurveSegment(
            control1 = HomeCurvePoint(a.x + handle, a.y + tangents[index] * handle),
            control2 = HomeCurvePoint(b.x - handle, b.y - tangents[index + 1] * handle),
            end = b,
        )
    }
}
