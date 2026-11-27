package app.myfinhub.android.feature.bankalert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun BankMerchantAliasesDialog(
    owner: String,
    store: BankMerchantAliasStore,
    mayEdit: () -> Boolean,
    onDismiss: () -> Unit,
) {
    var rows by remember(owner) { mutableStateOf<List<BankMerchantAlias>>(emptyList()) }
    var bankName by remember(owner) { mutableStateOf("") }
    var nickname by remember(owner) { mutableStateOf("") }
    var busy by remember(owner) { mutableStateOf(true) }
    var failed by remember(owner) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        try { rows = store.list(owner); failed = false }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed = true }
        finally { busy = false }
    }
    LaunchedEffect(owner) { refresh() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Τοπικά ονόματα εμπόρων") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Οι αντιστοιχίσεις μένουν κρυπτογραφημένες μόνο σε αυτή τη συσκευή. Δεν συγχρονίζονται με το Web.")
                if (failed) Text("Δεν ήταν δυνατή η πρόσβαση στις αντιστοιχίσεις.", color = MaterialTheme.colorScheme.error)
                if (!busy && !failed && rows.isEmpty()) Text("Δεν υπάρχουν αποθηκευμένα ονόματα.")
                rows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(row.nickname, style = MaterialTheme.typography.titleSmall)
                            Text(row.bankName, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(enabled = !busy && !failed, onClick = { bankName = row.bankName; nickname = row.nickname }) { Text("Επεξεργασία") }
                        TextButton(enabled = !busy && !failed, onClick = {
                            if (!mayEdit()) return@TextButton
                            busy = true
                            scope.launch {
                                try { store.remove(owner, row.bankName); refresh() }
                                catch (cancel: CancellationException) { throw cancel }
                                catch (_: Exception) { failed = true; busy = false }
                            }
                        }) { Text("Διαγραφή") }
                    }
                }
                OutlinedTextField(bankName, { bankName = it.take(90) }, label = { Text("Όνομα στο SMS") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bank_alias_original"))
                OutlinedTextField(nickname, { nickname = it.take(90) }, label = { Text("Δικό σου όνομα") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("bank_alias_nickname"))
                TextButton(enabled = !busy && !failed && validMerchantAliasName(bankName) && validMerchantAliasName(nickname), onClick = {
                    if (!mayEdit()) return@TextButton
                    busy = true
                    scope.launch {
                        try { store.put(owner, bankName, nickname); bankName = ""; nickname = ""; refresh() }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true; busy = false }
                    }
                }, modifier = Modifier.testTag("bank_alias_save")) { Text("Αποθήκευση στη συσκευή") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Κλείσιμο") } },
    )
}
