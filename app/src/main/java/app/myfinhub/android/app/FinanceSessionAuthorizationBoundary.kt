package app.myfinhub.android.app

import app.myfinhub.android.core.auth.AuthSession

/**
 * An active GET/POST belongs to one owner and one bearer authorization.
 * A refresh-token update alone does not change the identity used by the API.
 */
internal fun financeSessionAuthorizationChanged(
    previous: AuthSession?,
    incoming: AuthSession,
): Boolean = previous != null && (
    previous.userId != incoming.userId ||
        previous.accessToken != incoming.accessToken ||
        previous.assuranceLevel != incoming.assuranceLevel
)

/** A same-bearer auth validation failure can downgrade server authority even when
 * connectivity is still VALIDATED. An owner/bearer/AAL change has its separate
 * reset path, so do not republish that prior owner's projection here.
 */
internal fun financeValidatedOnlineAuthorityRevoked(
    previous: AuthSession?,
    incoming: AuthSession,
    previousAllowsOnline: Boolean,
    incomingAllowsOnline: Boolean,
): Boolean = previous != null && previousAllowsOnline && !incomingAllowsOnline &&
    !financeSessionAuthorizationChanged(previous, incoming)

/** Offline auth and validated-online auth are distinct presentation authorities.
 * A matched bearer alone is insufficient during the pre-attachment Compose frame.
 */
internal fun financePresentationMatchesAuthority(
    attached: AuthSession?,
    expected: AuthSession?,
    attachedAllowsOnline: Boolean,
    expectedAllowsOnline: Boolean,
): Boolean = attachedAllowsOnline == expectedAllowsOnline &&
    app.myfinhub.android.core.auth.matchesSensitiveUiSession(attached, expected)

/**
 * Root Compose can observe an authenticated owner before the side effect that
 * rebinds ViewModels. An older Ready/AuthRejected must not be rendered or used
 * for cross-session decisions during that frame.
 */
internal fun visibleFinanceStateForSession(
    authorized: Boolean,
    observed: FinanceProductState,
): FinanceProductState = if (authorized) observed else FinanceProductState.Loading

internal fun visibleCardSecretsForSession(
    authorized: Boolean,
    observed: app.myfinhub.android.feature.money.CardSecretUiState,
): app.myfinhub.android.feature.money.CardSecretUiState =
    if (authorized) observed else app.myfinhub.android.feature.money.CardSecretUiState.Hidden()

internal fun visibleCardCleanupForSession(
    authorized: Boolean,
    observed: app.myfinhub.android.feature.money.CardSecretCleanupUiState,
): app.myfinhub.android.feature.money.CardSecretCleanupUiState =
    if (authorized) observed else app.myfinhub.android.feature.money.CardSecretCleanupUiState.Idle

/**
 * Root pre-attach masking cannot protect the next frame if attachment advances
 * currentSession while a previous Ready/Failure/AuthRejected remains in StateFlow.
 * Refresh-token-only changes retain the same authority. Durable offline intents
 * are deliberately unaffected by this presentation-only transition.
 */
internal fun financePresentationAfterAuthorizationChange(
    previous: AuthSession?,
    incoming: AuthSession,
    observed: FinanceProductState,
): FinanceProductState = if (financeSessionAuthorizationChanged(previous, incoming)) {
    FinanceProductState.Idle
} else observed

/** Per-session server metadata is only valid for one owner/bearer/AAL and
 * one validated online authority. Cached finance data is a separate contract.
 */
internal fun financeServerMetadataAuthorityChanged(
    previous: AuthSession?,
    incoming: AuthSession,
    previousOnline: Boolean,
    incomingOnline: Boolean,
): Boolean = previous != null && (
    financeSessionAuthorizationChanged(previous, incoming) ||
        previousOnline != incomingOnline
)

/** Session-scoped server metadata, device lists and revocation callbacks must
 * never publish after an owner, access bearer, AAL or offline authority change.
 */
internal fun sessionScopedFinanceCallbackAuthorized(
    attached: AuthSession?,
    expected: AuthSession,
    automaticSyncAllowed: Boolean,
): Boolean = automaticSyncAllowed && app.myfinhub.android.core.auth.matchesSensitiveUiSession(attached, expected)

/** A validated auth token is not enough to keep previously revealed PAN/CVV on
 * screen after physical connectivity is lost or becomes unvalidated. UNKNOWN
 * is fail-closed. Offline finance cache has a separate presentation policy.
 */
internal fun cardVaultServerAccessAllowed(
    authState: app.myfinhub.android.feature.auth.AuthShellUiState,
    networkStatus: app.myfinhub.android.core.network.NetworkStatus,
): Boolean = (authState as? app.myfinhub.android.feature.auth.AuthShellUiState.Ready)?.offline == false &&
    networkStatus == app.myfinhub.android.core.network.NetworkStatus.ONLINE

/** Offline-ready permits encrypted finance cache, not previously fetched card PAN/CVV. */
internal fun cardVaultPresentationAllowed(
    authState: app.myfinhub.android.feature.auth.AuthShellUiState,
    attachedSessionMatches: Boolean,
): Boolean = attachedSessionMatches &&
    (authState as? app.myfinhub.android.feature.auth.AuthShellUiState.Ready)?.offline == false

/**
 * A server-confirmed cleanup marker is usable only when BOTH finance and vault
 * controllers are attached to the current online AAL2 owner/bearer.
 * The shell becomes Ready before LaunchedEffect attaches these controllers.
 */
internal fun mayRunCardVaultCleanupForAuth(
    authState: app.myfinhub.android.feature.auth.AuthShellUiState,
    financeSessionAuthorized: Boolean,
    vaultSessionAuthorized: Boolean,
): Boolean {
    val ready = authState as? app.myfinhub.android.feature.auth.AuthShellUiState.Ready
        ?: return false
    return !ready.offline &&
        ready.session.assuranceLevel == app.myfinhub.android.core.auth.AssuranceLevel.AAL2 &&
        financeSessionAuthorized && vaultSessionAuthorized
}
