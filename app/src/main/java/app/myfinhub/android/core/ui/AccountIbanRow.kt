package app.myfinhub.android.core.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import app.myfinhub.android.core.network.AccountMetadataRecord
import app.myfinhub.android.core.network.compactIban

/**
 * Shared in-memory account-metadata UI. Never exposes a full IBAN in UI, content descriptions,
 * logs or persisted finance state; copy only from a user-initiated action on a visible screen.
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
    val text = when {
        !amountsVisible -> "IBAN ••••"
        compact != null -> compact
        else -> "Δεν έχει οριστεί"
    }
    val context = LocalContext.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f).semantics {
                contentDescription = if (compact != null) "IBAN $compact" else text
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (compact != null && record?.iban != null) {
            val iban = record.iban
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("IBAN", iban)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                }
                clipboard?.setPrimaryClip(clip)
            }) {
                Text("Αντιγραφή")
            }
        }
    }
}
