from pathlib import Path

path = Path("app/src/main/java/app/myfinhub/android/app/FinanceProductViewModel.kt")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"Expected exactly one occurrence, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)


def replace_region(start: str, end: str, replacement: str) -> None:
    global text
    start_index = text.find(start)
    if start_index < 0:
        raise SystemExit(f"Missing region start: {start}")
    end_index = text.find(end, start_index)
    if end_index < 0:
        raise SystemExit(f"Missing region end: {end}")
    text = text[:start_index] + replacement + text[end_index:]


replace_once(
    "import app.myfinhub.android.core.data.PendingTransactionIntent\n"
    "import app.myfinhub.android.core.data.PendingTransactionSyncState\n",
    "import app.myfinhub.android.core.data.PendingCanonicalMutationIntent\n"
    "import app.myfinhub.android.core.data.PendingMutationKind\n"
    "import app.myfinhub.android.core.data.PendingMutationSyncState\n"
    "import app.myfinhub.android.core.data.compactPendingMutationIntents\n"
    "import app.myfinhub.android.core.data.reconcileSatisfiedPendingMutations\n",
)
replace_once("import app.myfinhub.android.core.data.canonicalEvents\n", "")

replace_once(
    " * The encrypted device cache contains only the last server-accepted canonical document plus a\n"
    " * separate queue of stable-id transaction intents. Offline-created transactions are never folded\n"
    " * into the cached server snapshot. Reconnect always loads the newest server revision first. Only\n"
    " * intents known to have never been sent are replayed automatically; a write that has crossed the\n"
    " * network boundary is marked NEEDS_REVIEW before the attempt so an ambiguous failure can never be\n"
    " * retried blindly after reconnect or process death.\n",
    " * The encrypted device cache contains the last server-accepted canonical document plus a separate\n"
    " * ordered queue of stable canonical mutation intents. Every supported finance mutation can be\n"
    " * applied optimistically offline and survives process death. Reconnect always loads the newest\n"
    " * server revision first. Only intents known never to have been sent replay automatically; an intent\n"
    " * is persisted as NEEDS_REVIEW before crossing the write boundary so ambiguous failures are never\n"
    " * blindly retried.\n",
)

replace_once(
    "    private var lastServerDocument: CanonicalFinanceDocument? = null\n"
    "    private var pendingTransactions: List<PendingTransactionIntent> = emptyList()\n\n"
    "    /** Non-transaction online mutation retained for explicit conflict/failure recovery. */\n"
    "    private var pendingMutation: CanonicalFinanceMutation? = null\n",
    "    private var lastServerDocument: CanonicalFinanceDocument? = null\n"
    "    private var pendingMutations: List<PendingCanonicalMutationIntent> = emptyList()\n",
)

replace_once(
    "                if (reloadWhenOnline || ready?.offline == true || pendingTransactions.any { it.syncState == PendingTransactionSyncState.NEVER_SENT }) {\n",
    "                if (reloadWhenOnline || ready?.offline == true || pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {\n",
)

replace_once(
    "            pendingMutation = null\n"
    "            lastServerDocument = null\n"
    "            pendingTransactions = emptyList()\n",
    "            lastServerDocument = null\n"
    "            pendingMutations = emptyList()\n",
)
replace_once(
    "        pendingMutation = null\n"
    "        lastServerDocument = null\n"
    "        pendingTransactions = emptyList()\n",
    "        lastServerDocument = null\n"
    "        pendingMutations = emptyList()\n",
)
replace_once(
    "            if (canUseServer() && (ready.offline || reloadWhenOnline || pendingTransactions.isNotEmpty())) {\n",
    "            if (canUseServer() && (ready.offline || reloadWhenOnline || pendingMutations.isNotEmpty())) {\n",
)

replace_region(
    "    /** Explicit recovery for either an ambiguous queued transaction or a non-transaction mutation. */\n",
    "    fun discardPendingAndReload() {",
    """    /** Explicit recovery for ambiguous queued work. A fresh server load always happens first. */
    fun retryPendingMutation() {
        if (!canUseServer()) {
            mutableNotices.tryEmit(offlineUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
            return
        }
        if (pendingMutations.isEmpty()) {
            loadFresh(preserveUi = true)
            return
        }
        loadFresh(preserveUi = true, includeNeedsReview = true)
    }

""",
)
replace_region(
    "    fun discardPendingAndReload() {",
    "    fun onHomeAction(action: HomeAction) {",
    """    fun discardPendingAndReload() {
        // Do not silently discard durable offline work. This route now means "reload/reconcile".
        reloadWhenOnline = false
        loadFresh(preserveUi = false)
    }

""",
)

replace_region(
    "    fun onActivityAction(action: ActivityAction) {",
    "    private fun deleteTransaction(transactionId: String) {",
    """    fun onActivityAction(action: ActivityAction) {
        when (action) {
            is ActivityAction.SaveEdit -> {
                val ready = mutableState.value as? FinanceProductState.Ready ?: return
                if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
                applyMutation(
                    EditCanonicalActivity(
                        transactionId = action.id,
                        note = action.note,
                        category = action.category,
                        nowIso = Instant.now().toString(),
                    ),
                )
            }
            is ActivityAction.Delete -> deleteTransaction(action.id)
            else -> updateReady { ready ->
                ready.copy(projection = ready.projection.copy(activityState = reduceActivity(ready.projection.activityState, action)))
            }
        }
    }

""",
)
replace_region(
    "    private fun deleteTransaction(transactionId: String) {",
    "    fun deleteCard(cardId: String) {",
    """    private fun deleteTransaction(transactionId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val id = transactionId.trim()
        if (id.isBlank()) return
        applyMutation(DeleteCanonicalActivity(id, Instant.now().toString()))
    }

""",
)
replace_region(
    "    fun deleteCard(cardId: String) {",
    "    fun onQuickEntryAction(action: QuickEntryAction) {",
    """    fun deleteCard(cardId: String) {
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return
        val normalizedCardId = cardId.trim()
        if (normalizedCardId.isBlank() || ready.projection.document.canonicalCards().none { it.id == normalizedCardId && it.active }) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η κάρτα δεν είναι πλέον διαθέσιμη για διαγραφή.",
                    details = "Ενέργεια: Διαγραφή κάρτας\\nΚατηγορία: INVALID_CARD_STATE",
                    diagnosticCode = "MFH-APP-INVALID_CARD_STATE",
                ),
            )
            return
        }
        applyMutation(
            DeactivateCanonicalCard(
                cardId = normalizedCardId,
                nowIso = Instant.now().toString(),
            ),
        )
    }

""",
)
replace_once(
    "        if (!canUseServer()) {\n"
    "            mutableNotices.tryEmit(offlineMutationNotice(\"Η αλλαγή budget χρειάζεται επαληθευμένη σύνδεση.\"))\n"
    "            return\n"
    "        }\n\n",
    "",
)

replace_region(
    "    private fun loadFresh(\n",
    "    private suspend fun replayPendingTransactions(\n",
    """    private fun loadFresh(
        preserveUi: Boolean,
        includeNeedsReview: Boolean = false,
    ) {
        val session = currentSession ?: return
        if (loadJob?.isActive == true || mutationJob?.isActive == true) return
        val previous = (mutableState.value as? FinanceProductState.Ready)?.projection.takeIf { preserveUi }

        loadJob = viewModelScope.launch {
            val cached = localStore.load(session.userId)
            if (cached != null) {
                lastServerDocument = cached.serverDocument
                pendingMutations = cached.pendingMutations
                mutableLastSuccessfulSync.value = cached.lastSuccessfulSync
            }

            if (!canUseServer()) {
                reloadWhenOnline = true
                if (lastServerDocument != null) {
                    renderLocalState(previous = previous, offline = true)
                } else {
                    mutableState.value = FinanceProductState.Failure(
                        message = "Δεν υπάρχει ακόμη αποθηκευμένο αντίγραφο οικονομικών δεδομένων για χρήση χωρίς σύνδεση.",
                        retryable = true,
                    )
                }
                return@launch
            }

            synchronizePendingFromServer(
                session = session,
                previousProjection = previous,
                includeNeedsReview = includeNeedsReview,
            )
        }
    }

    private suspend fun synchronizePendingFromServer(
        session: AuthSession,
        previousProjection: CanonicalProductProjection?,
        includeNeedsReview: Boolean,
    ) {
        mutableState.value = if (previousProjection == null && lastServerDocument == null) {
            FinanceProductState.Loading
        } else {
            lastServerDocument?.let { document ->
                readyForDocument(
                    document = applyAllPending(document),
                    previous = previousProjection,
                    saving = true,
                    offline = false,
                )
            } ?: FinanceProductState.Loading
        }

        repository.load(session)
        when (val loaded = repository.state.value) {
            is FinanceSyncState.Ready -> {
                lastServerDocument = loaded.envelope.document
                recordSuccessfulSync(loaded.envelope.lastSavedAt)
                reconcileCommittedPending(loaded.envelope.document)
                persistLocalSnapshot()

                val eligible = if (includeNeedsReview) {
                    pendingMutations
                } else {
                    pendingMutations.takeWhile { it.syncState == PendingMutationSyncState.NEVER_SENT }
                }
                if (eligible.isNotEmpty()) {
                    replayPendingMutations(
                        session = session,
                        serverDocument = loaded.envelope.document,
                        previousProjection = previousProjection,
                        eligible = eligible,
                    )
                } else {
                    renderLocalState(previous = previousProjection, offline = false)
                }
            }
            is FinanceSyncState.Error -> {
                if (loaded.failure.kind == ApiFailureKind.NETWORK && lastServerDocument != null) {
                    reloadWhenOnline = true
                    renderLocalState(previous = previousProjection, offline = true)
                    mutableNotices.emit(offlineUserNotice("Ανανέωση οικονομικών δεδομένων"))
                } else {
                    handleLoadFailure(loaded.failure, "Φόρτωση οικονομικών δεδομένων")
                }
            }
            else -> failLoad("Δεν ήταν δυνατή η φόρτωση των οικονομικών δεδομένων.")
        }
    }

""",
)

replace_region(
    "    private suspend fun replayPendingTransactions(\n",
    "    private fun applyMutation(mutation: CanonicalFinanceMutation) {",
    """    private suspend fun replayPendingMutations(
        session: AuthSession,
        serverDocument: CanonicalFinanceDocument,
        previousProjection: CanonicalProductProjection?,
        eligible: List<PendingCanonicalMutationIntent>,
    ) {
        if (!canUseServer()) {
            renderLocalState(previousProjection, offline = true)
            return
        }
        val eligibleIds = eligible.map(PendingCanonicalMutationIntent::intentId).toSet()
        // Persist NEEDS_REVIEW before crossing the network write boundary. Any transport failure
        // after this point is ambiguous and automatic reconnect must not replay these intents.
        pendingMutations = pendingMutations.map { pending ->
            if (pending.intentId in eligibleIds) pending.copy(syncState = PendingMutationSyncState.NEEDS_REVIEW) else pending
        }
        persistLocalSnapshot()

        val candidateDocument = runCatching {
            eligible.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }
        }.getOrElse { error ->
            renderLocalState(previousProjection, offline = false)
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία εκκρεμών αλλαγών",
                    throwable = error,
                    message = "Οι εκκρεμείς αλλαγές διατηρήθηκαν αλλά δεν μπόρεσαν να προετοιμαστούν για συγχρονισμό.",
                ),
            )
            return
        }

        mutableState.value = readyForDocument(
            document = applyAllPending(serverDocument),
            previous = previousProjection,
            saving = true,
            offline = false,
        )
        repository.save(session, candidateDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                val committed = pendingMutations.filter { it.intentId in eligibleIds }
                pendingMutations = pendingMutations.filterNot { it.intentId in eligibleIds }
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                committed.mapNotNull(PendingCanonicalMutationIntent::affectedCardId).distinct().forEach { cardId ->
                    mutableCommittedCardDeletions.emit(cardId)
                }
                reconcileCommittedPending(saved.envelope.document)
                persistLocalSnapshot()
                renderLocalState(previousProjection, offline = false)
            }
            is FinanceSyncState.Conflict -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.REVISION_CONFLICT,
                        "Οι εκκρεμείς αλλαγές διατηρήθηκαν. Φόρτωσε τα νεότερα δεδομένα πριν επιλέξεις νέα επανάληψη.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                persistLocalSnapshot()
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    renderLocalState(
                        previous = previousProjection,
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        issue = FinanceSyncIssue(
                            FinanceSyncIssueKind.SAVE_FAILED,
                            "Οι εκκρεμείς αλλαγές διατηρήθηκαν στη συσκευή. Απαιτείται νέα φόρτωση του server πριν από ρητή επανάληψη.",
                        ),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Συγχρονισμός εκκρεμών αλλαγών"))
                }
            }
            else -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.SAVE_FAILED,
                        "Οι εκκρεμείς αλλαγές διατηρήθηκαν στη συσκευή και χρειάζονται ρητή επανάληψη.",
                    ),
                )
            }
        }
    }

""",
)

replace_region(
    "    private fun applyMutation(mutation: CanonicalFinanceMutation) {",
    "    private suspend fun saveMutation(\n",
    """    private fun applyMutation(mutation: CanonicalFinanceMutation) {
        val session = currentSession ?: return
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return

        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                val queued = queueLocalMutation(
                    session = session,
                    mutation = mutation,
                    previousProjection = ready.projection,
                )
                if (queued && canUseServer()) {
                    synchronizePendingFromServer(
                        session = session,
                        previousProjection = ready.projection,
                        includeNeedsReview = false,
                    )
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    private suspend fun queueLocalMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        previousProjection: CanonicalProductProjection,
    ): Boolean {
        val serverDocument = lastServerDocument ?: localStore.load(session.userId)?.serverDocument
        if (serverDocument == null) {
            mutableNotices.emit(
                UserNotice(
                    message = "Δεν υπάρχει ασφαλές τοπικό αντίγραφο για αυτή την αλλαγή χωρίς σύνδεση.",
                    details = "Ενέργεια: Αποθήκευση offline αλλαγής\\nΚατηγορία: OFFLINE_CACHE_MISSING",
                    diagnosticCode = "MFH-OFFLINE-CACHE-MISSING",
                ),
            )
            return false
        }
        lastServerDocument = serverDocument

        val currentOptimisticDocument = runCatching { applyAllPending(serverDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Ανάκτηση τοπικών αλλαγών",
                    throwable = error,
                    message = "Οι υπάρχουσες τοπικές αλλαγές δεν μπόρεσαν να εφαρμοστούν με ασφάλεια.",
                ),
            )
            return false
        }
        runCatching { mutation.apply(currentOptimisticDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εφαρμοστεί με ασφάλεια.",
                ),
            )
            return false
        }

        val intent = PendingCanonicalMutationIntent.fromMutation(
            mutation = mutation,
            intentId = "mutation-android-${UUID.randomUUID()}",
        )
        val previousQueue = pendingMutations
        pendingMutations = compactPendingMutationIntents(pendingMutations, intent)
        val optimisticDocument = runCatching { applyAllPending(serverDocument) }.getOrElse { error ->
            pendingMutations = previousQueue
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση τοπικής αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return false
        }
        persistLocalSnapshot()

        var projection = runCatching {
            projectCanonicalProduct(optimisticDocument, LocalDate.now(), previousProjection)
        }.getOrElse { error ->
            pendingMutations = previousQueue
            persistLocalSnapshot()
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση τοπικής αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή ακυρώθηκε επειδή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return false
        }
        if (mutation is AppendCanonicalEvent) {
            projection = projection.copy(
                quickEntryState = projection.quickEntryState.copy(
                    persisted = false,
                    dirty = false,
                    validationMessage = null,
                ),
            )
        }
        mutableState.value = readyForProjection(
            projection = projection,
            saving = false,
            offline = !canUseServer(),
        )

        if (!canUseServer()) {
            reloadWhenOnline = true
            mutableNotices.emit(
                UserNotice(
                    message = "Η αλλαγή αποθηκεύτηκε στη συσκευή και περιμένει συγχρονισμό.",
                    details = "Ενέργεια: Αποθήκευση offline αλλαγής\\nΚατηγορία: PENDING_SYNC\\nΘα φορτωθεί πρώτα η νεότερη κατάσταση από τον server πριν σταλεί η αλλαγή.",
                    diagnosticCode = "MFH-OFFLINE-PENDING-SYNC",
                ),
            )
        }
        return true
    }

""",
)

replace_region(
    "    private suspend fun saveMutation(\n",
    "    private suspend fun queueAttemptedTransaction(mutation: AppendCanonicalEvent) {",
    "",
)
replace_region(
    "    private suspend fun queueAttemptedTransaction(mutation: AppendCanonicalEvent) {",
    "    private suspend fun reconcileCommittedPending(serverDocument: CanonicalFinanceDocument) {",
    "",
)

replace_region(
    "    private suspend fun reconcileCommittedPending(serverDocument: CanonicalFinanceDocument) {",
    "    private suspend fun handleLoadFailure(failure: ApiResult.Failure, operation: String) {",
    """    private suspend fun reconcileCommittedPending(serverDocument: CanonicalFinanceDocument) {
        if (pendingMutations.isEmpty()) return
        val remaining = reconcileSatisfiedPendingMutations(serverDocument, pendingMutations)
        val remainingIds = remaining.map(PendingCanonicalMutationIntent::intentId).toSet()
        val committed = pendingMutations.filterNot { it.intentId in remainingIds }
        pendingMutations = remaining
        committed.mapNotNull(PendingCanonicalMutationIntent::affectedCardId).distinct().forEach { cardId ->
            mutableCommittedCardDeletions.emit(cardId)
        }
    }

    private suspend fun renderLocalState(
        previous: CanonicalProductProjection?,
        offline: Boolean,
        issue: FinanceSyncIssue? = reviewIssueOrNull(),
    ) {
        val serverDocument = lastServerDocument ?: return
        mutableState.value = readyForDocument(
            document = applyAllPending(serverDocument),
            previous = previous,
            saving = false,
            offline = offline,
            issue = issue,
        )
    }

    private fun readyForDocument(
        document: CanonicalFinanceDocument,
        previous: CanonicalProductProjection?,
        saving: Boolean,
        offline: Boolean,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready {
        val projection = projectCanonicalProduct(document, LocalDate.now(), previous)
        return FinanceProductState.Ready(
            projection = markPendingTransactions(projection),
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = pendingTransactionIds().size,
            pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
        )
    }

    private fun readyForProjection(
        projection: CanonicalProductProjection,
        saving: Boolean = false,
        offline: Boolean = false,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready = FinanceProductState.Ready(
        projection = markPendingTransactions(projection),
        saving = saving,
        issue = issue,
        offline = offline,
        pendingTransactionCount = pendingTransactionIds().size,
        pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
    )

    private fun applyAllPending(serverDocument: CanonicalFinanceDocument): CanonicalFinanceDocument =
        pendingMutations.fold(serverDocument) { document, pending -> pending.asMutation().apply(document) }

    private fun markPendingTransactions(projection: CanonicalProductProjection): CanonicalProductProjection {
        val ids = pendingTransactionIds()
        if (ids.isEmpty()) return projection
        return projection.copy(
            activityState = projection.activityState.copy(
                items = projection.activityState.items.map { item ->
                    if (item.id in ids) item.copy(pendingSync = true) else item
                },
            ),
        )
    }

    private fun pendingTransactionIds(): Set<String> =
        pendingMutations.mapNotNull(PendingCanonicalMutationIntent::affectedTransactionId)
            .filter(String::isNotBlank)
            .toSet()

    private fun reviewIssueOrNull(): FinanceSyncIssue? =
        if (pendingMutations.any { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }) {
            FinanceSyncIssue(
                FinanceSyncIssueKind.SAVE_FAILED,
                "Υπάρχουν αλλαγές που μπορεί να έχουν φτάσει στον server. Απαιτείται νέα φόρτωση πριν από ρητή επανάληψη.",
            )
        } else {
            null
        }

    private suspend fun persistLocalSnapshot() {
        val session = currentSession ?: return
        val serverDocument = lastServerDocument ?: return
        localStore.save(
            FinanceLocalSnapshot(
                userId = session.userId,
                serverDocument = serverDocument,
                pendingMutations = pendingMutations,
                lastSuccessfulSync = mutableLastSuccessfulSync.value,
            ),
        )
    }

""",
)

replace_once(
    "        message = message,\n"
    "        details = \"Ενέργεια: Offline αλλαγή\\nΚατηγορία: CONNECTION_REQUIRED\\nΟι ήδη φορτωμένες οικονομικές πληροφορίες παραμένουν διαθέσιμες.\",\n",
    "        message = message,\n"
    "        details = \"Ενέργεια: Offline συγχρονισμός\\nΚατηγορία: CONNECTION_REQUIRED\\nΟι τοπικές αλλαγές παραμένουν κρυπτογραφημένες στη συσκευή.\",\n",
)

# No transaction-only queue symbols may survive the migration.
for forbidden in ("pendingTransactions", "PendingTransactionIntent", "PendingTransactionSyncState", "queueOfflineTransaction", "queueAttemptedTransaction", "saveMutation(", "pendingMutation"):
    if forbidden in text:
        raise SystemExit(f"Transaction-only/volatile queue symbol survived: {forbidden}")

path.write_text(text)
