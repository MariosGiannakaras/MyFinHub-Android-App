package app.myfinhub.android.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.network.compactIban
import app.myfinhub.android.core.network.normalizeIban
import app.myfinhub.android.core.security.SensitiveCardClipboard
import kotlinx.coroutines.delay

/** Injectable controller permits hosted Compose tests without system clipboard readback. */
internal val LocalIbanClipboardController =
    staticCompositionLocalOf<SensitiveCardClipboard?> { null }

private const val IBAN_CLIPBOARD_LIFETIME_MS = 60_000L

/**
 * Shared Home / Accounts / Account detail IBAN display. Never shows or announces
 * full IBAN; a valid normalized IBAN is copied only after explicit user action.
 *
 * The clipboard is marked sensitive and expires after 60s. Unlike PAN/CVV,
 * switching to a banking app must remain possible: backgrounding alone does NOT
 * revoke an IBAN copy. Hiding values, changing accounts or leaving this row
 * immediately revokes only the app-owned clipboard entry, not a newer foreign copy.
 */
@Composable
fun AccountIbanRow(
    record: AccountMetadataRecord?,
    loaded: Boolean,
    amountsVisible: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!loaded) return
    val compact = if (amountsVisible) compactIban(record?.iban) else null
    val canonicalIban = if (compact != null) normalizeIban(record?.iban) else null
    val label = when {
        !amountsVisible -> "IBAN ••••"
        compact != null -> compact
        else -> "Δεν έχει οριστεί"
    }
    val context = LocalContext.current.applicationContext
    val injected = LocalIbanClipboardController.current
    val clipboard = remember(context, injected) {
        injected ?: SensitiveCardClipboard(context, expiryMillis = IBAN_CLIPBOARD_LIFETIME_MS)
    }
    // Account identity matters even when two accounts happen to share an IBAN.
    // Revoke the previous row's clipboard entitlement on account switch.
    val accountId = record?.accountId
    var status by remember(accountId, canonicalIban) { mutableStateOf("Αντιγραφή") }
    var copyCount by remember(accountId, canonicalIban) { mutableIntStateOf(0) }
    DisposableEffect(clipboard, accountId, canonicalIban) {
        // A changed account, hidden amount or removed row may not leave an IBAN behind.
        onDispose { clipboard.clear() }
    }
    LaunchedEffect(copyCount, accountId, canonicalIban) {
        if (copyCount > 0) {
            delay(IBAN_CLIPBOARD_LIFETIME_MS)
            status = "Αντιγραφή"
        }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = if (compact != null) "IBAN $compact" else label
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (canonicalIban != null) {
            TextButton(
                onClick = {
                    if (clipboard.copy(canonicalIban)) {
                        status = "Αντιγράφηκε για 60″"
                        copyCount += 1
                    } else {
                        // Failed system writes do not start a misleading success timer.
                        status = "Αποτυχία αντιγραφής"
                    }
                },
                modifier = Modifier.testTag("account_iban_copy"),
            ) {
                Text(status)
            }
        }
    }
}
