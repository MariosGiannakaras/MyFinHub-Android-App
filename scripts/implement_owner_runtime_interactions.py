#!/usr/bin/env python3
from pathlib import Path
import json


def require_replace(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"Missing patch anchor: {label}")
    return text.replace(old, new, 1)


# Durable owner contract.
plan_path = Path("docs/RC7_S24_OWNER_UI_UX_CORRECTION_PLAN.md")
plan = plan_path.read_text(encoding="utf-8")
marker = "## Ordered implementation slices\n"
contract = """## Runtime delivery and motion contract — owner requirement 2026-09-09

These requirements apply across every correction slice and are not optional polish:

- When the server is reachable and the canonical repository is ready, a finance mutation must cross the network write boundary immediately using the current server revision. Do **not** hold online writes behind an Undo grace timer or an offline queue delay. The target behavior is the fast database update path that existed before general offline mutation support.
- The durable pending queue, `Προς συγχρονισμό` state and user Undo window belong to **offline/local-only mutations**. They remain encrypted on-device and reconcile from a fresh server revision when connectivity returns.
- If an online write suffers an ambiguous transport/server failure, retain the crash-safe `NEEDS_REVIEW` reconciliation safety contract; this is an error-recovery state, not an intentional Undo delay. Never blindly replay an ambiguous write.
- Online success must clear transient pending state immediately after server acknowledgement and refresh the in-memory/cached canonical projection without an unnecessary pre-save reload round trip.
- Add purposeful motion throughout the product: short state transitions, bottom-sheet motion, content reveal/collapse, press feedback and selection feedback. Motion must explain state/causality, not delay actions.
- Micro-animations and micro-interactions should normally stay in the ~120–240 ms range, respect platform animation scaling/accessibility behavior, preserve touch targets, and never block persistence/network work.
- Destructive confirmation remains explicit. Animation must never become the only cue for a finance-critical state change.

"""
if contract not in plan:
    if marker not in plan:
        raise SystemExit("Missing plan insertion marker")
    plan = plan.replace(marker, contract + marker, 1)
plan = plan.replace(
    "- Offline success should read as successful local save with pending sync, not as a warning: e.g. `Αποθηκεύτηκε` + `Θα συγχρονιστεί όταν υπάρχει σύνδεση` + `Αναίρεση`.\n- Preserve the encrypted local enqueue/reconcile/undo contract exactly.\n",
    "- Offline success should read as successful local save with pending sync, not as a warning: e.g. `Αποθηκεύτηκε` + `Θα συγχρονιστεί όταν υπάρχει σύνδεση` + `Αναίρεση`.\n- Online mutations must save to the server immediately with no Undo grace delay; offline/local-only mutations retain durable pending + Undo behavior.\n- Add functional motion to the Quick Entry flow: animated note reveal/collapse, animated mobile sheets and lightweight press/selection feedback with no persistence delay.\n- Preserve the encrypted offline enqueue/reconcile safety contract, while keeping the normal online path direct and fast.\n",
)
plan_path.write_text(plan, encoding="utf-8")

# Canonical tracking update. Generated files are rendered by the workflow.
state_path = Path("tracking/android-project-state.json")
state = json.loads(state_path.read_text(encoding="utf-8"))
state["updated_at"] = "2026-09-09"
workstream = state["active_workstream"]
workstream["status"] = "rc7_owner_ui_correction_slice_a_fast_online_delivery_and_motion"
workstream["summary"] = (
    "Production-signed `1.0.0-rc7` / versionCode `10006` remains the installed technical baseline on the authorized Samsung Galaxy S24 Ultra, but is not owner-accepted as final. "
    "The owner physical correction pass is active on `android/rc7-owner-ui-ux-correction-pass` / PR #100. Slice A has started with mobile bottom-sheet selection and Greek date corrections. "
    "The owner additionally requires the pre-offline fast online mutation behavior: when connected, canonical writes must be sent to the database immediately with no Undo grace delay; durable pending + Undo is offline-only. "
    "Purposeful animations, micro-animations and micro-interactions are mandatory across the correction slices. The durable implementation specification remains `docs/RC7_S24_OWNER_UI_UX_CORRECTION_PLAN.md`. Overall progress remains 4/6."
)
workstream["next"][0] = (
    "Complete Slice A on `android/rc7-owner-ui-ux-correction-pass`: keep the validated mobile bottom-sheet/Greek-date corrections, restore immediate online canonical writes with no Undo grace timer, keep durable pending + Undo only for offline mutations, add shared motion/micro-interaction primitives and Quick Entry motion, then run unit/compile/UI/screenshot/S24-target gates and inspect real renders."
)
for requirement in [
    "Online finance mutations must use the immediate server write path when connectivity and repository state allow it; no intentional Undo/grace delay may sit in front of an online database update. Durable pending + Undo is for offline/local-only mutations.",
    "Purposeful animations, micro-animations and micro-interactions are required across the owner correction pass, but motion must remain short, accessible, non-blocking and must never delay finance persistence or replace explicit destructive confirmation.",
]:
    if requirement not in state["constraints"]:
        state["constraints"].append(requirement)
redesign = state["current_redesign_pass"]
redesign["checkpoint"] = "slice_a_picker_render_validated_fast_online_delivery_and_motion_implementation"
redesign["completion_rule"] = (
    "Each slice requires real Compose render inspection plus relevant hosted gates and a canonical tracking update before advancing. "
    "Online writes must remain immediate; offline-only pending/undo and the shared motion contract must remain regression-protected. "
    "A higher production candidate and explicit S24 owner acceptance are required before final/stable promotion."
)
state_path.write_text(json.dumps(state, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

# Explicit/testable mutation delivery policy.
Path("app/src/main/java/app/myfinhub/android/app/MutationDeliveryPolicy.kt").write_text(
    """package app.myfinhub.android.app

/**
 * Owner-approved mutation delivery policy. Normal connected writes stay on the direct server path;
 * the durable local queue and Undo semantics exist for offline work or ordered reconciliation.
 */
internal enum class MutationDeliveryMode {
    IMMEDIATE_SERVER,
    DURABLE_OFFLINE_QUEUE,
    RECONCILE_PENDING_FIRST,
}

internal fun mutationDeliveryMode(
    serverAvailable: Boolean,
    repositoryReady: Boolean,
    hasPendingMutations: Boolean,
): MutationDeliveryMode = when {
    !serverAvailable -> MutationDeliveryMode.DURABLE_OFFLINE_QUEUE
    hasPendingMutations || !repositoryReady -> MutationDeliveryMode.RECONCILE_PENDING_FIRST
    else -> MutationDeliveryMode.IMMEDIATE_SERVER
}
""",
    encoding="utf-8",
)
Path("app/src/test/java/app/myfinhub/android/app/MutationDeliveryPolicyTest.kt").write_text(
    """package app.myfinhub.android.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MutationDeliveryPolicyTest {
    @Test
    fun connectedReadyState_usesImmediateServerPath() {
        assertEquals(
            MutationDeliveryMode.IMMEDIATE_SERVER,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = true, hasPendingMutations = false),
        )
    }

    @Test
    fun offlineState_usesDurableQueueWithUndoSemantics() {
        assertEquals(
            MutationDeliveryMode.DURABLE_OFFLINE_QUEUE,
            mutationDeliveryMode(serverAvailable = false, repositoryReady = true, hasPendingMutations = false),
        )
    }

    @Test
    fun connectedStateWithPendingWork_reconcilesBeforeNewDirectWrites() {
        assertEquals(
            MutationDeliveryMode.RECONCILE_PENDING_FIRST,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = true, hasPendingMutations = true),
        )
        assertEquals(
            MutationDeliveryMode.RECONCILE_PENDING_FIRST,
            mutationDeliveryMode(serverAvailable = true, repositoryReady = false, hasPendingMutations = false),
        )
    }
}
""",
    encoding="utf-8",
)

# Finance controller: direct connected write; offline-only durable pending + Undo.
vm_path = Path("app/src/main/java/app/myfinhub/android/app/FinanceProductViewModel.kt")
vm = vm_path.read_text(encoding="utf-8")
vm = vm.replace("import kotlinx.coroutines.delay\n", "")
vm = vm.replace("    private var autoSyncJob: Job? = null\n", "")

vm = require_replace(
    vm,
    """    fun undoLatestPendingMutation() {
        val latest = pendingMutations.lastOrNull() ?: return
""",
    """    fun undoLatestPendingMutation() {
        if (canUseServer()) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Η αναίρεση εκκρεμούς αλλαγής είναι διαθέσιμη μόνο για αλλαγές που αποθηκεύτηκαν offline.",
                    details = "Ενέργεια: Αναίρεση αλλαγής\\nΚατηγορία: OFFLINE_ONLY_UNDO\\nΟι online αλλαγές αποστέλλονται άμεσα στον server.",
                    diagnosticCode = "MFH-OFFLINE-UNDO-ONLY",
                ),
            )
            return
        }
        val latest = pendingMutations.lastOrNull() ?: return
""",
    "offline-only undo guard",
)
vm = vm.replace("""        autoSyncJob?.cancel()
        autoSyncJob = null
        viewModelScope.launch {
""", """        viewModelScope.launch {
""", 1)
vm = vm.replace(
    """            if (canUseServer() && pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                scheduleAutoSync()
            }
""",
    "",
    1,
)

old_apply = """    private fun applyMutation(mutation: CanonicalFinanceMutation) {
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
                    scheduleAutoSync()
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    private fun scheduleAutoSync() {
        autoSyncJob?.cancel()
        autoSyncJob = viewModelScope.launch {
            delay(UNDO_GRACE_PERIOD_MILLIS)
            if (canUseServer() && currentSession != null && pendingMutations.any { it.syncState == PendingMutationSyncState.NEVER_SENT }) {
                loadFresh(preserveUi = true)
            }
        }
    }

"""
new_apply = """    private fun applyMutation(mutation: CanonicalFinanceMutation) {
        val session = currentSession ?: return
        val ready = mutableState.value as? FinanceProductState.Ready ?: return
        if (ready.saving || ready.issue != null || mutationLaunchInFlight) return

        mutationLaunchInFlight = true
        mutationJob = viewModelScope.launch {
            try {
                when (
                    mutationDeliveryMode(
                        serverAvailable = canUseServer(),
                        repositoryReady = repository.state.value is FinanceSyncState.Ready,
                        hasPendingMutations = pendingMutations.isNotEmpty(),
                    )
                ) {
                    MutationDeliveryMode.IMMEDIATE_SERVER -> commitOnlineMutation(
                        session = session,
                        mutation = mutation,
                        previousProjection = ready.projection,
                    )
                    MutationDeliveryMode.DURABLE_OFFLINE_QUEUE -> queueLocalMutation(
                        session = session,
                        mutation = mutation,
                        previousProjection = ready.projection,
                    )
                    MutationDeliveryMode.RECONCILE_PENDING_FIRST -> {
                        val queued = queueLocalMutation(
                            session = session,
                            mutation = mutation,
                            previousProjection = ready.projection,
                        )
                        if (queued && canUseServer()) {
                            synchronizePendingFromServer(
                                session = session,
                                previousProjection = (mutableState.value as? FinanceProductState.Ready)?.projection
                                    ?: ready.projection,
                                includeNeedsReview = false,
                            )
                        }
                    }
                }
            } finally {
                mutationLaunchInFlight = false
            }
        }
    }

    /**
     * Fast connected path: one canonical save using the current server revision. The temporary
     * NEEDS_REVIEW intent is crash-safety only; it is hidden during a healthy online request and
     * removed immediately on acknowledgement. No Undo grace timer or pre-save reload is involved.
     */
    private suspend fun commitOnlineMutation(
        session: AuthSession,
        mutation: CanonicalFinanceMutation,
        previousProjection: CanonicalProductProjection,
    ) {
        val serverDocument = lastServerDocument ?: previousProjection.document
        lastServerDocument = serverDocument
        val candidateDocument = runCatching { mutation.apply(serverDocument) }.getOrElse { error ->
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προετοιμασία online αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εφαρμοστεί με ασφάλεια.",
                ),
            )
            return
        }

        val inFlightIntent = PendingCanonicalMutationIntent.fromMutation(
            mutation = mutation,
            intentId = "mutation-online-${UUID.randomUUID()}",
            syncState = PendingMutationSyncState.NEEDS_REVIEW,
        )
        pendingMutations = pendingMutations + inFlightIntent
        persistLocalSnapshot()

        val optimisticProjection = runCatching {
            projectCanonicalProduct(candidateDocument, LocalDate.now(), previousProjection)
        }.getOrElse { error ->
            pendingMutations = pendingMutations.filterNot { it.intentId == inFlightIntent.intentId }
            persistLocalSnapshot()
            mutableNotices.emit(
                unexpectedUserNotice(
                    operation = "Προεπισκόπηση online αλλαγής",
                    throwable = error,
                    message = "Η αλλαγή δεν μπόρεσε να εμφανιστεί με ασφάλεια.",
                ),
            )
            return
        }
        mutableState.value = readyForProjection(
            projection = optimisticProjection,
            saving = true,
            offline = false,
        )

        repository.save(session, candidateDocument)
        when (val saved = repository.state.value) {
            is FinanceSyncState.Ready -> {
                pendingMutations = pendingMutations.filterNot { it.intentId == inFlightIntent.intentId }
                lastServerDocument = saved.envelope.document
                recordSuccessfulSync(saved.envelope.lastSavedAt)
                if (mutation is DeactivateCanonicalCard) {
                    mutableCommittedCardDeletions.emit(mutation.cardId)
                }
                persistLocalSnapshot()

                var committedProjection = projectCanonicalProduct(
                    saved.envelope.document,
                    LocalDate.now(),
                    previousProjection,
                )
                if (mutation is AppendCanonicalEvent) {
                    committedProjection = committedProjection.copy(
                        quickEntryState = committedProjection.quickEntryState.copy(
                            persisted = true,
                            pendingSync = false,
                            dirty = false,
                            validationMessage = null,
                        ),
                    )
                }
                mutableState.value = readyForProjection(
                    projection = committedProjection,
                    saving = false,
                    offline = false,
                )
            }
            is FinanceSyncState.Conflict -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.REVISION_CONFLICT,
                        "Η αλλαγή διατηρήθηκε με ασφάλεια. Φόρτωσε τα νεότερα δεδομένα πριν από ρητή επανάληψη.",
                    ),
                )
            }
            is FinanceSyncState.Error -> {
                persistLocalSnapshot()
                if (saved.failure.kind.isAuthRejection()) {
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση αλλαγής"))
                    repository.clear()
                    mutableState.value = FinanceProductState.AuthRejected
                } else {
                    if (saved.failure.kind == ApiFailureKind.NETWORK) reloadWhenOnline = true
                    renderLocalState(
                        previous = previousProjection,
                        offline = saved.failure.kind == ApiFailureKind.NETWORK,
                        issue = FinanceSyncIssue(
                            if (saved.failure.kind == ApiFailureKind.NETWORK) {
                                FinanceSyncIssueKind.WAITING_FOR_NETWORK
                            } else {
                                FinanceSyncIssueKind.SAVE_FAILED
                            },
                            "Η αλλαγή διατηρήθηκε με ασφάλεια και θα συμφωνηθεί με την τρέχουσα κατάσταση του server πριν από οποιαδήποτε επανάληψη.",
                        ),
                    )
                    mutableNotices.emit(saved.failure.toUserNotice("Αποθήκευση αλλαγής"))
                }
            }
            else -> {
                persistLocalSnapshot()
                renderLocalState(
                    previous = previousProjection,
                    offline = false,
                    issue = FinanceSyncIssue(
                        FinanceSyncIssueKind.SAVE_FAILED,
                        "Η αλλαγή διατηρήθηκε με ασφάλεια και χρειάζεται συμφωνία με τον server.",
                    ),
                )
            }
        }
    }

"""
vm = require_replace(vm, old_apply, new_apply, "online grace path")

old_ready_doc = """        val projection = projectCanonicalProduct(document, LocalDate.now(), previous)
        return FinanceProductState.Ready(
            projection = markPendingTransactions(projection),
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = pendingTransactionIds().size,
            pendingChangeCount = pendingMutations.size,
            latestPendingChange = latestPendingChangeUi(),
            pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
        )
"""
new_ready_doc = """        val projection = projectCanonicalProduct(document, LocalDate.now(), previous)
        val exposePending = offline || issue != null
        return FinanceProductState.Ready(
            projection = if (exposePending) markPendingTransactions(projection) else projection,
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = if (exposePending) pendingTransactionIds().size else 0,
            pendingChangeCount = if (exposePending) pendingMutations.size else 0,
            latestPendingChange = if (exposePending) latestPendingChangeUi() else null,
            pendingReviewCount = if (exposePending) {
                pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }
            } else {
                0
            },
        )
"""
vm = require_replace(vm, old_ready_doc, new_ready_doc, "readyForDocument pending exposure")

old_ready_projection = """    private fun readyForProjection(
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
        pendingChangeCount = pendingMutations.size,
        latestPendingChange = latestPendingChangeUi(),
        pendingReviewCount = pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW },
    )
"""
new_ready_projection = """    private fun readyForProjection(
        projection: CanonicalProductProjection,
        saving: Boolean = false,
        offline: Boolean = false,
        issue: FinanceSyncIssue? = null,
    ): FinanceProductState.Ready {
        val exposePending = offline || issue != null
        return FinanceProductState.Ready(
            projection = if (exposePending) markPendingTransactions(projection) else projection,
            saving = saving,
            issue = issue,
            offline = offline,
            pendingTransactionCount = if (exposePending) pendingTransactionIds().size else 0,
            pendingChangeCount = if (exposePending) pendingMutations.size else 0,
            latestPendingChange = if (exposePending) latestPendingChangeUi() else null,
            pendingReviewCount = if (exposePending) {
                pendingMutations.count { it.syncState == PendingMutationSyncState.NEEDS_REVIEW }
            } else {
                0
            },
        )
    }
"""
vm = require_replace(vm, old_ready_projection, new_ready_projection, "readyForProjection pending exposure")
vm = vm.replace(
    "            canUndo = pending.syncState == PendingMutationSyncState.NEVER_SENT,\n",
    "            canUndo = !canUseServer() && pending.syncState == PendingMutationSyncState.NEVER_SENT,\n",
    1,
)
vm = vm.replace("        autoSyncJob?.cancel()\n", "")
vm = vm.replace("        autoSyncJob = null\n", "")
vm = vm.replace(
    "\n    private companion object {\n        const val UNDO_GRACE_PERIOD_MILLIS = 5_000L\n    }\n",
    "\n",
)
vm_path.write_text(vm, encoding="utf-8")

# Shared motion primitives.
Path("app/src/main/java/app/myfinhub/android/designsystem/MyFinHubMotion.kt").write_text(
    """package app.myfinhub.android.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

object MyFinHubMotion {
    const val QuickDurationMillis = 120
    const val StandardDurationMillis = 180
    const val EmphasizedDurationMillis = 240
    const val PressedScale = 0.985f
}

/** Lightweight tactile feedback. Compose animation timing follows the platform duration scale. */
@Composable
fun Modifier.myFinHubPressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = MyFinHubMotion.PressedScale,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(durationMillis = MyFinHubMotion.QuickDurationMillis),
        label = "MyFinHub press feedback",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
""",
    encoding="utf-8",
)

components_path = Path("app/src/main/java/app/myfinhub/android/designsystem/MyFinHubComponents.kt")
components = components_path.read_text(encoding="utf-8")
components = components.replace(
    "import androidx.compose.foundation.combinedClickable\n",
    "import androidx.compose.foundation.combinedClickable\nimport androidx.compose.foundation.interaction.MutableInteractionSource\n",
    1,
)
components = components.replace(
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\n",
    1,
)
components = require_replace(
    components,
    """fun MyFinHubPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = MyFinHubIcons.Add,
) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
        enabled = enabled,
""",
    """fun MyFinHubPrimaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = MyFinHubIcons.Add,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .myFinHubPressScale(interactionSource)
            .heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
        enabled = enabled,
        interactionSource = interactionSource,
""",
    "primary press feedback",
)
components = require_replace(
    components,
    """fun MyFinHubOutlinedAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
        enabled = enabled,
""",
    """fun MyFinHubOutlinedAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .myFinHubPressScale(interactionSource)
            .heightIn(min = MyFinHubDesignMetrics.primaryActionMinHeight),
        enabled = enabled,
        interactionSource = interactionSource,
""",
    "outlined press feedback",
)
components_path.write_text(components, encoding="utf-8")

forms_path = Path("app/src/main/java/app/myfinhub/android/designsystem/MyFinHubFormComponents.kt")
forms = forms_path.read_text(encoding="utf-8")
forms = forms.replace(
    "import androidx.compose.foundation.layout.Arrangement\n",
    "import androidx.compose.foundation.interaction.MutableInteractionSource\nimport androidx.compose.foundation.layout.Arrangement\n",
    1,
)
forms = forms.replace(
    "import androidx.compose.runtime.Composable\n",
    "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.remember\n",
    1,
)
forms = require_replace(
    forms,
    """fun MyFinHubSelectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val semanticsModifier = if (errorMessage == null) modifier else {
        modifier.semantics { error(errorMessage) }
    }
""",
    """fun MyFinHubSelectorButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errorMessage: String? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val semanticsModifier = if (errorMessage == null) modifier else {
        modifier.semantics { error(errorMessage) }
    }
""",
    "selector interaction source",
)
forms = require_replace(
    forms,
    """        OutlinedButton(
            onClick = onClick,
            modifier = semanticsModifier
                .fillMaxWidth()
                .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight),
            enabled = enabled,
            shape = MaterialTheme.shapes.extraSmall,
            content = content,
        )
""",
    """        OutlinedButton(
            onClick = onClick,
            modifier = semanticsModifier
                .myFinHubPressScale(interactionSource)
                .fillMaxWidth()
                .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight),
            enabled = enabled,
            interactionSource = interactionSource,
            shape = MaterialTheme.shapes.extraSmall,
            content = content,
        )
""",
    "selector press feedback",
)
forms_path.write_text(forms, encoding="utf-8")

# Quick Entry motion and online-success behavior.
quick_path = Path("app/src/main/java/app/myfinhub/android/feature/quickentry/ProductionQuickEntryScreen.kt")
quick = quick_path.read_text(encoding="utf-8")
quick = quick.replace(
    "import androidx.compose.foundation.clickable\n",
    """import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
""",
    1,
)
quick = quick.replace(
    "import app.myfinhub.android.designsystem.MyFinHubIcons\n",
    "import app.myfinhub.android.designsystem.MyFinHubIcons\nimport app.myfinhub.android.designsystem.MyFinHubMotion\n",
    1,
)
quick = quick.replace('Locale("el", "GR")', 'Locale.forLanguageTag("el-GR")')
quick = require_replace(
    quick,
    """    // Local encrypted enqueue is the successful mobile form submission boundary. Sync/Undo remains
    // visible centrally, so keeping the form open after a safe enqueue only creates a dead-end screen.
    LaunchedEffect(savedLocally) {
        if (savedLocally) onBack()
    }
""",
    """    // Connected saves close only after server acknowledgement. Offline saves close after the durable
    // encrypted local enqueue; only that offline path owns pending-sync/Undo semantics.
    LaunchedEffect(state.persisted, savedLocally) {
        if (state.persisted || savedLocally) onBack()
    }
""",
    "Quick Entry success boundary",
)
quick = require_replace(
    quick,
    """                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
""",
    """                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = tween(durationMillis = MyFinHubMotion.StandardDurationMillis),
                    ),
                contentPadding = PaddingValues(
""",
    "Quick Entry hero content animation",
)
quick = require_replace(
    quick,
    """            if (noteExpanded || state.note.isNotBlank()) {
                MyFinHubOutlinedField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    label = "Σημείωση · προαιρετική",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
""",
    """            AnimatedVisibility(
                visible = noteExpanded || state.note.isNotBlank(),
                enter = fadeIn(tween(MyFinHubMotion.QuickDurationMillis)) +
                    expandVertically(tween(MyFinHubMotion.StandardDurationMillis)),
                exit = fadeOut(tween(MyFinHubMotion.QuickDurationMillis)) +
                    shrinkVertically(tween(MyFinHubMotion.StandardDurationMillis)),
            ) {
                MyFinHubOutlinedField(
                    value = state.note,
                    onValueChange = { onAction(QuickEntryAction.NoteChanged(it)) },
                    label = "Σημείωση · προαιρετική",
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
""",
    "Quick Entry note reveal animation",
)
quick_path.write_text(quick, encoding="utf-8")

# This deterministic helper is intentionally one-shot; the workflow will remove it in a later cleanup commit.
