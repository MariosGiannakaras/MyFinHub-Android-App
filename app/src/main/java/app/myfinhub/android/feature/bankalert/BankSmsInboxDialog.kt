package app.myfinhub.android.feature.bankalert

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import app.myfinhub.android.BuildConfig
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun BankSmsInboxDialog(
    owner: String,
    store: BankSmsInboxStore,
    aliases: BankMerchantAliasStore,
    existingEventIds: Set<String>,
    mayEdit: () -> Boolean,
    onReview: (PendingBankSms) -> Unit,
    onManual: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val revision by BankSmsInboxStore.revision.collectAsState()
    var retry by remember { mutableStateOf(0) }
    var rows by remember(owner) { mutableStateOf<List<PendingBankSms>>(emptyList()) }
    var names by remember(owner) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var failed by remember(owner) { mutableStateOf(false) }
    var loaded by remember(owner) { mutableStateOf(false) }
    var optedIn by remember(owner) { mutableStateOf(store.enabled(owner)) }
    var smsGranted by remember { mutableStateOf(hasBankSmsPermission(context)) }
    var notificationsGranted by remember { mutableStateOf((context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true)) }
    var discard by remember(owner) { mutableStateOf<PendingBankSms?>(null) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        smsGranted = hasBankSmsPermission(context)
        notificationsGranted = (context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true)
        retry++
    }
    LaunchedEffect(owner, revision, retry) {
        optedIn = store.enabled(owner)
        smsGranted = hasBankSmsPermission(context)
        notificationsGranted = (context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true)
        try {
            rows = withContext(Dispatchers.IO) { store.list(owner) }
            failed = false
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { rows = emptyList(); failed = true }
        loaded = true
        names = try { aliases.list(owner).associate { merchantAliasKey(it.bankName) to it.nickname } }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { emptyMap() }
    }
    fun requestAccess() {
        if (!mayEdit() || !BuildConfig.BANK_SMS_CAPTURE_ENABLED) return
        scope.launch {
            try {
                // Consent is explicit and owner-bound; Android permission must also be granted.
                val enabled = mayEdit() && store.enable(owner)
                if (!enabled) { failed = true; return@launch }
                if (!mayEdit()) return@launch
                optedIn = true
                val requested = buildList {
                    if (!hasBankSmsPermission(context)) add(Manifest.permission.RECEIVE_SMS)
                    if (Build.VERSION.SDK_INT >= 33 && !notificationsGranted) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (requested.isNotEmpty()) permissions.launch(requested.toTypedArray())
                retry++
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failed = true }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Εισερχόμενα τραπεζικά SMS") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Μόνο νέες αναγνωρισμένες κινήσεις Πειραιώς, στη συσκευή. Δεν καταχωρίζονται αυτόματα. Δεν αποθηκεύονται τα μηνύματα ή τα υπόλοιπα.")
                if (BuildConfig.BANK_SMS_CAPTURE_ENABLED) {
                    Text(when {
                        !optedIn -> "Η αυτόματη λήψη είναι απενεργοποιημένη."
                        !smsGranted -> "Δεν δόθηκε άδεια SMS. Επιτρέπεται μόνο αν το υποστηρίζει η εγκατάσταση Android. Δοκίμασε ξανά ή έλεγξε τις άδειες εφαρμογής."
                        !notificationsGranted -> "Η λήψη είναι ενεργή. Χωρίς άδεια ειδοποιήσεων, οι κινήσεις παραμένουν εδώ."
                        else -> "Η αυτόματη λήψη και οι ειδοποιήσεις είναι ενεργές."
                    }, modifier = Modifier.testTag("bank_sms_capture_status"))
                    if (!optedIn || !smsGranted || !notificationsGranted) {
                        TextButton(onClick = ::requestAccess, enabled = loaded && !failed,
                            modifier = Modifier.testTag("bank_sms_enable")) { Text(if (optedIn) "Ενεργοποίηση αδειών" else "Ενεργοποίηση αυτόματης λήψης") }
                    }
                    if (optedIn) TextButton(onClick = {
                        if (mayEdit()) {
                            // Synchronous consent revocation precedes any further broadcasts.
                            try { store.disable(); clearBankSmsNotification(context); retry++ }
                            catch (_: Exception) { failed = true }
                        }
                    }) { Text("Απενεργοποίηση λήψης") }
                } else Text("Η αυτόματη λήψη δεν διατίθεται σε αυτή την έκδοση.")
                if (failed) {
                    Text("Δεν ήταν δυνατή η πρόσβαση στην τοπική ουρά.", color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) { Text("Δοκίμασε ξανά") }
                } else if (loaded && rows.isEmpty()) Text("Δεν υπάρχουν εκκρεμείς κινήσεις.")
                if (rows.size >= BankSmsInboxStore.MAX_PENDING) Text("Η ουρά είναι γεμάτη. Έλεγξε τις εκκρεμότητες πριν από νέα λήψη.", color = MaterialTheme.colorScheme.error)
                rows.forEach { row ->
                    val recorded = row.eventId in existingEventIds
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(names[merchantAliasKey(row.merchant)] ?: row.merchant, style = MaterialTheme.typography.titleSmall)
                        Text("${row.date} ${row.time} · ${BigDecimal.valueOf(row.cents, 2).toPlainString()} €")
                        if (row.lastFour.isNotEmpty()) Text("Πιστωτική • ${row.lastFour}") else Text("Αγορά με κάρτα")
                        if (recorded) Text("Έχει ήδη καταχωριστεί ή περιμένει συγχρονισμό.")
                        else Row {
                            TextButton(onClick = { if (mayEdit()) onReview(row) },
                                modifier = Modifier.testTag("bank_sms_review_${row.id}")) { Text("Έλεγχος") }
                            TextButton(onClick = { if (mayEdit()) discard = row }) { Text("Απόρριψη") }
                        }
                    }
                }
                TextButton(onClick = onManual, modifier = Modifier.testTag("bank_alert_import")) { Text("Χειροκίνητη εισαγωγή κειμένου (εφεδρικά)") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Κλείσιμο") } },
    )
    discard?.let { row ->
        AlertDialog(
            onDismissRequest = { discard = null },
            title = { Text("Απόρριψη εκκρεμούς SMS;") },
            text = { Text("Δεν θα καταχωριστεί συναλλαγή.") },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            confirmButton = {
                TextButton(onClick = {
                    discard = null
                    if (mayEdit() && row.eventId !in existingEventIds) scope.launch {
                        try { withContext(Dispatchers.IO) { store.discard(owner, row.id) }; retry++ }
                        catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { failed = true }
                    }
                }) { Text("Απόρριψη") }
            },
            dismissButton = { TextButton(onClick = { discard = null }) { Text("Ακύρωση") } },
        )
    }
}
