package app.myfinhub.android.feature.quickentry

/**
 * Local enqueue is a completed user action even though the server has not acknowledged it yet.
 * The fallback expression keeps compatibility with projections created before pendingSync became an
 * explicit state bit: an already-previewed, clean, non-server-persisted draft is locally committed.
 */
val QuickEntryUiState.awaitingSync: Boolean
    get() = pendingSync || (!persisted && savedSummary != null && !dirty)
