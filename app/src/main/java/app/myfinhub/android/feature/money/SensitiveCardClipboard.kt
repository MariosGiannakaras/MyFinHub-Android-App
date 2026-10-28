package app.myfinhub.android.feature.money

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Compatibility aliases: one shared core-security clipboard policy powers
 * both gated card secrets and user-initiated account IBAN copying.
 */
internal typealias SensitiveClipboardPort = app.myfinhub.android.core.security.SensitiveClipboardPort
internal typealias SensitiveCardClipboard = app.myfinhub.android.core.security.SensitiveCardClipboard

internal val LocalSensitiveClipboardController =
    staticCompositionLocalOf<SensitiveCardClipboard?> { null }
