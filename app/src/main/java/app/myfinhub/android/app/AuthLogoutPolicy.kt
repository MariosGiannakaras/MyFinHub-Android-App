package app.myfinhub.android.app

import app.myfinhub.android.feature.money.CardSecretUiState

/**
 * Remote auth rejection is owned only by product data/card-secret APIs.
 *
 * Auxiliary services such as the private updater are deliberately absent from this policy: an
 * update check/download/install failure must never invalidate or clear the user's authenticated
 * product session.
 */
internal fun shouldLogoutForProductAuthRejection(
    financeState: FinanceProductState,
    cardSecretState: CardSecretUiState,
): Boolean = financeState is FinanceProductState.AuthRejected || cardSecretState is CardSecretUiState.AuthRejected
