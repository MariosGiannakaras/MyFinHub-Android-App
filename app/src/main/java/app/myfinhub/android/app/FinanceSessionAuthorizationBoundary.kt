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
