package app.myfinhub.android.core.auth

/**
 * A sensitive Compose frame must belong to the EXACT authenticated session
 * that produced its state. Session attachment happens in LaunchedEffect, after
 * composition, so auth Ready may advance before an older owner/bearer is reset.
 *
 * Refresh-token rotation alone does not change the authorization that fetched
 * the visible content. Bearer or assurance changes revoke it synchronously.
 */
internal fun matchesSensitiveUiSession(attached: AuthSession?, authenticated: AuthSession?): Boolean =
    attached != null && authenticated != null &&
        attached.userId == authenticated.userId &&
        attached.accessToken == authenticated.accessToken &&
        attached.assuranceLevel == authenticated.assuranceLevel
