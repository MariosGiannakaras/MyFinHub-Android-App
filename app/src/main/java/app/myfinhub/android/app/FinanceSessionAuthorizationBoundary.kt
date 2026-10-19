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
        previous.accessToken != incoming.accessToken
)
