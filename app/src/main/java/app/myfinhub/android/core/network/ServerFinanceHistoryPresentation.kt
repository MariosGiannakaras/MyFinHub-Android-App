package app.myfinhub.android.core.network

/** A bounded, latest-first read-only projection of Web /api/history.
 * Server arrays arrive chronological, oldest first, from the revision authority.
 * Never synthesize actions or copy snapshots into a local financial store.
 */
fun visibleServerFinanceHistoryPoints(
    summary: ServerFinanceHistorySummary,
    requestedCount: Int,
): List<ServerFinanceHistoryPoint> =
    summary.points.takeLast(requestedCount.coerceIn(0, summary.points.size)).asReversed()
