package app.myfinhub.android.feature.receipt

import android.Manifest
import android.content.pm.PackageManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import app.myfinhub.android.feature.activity.ActivityCategoryOption
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.auth.AuthShellUiState
import app.myfinhub.android.feature.bankalert.BankAlertParser
import app.myfinhub.android.feature.bankalert.BankMerchantAliasStore
import app.myfinhub.android.feature.bankalert.BankMerchantAliasesDialog
import app.myfinhub.android.feature.bankalert.merchantAliasKey
import app.myfinhub.android.feature.bankalert.validMerchantAliasName
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class ReceiptImportController(
    val open: () -> Unit,
    val prefill: ReceiptPrefill?,
    val consume: () -> Boolean,
)
internal val LocalReceiptImportController = staticCompositionLocalOf<ReceiptImportController?> { null }

private enum class ReceiptPhase { CHOOSE, BANK_TEXT, BANK_LOOKUP, BANK_ALIASES, CAMERA_PERMISSION, CAMERA_READY, CAMERA, QR_SCAN, PICKER, RECOGNIZE, REVIEW, ERROR }
private enum class ReceiptFailure { CAMERA_PERMISSION, CAMERA_LAUNCH, QR_LAUNCH, PICKER_LAUNCH, RECOGNITION }
private data class ReceiptRequest(
    val ownerId: String,
    val phase: ReceiptPhase,
    val image: Uri? = null,
    val cameraFile: File? = null,
    val proposal: ReceiptProposal? = null,
    val failure: ReceiptFailure? = null,
    val qrOnly: Boolean = false,
    // Preserve identity through copies so a stale camera callback cannot match a new request.
    val token: Any = Any(),
)

internal fun receiptOwner(auth: AuthShellUiState): String? = when (auth) {
    is AuthShellUiState.Ready -> auth.session.userId
    is AuthShellUiState.Locked -> auth.session.userId
    // Loading can be same-owner authentication returning from an external activity.
    else -> null
}

internal fun mayKeepReceiptRequest(ownerId: String, auth: AuthShellUiState): Boolean =
    auth == AuthShellUiState.Loading || receiptOwner(auth) == ownerId

private fun cameraDirectory(context: Context) = File(context.cacheDir, "receipt-capture")

/** Only the one private temporary output URI is granted to the camera Activity. */
internal class ReceiptCameraContract : ActivityResultContracts.TakePicture() {
    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input).apply {
            clipData = ClipData.newRawUri("receipt", input)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
}

/**
 * The activity-result launchers live above the lock/navigation shell. ON_STOP still locks
 * the app. The external result remains privately owner-bound in memory, with no persisted URI
 * permission or saved-instance state, until the same owner unlocks and finance is authorized.
 */
@Composable
internal fun ReceiptImportHost(
    auth: AuthShellUiState,
    authorized: Boolean,
    activityItems: List<ActivityItem>,
    currentOwnerMayEdit: (String) -> Boolean,
    categoryOptions: List<ActivityCategoryOption> = emptyList(),
    qrScanner: @Composable ((ReceiptQrRead) -> Unit, () -> Unit, () -> Unit) -> Unit = { read, failure, dismiss ->
        ReceiptQrScanner(read, failure, dismiss)
    },
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val merchantAliases = remember(context) { BankMerchantAliasStore(context) }
    var request by remember { mutableStateOf<ReceiptRequest?>(null) }
    var reviewed by remember { mutableStateOf<Pair<String, ReceiptPrefill>?>(null) }
    val liveAuth by rememberUpdatedState(auth)
    val mayEdit by rememberUpdatedState(currentOwnerMayEdit)

    fun clear() {
        request?.cameraFile?.delete()
        request = null
        reviewed = null
    }

    // A killed process has no owner-bound request; remove leftover private camera files.
    DisposableEffect(context) {
        cameraDirectory(context).listFiles()?.forEach { it.delete() }
        onDispose { clear() }
    }

    val cameraContract = remember { ReceiptCameraContract() }
    val pickerContract = remember { ActivityResultContracts.PickVisualMedia() }
    val camera = rememberLauncherForActivityResult(cameraContract) { success ->
        val active = request
        if (active?.phase == ReceiptPhase.CAMERA) {
            if (success && active.image != null && mayKeepReceiptRequest(active.ownerId, liveAuth)) {
                request = active.copy(phase = ReceiptPhase.RECOGNIZE)
            } else clear()
        }
    }
    val picker = rememberLauncherForActivityResult(pickerContract) { uri ->
        val active = request
        if (active?.phase == ReceiptPhase.PICKER) {
            if (uri != null && uri.scheme == "content" && mayKeepReceiptRequest(active.ownerId, liveAuth)) {
                request = active.copy(phase = ReceiptPhase.RECOGNIZE, image = uri)
            } else clear()
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val pending = request
        if (pending?.phase == ReceiptPhase.CAMERA_PERMISSION) {
            if (!mayKeepReceiptRequest(pending.ownerId, liveAuth)) clear()
            else request = pending.copy(
                phase = if (granted) ReceiptPhase.CAMERA_READY else ReceiptPhase.ERROR,
                failure = if (granted) null else ReceiptFailure.CAMERA_PERMISSION,
            )
        }
    }

    fun beginCamera(active: ReceiptRequest, qrOnly: Boolean) {
        if (!mayEdit(active.ownerId) || request != active) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED) {
            request = active.copy(phase = ReceiptPhase.CAMERA_READY, qrOnly = qrOnly)
        } else {
            request = active.copy(phase = ReceiptPhase.CAMERA_PERMISSION, qrOnly = qrOnly)
            try { permission.launch(Manifest.permission.CAMERA) }
            catch (_: Exception) {
                request = active.copy(phase = ReceiptPhase.ERROR, failure = ReceiptFailure.CAMERA_PERMISSION)
            }
        }
    }

    val readyOwner = (auth as? AuthShellUiState.Ready)?.session?.userId
        ?.takeIf { authorized }
    val active = request
    val requestOwner = active?.ownerId ?: reviewed?.first
    LaunchedEffect(auth, requestOwner) {
        if (requestOwner != null && !mayKeepReceiptRequest(requestOwner, auth)) clear()
    }
    LaunchedEffect(active, readyOwner) {
        // A live preview is never resumed automatically after lock/authority loss.
        if (active != null && (active.phase in setOf(ReceiptPhase.QR_SCAN, ReceiptPhase.BANK_TEXT, ReceiptPhase.BANK_LOOKUP, ReceiptPhase.BANK_ALIASES) ||
                active.proposal?.bankAlert != null) && active.ownerId != readyOwner) {
            if (request == active) clear()
            return@LaunchedEffect
        }
        if (active?.phase == ReceiptPhase.BANK_LOOKUP && active.ownerId == readyOwner &&
            request == active && mayEdit(active.ownerId)) {
            val proposal = active.proposal ?: return@LaunchedEffect
            val descriptor = proposal.bankAlert?.merchant.orEmpty()
            val resolved = try {
                val nickname = merchantAliases.list(active.ownerId).singleOrNull {
                    merchantAliasKey(it.bankName) == merchantAliasKey(descriptor)
                }?.nickname
                proposal.copy(merchant = nickname ?: descriptor)
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { proposal.copy(bankAliasLookupFailed = true) }
            if (request == active && mayEdit(active.ownerId)) request = active.copy(phase = ReceiptPhase.REVIEW, proposal = resolved)
            return@LaunchedEffect
        }
        if (active?.phase == ReceiptPhase.CAMERA_READY && active.ownerId == readyOwner &&
            request == active && mayEdit(active.ownerId)) {
            if (active.qrOnly) {
                request = active.copy(phase = ReceiptPhase.QR_SCAN)
            } else {
                var file: File? = null
                try {
                    val directory = cameraDirectory(context)
                    check(directory.exists() || directory.mkdirs())
                    file = File.createTempFile("receipt-", ".jpg", directory)
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".receipt", file)
                    request = active.copy(phase = ReceiptPhase.CAMERA, image = uri, cameraFile = file)
                    camera.launch(uri)
                } catch (_: Exception) {
                    file?.delete()
                    request = active.copy(phase = ReceiptPhase.ERROR, image = null, cameraFile = null,
                        failure = ReceiptFailure.CAMERA_LAUNCH)
                }
            }
            return@LaunchedEffect
        }
        if (active?.phase != ReceiptPhase.RECOGNIZE || active.image == null ||
            active.ownerId != readyOwner || !mayEdit(active.ownerId)) return@LaunchedEffect
        try {
            val proposal = recognizeReceipt(context, active.image)
            if (request == active && mayEdit(active.ownerId)) {
                active.cameraFile?.delete()
                request = active.copy(phase = ReceiptPhase.REVIEW, image = null, cameraFile = null, proposal = proposal)
            }
        } catch (cancel: CancellationException) {
            // Lock or owner switch cancels publication. A same-owner unlock may retry the bounded read.
            throw cancel
        } catch (_: Exception) {
            if (request == active) {
                active.cameraFile?.delete()
                request = active.copy(phase = ReceiptPhase.ERROR, image = null, cameraFile = null,
                    failure = ReceiptFailure.RECOGNITION)
            }
        } catch (_: LinkageError) {
            // Missing native OCR library: normal manual entry must remain usable.
            if (request == active) {
                active.cameraFile?.delete()
                request = active.copy(phase = ReceiptPhase.ERROR, image = null, cameraFile = null,
                    failure = ReceiptFailure.RECOGNITION)
            }
        }
    }

    val controller = readyOwner?.let { owner ->
        ReceiptImportController(
            open = {
                if (request == null && reviewed == null && mayEdit(owner)) {
                    request = ReceiptRequest(owner, ReceiptPhase.CHOOSE)
                }
            },
            prefill = reviewed?.takeIf { it.first == owner }?.second,
            consume = {
                if (reviewed?.first == owner && mayEdit(owner)) {
                    reviewed = null
                    true
                } else false
            },
        )
    }
    CompositionLocalProvider(LocalReceiptImportController provides controller) { content() }

    // Never display a result while locked or while finance for this owner is not attached.
    if (active == null || active.ownerId != readyOwner) return
    when (active.phase) {
        ReceiptPhase.CHOOSE -> AlertDialog(
            onDismissRequest = { clear() },
            title = { Text("Εισαγωγή απόδειξης ή SMS") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Η αναγνώριση γίνεται στη συσκευή. Θα ελέγξεις τα στοιχεία πριν από την καταχώριση.")
                    TextButton(
                        onClick = { beginCamera(active, qrOnly = true) },
                        modifier = Modifier.fillMaxWidth().testTag("receipt_scan_qr"),
                    ) { Text("Σάρωση QR Code") }
                    Text("Ή φωτογράφισε ολόκληρη την απόδειξη για κατάστημα και είδη.")
                    TextButton(
                        onClick = {
                            if (mayEdit(active.ownerId) && request == active) {
                                request = active.copy(phase = ReceiptPhase.BANK_TEXT)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("bank_alert_import"),
                    ) { Text("Εισαγωγή τραπεζικού SMS") }
                    TextButton(
                        onClick = { if (mayEdit(active.ownerId) && request == active) request = active.copy(phase = ReceiptPhase.BANK_ALIASES) },
                        modifier = Modifier.fillMaxWidth().testTag("bank_alert_aliases"),
                    ) { Text("Τοπικά ονόματα εμπόρων") }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { beginCamera(active, qrOnly = false) },
                    modifier = Modifier.testTag("receipt_camera"),
                ) { Text("Κάμερα") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        if (!mayEdit(active.ownerId)) return@TextButton
                        request = active.copy(phase = ReceiptPhase.PICKER)
                        try { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        catch (_: Exception) {
                            request = active.copy(phase = ReceiptPhase.ERROR, failure = ReceiptFailure.PICKER_LAUNCH)
                        }
                    },
                    modifier = Modifier.testTag("receipt_gallery"),
                ) { Text("Φωτογραφία") }
            },
        )
        ReceiptPhase.BANK_TEXT -> BankAlertTextDialog(
            onDismiss = { clear() },
            onParsed = { alert ->
                if (request == active && mayEdit(active.ownerId)) {
                    request = active.copy(phase = ReceiptPhase.BANK_LOOKUP, proposal = ReceiptProposal(
                        merchant = alert.merchant, date = alert.date, totalCents = alert.cents,
                        currency = ReceiptCurrency.EUR, bankAlert = alert,
                    ))
                }
            },
        )
        ReceiptPhase.BANK_ALIASES -> BankMerchantAliasesDialog(
            owner = active.ownerId, store = merchantAliases,
            mayEdit = { request == active && mayEdit(active.ownerId) },
            onDismiss = { if (request == active) request = active.copy(phase = ReceiptPhase.CHOOSE) },
        )
        ReceiptPhase.BANK_LOOKUP -> AlertDialog(
            onDismissRequest = { clear() }, title = { Text("Εύρεση τοπικού ονόματος") },
            text = { CircularProgressIndicator() },
            confirmButton = { TextButton(onClick = { clear() }) { Text("Ακύρωση") } },
        )
        ReceiptPhase.QR_SCAN -> qrScanner(
            { result ->
                if (request == active && mayEdit(active.ownerId) && result.fiscal != null) {
                    request = active.copy(
                        phase = ReceiptPhase.REVIEW,
                        proposal = mergeReceiptQr(ReceiptProposal(), result),
                    )
                }
            },
            {
                if (request == active) request = active.copy(phase = ReceiptPhase.ERROR, failure = ReceiptFailure.QR_LAUNCH)
            },
            {
                if (request == active) request = active.copy(phase = ReceiptPhase.CHOOSE)
            },
        )
        ReceiptPhase.RECOGNIZE -> AlertDialog(
            onDismissRequest = { clear() },
            title = { Text("Αναγνώριση στη συσκευή") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Ανάγνωση QR και ελληνικού / αγγλικού κειμένου…")
                }
            },
            confirmButton = { TextButton(onClick = { clear() }) { Text("Ακύρωση") } },
        )
        ReceiptPhase.REVIEW -> active.proposal?.let { proposal ->
            ReceiptReviewDialog(
                proposal = proposal,
                activityItems = activityItems,
                categoryOptions = categoryOptions,
                onDismiss = { clear() },
                onSaveAlias = proposal.bankAlert?.takeIf { it.kind != BankAlertParser.Kind.WITHDRAWAL && validMerchantAliasName(it.merchant) }?.let { bank ->
                    val save: suspend (String) -> Boolean = { nickname ->
                        if (request != active || !mayEdit(active.ownerId)) false else try {
                            merchantAliases.put(active.ownerId, bank.merchant, nickname)
                            request == active && mayEdit(active.ownerId)
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: Exception) { false }
                    }
                    save
                },
                onApply = { prefill ->
                    if (request == active && mayEdit(active.ownerId)) {
                        reviewed = active.ownerId to prefill
                        request = null
                    }
                },
            )
        }
        ReceiptPhase.ERROR -> AlertDialog(
            onDismissRequest = { clear() },
            title = { Text(when (active.failure) {
                ReceiptFailure.CAMERA_PERMISSION -> "Δεν δόθηκε πρόσβαση στην κάμερα"
                ReceiptFailure.QR_LAUNCH -> "Δεν άνοιξε η σάρωση QR"
                ReceiptFailure.CAMERA_LAUNCH -> "Δεν άνοιξε η κάμερα"
                ReceiptFailure.PICKER_LAUNCH -> "Δεν άνοιξε η επιλογή φωτογραφίας"
                else -> "Δεν έγινε αναγνώριση"
            }, modifier = Modifier.testTag("receipt_error_title")) },
            text = { Text(when (active.failure) {
                ReceiptFailure.CAMERA_PERMISSION -> "Μπορείς να επιλέξεις ήδη υπάρχουσα φωτογραφία ή να επιτρέψεις την κάμερα στις ρυθμίσεις Android."
                ReceiptFailure.QR_LAUNCH -> "Δοκίμασε ξανά ή εισήγαγε φωτογραφία της απόδειξης."
                ReceiptFailure.CAMERA_LAUNCH -> "Δοκίμασε ξανά ή επίλεξε φωτογραφία. Δεν έγινε αναγνώριση εικόνας."
                ReceiptFailure.PICKER_LAUNCH -> "Δοκίμασε ξανά ή χρησιμοποίησε την κάμερα. Δεν έγινε αναγνώριση εικόνας."
                else -> "Χρησιμοποίησε καθαρή φωτογραφία JPEG ή PNG έως 12 MB, ή συνέχισε χειροκίνητα."
            }) },
            confirmButton = { TextButton(onClick = { request = ReceiptRequest(active.ownerId, ReceiptPhase.CHOOSE) }) { Text("Ξανά") } },
            dismissButton = { TextButton(onClick = { clear() }) { Text("Χειροκίνητα") } },
        )
        ReceiptPhase.CAMERA_PERMISSION, ReceiptPhase.CAMERA_READY, ReceiptPhase.CAMERA, ReceiptPhase.PICKER -> Unit
    }
}

@Composable
internal fun BankAlertTextDialog(
    onDismiss: () -> Unit,
    onParsed: (BankAlertParser.Alert) -> Unit,
) {
    // No automatic clipboard/inbox access or rememberSaveable. Raw text is disposed with the dialog.
    var text by remember { mutableStateOf("") }
    var rejected by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Τραπεζικό SMS") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Αντέγραψε μόνο την ειδοποίηση αγοράς χρεωστικής ή πιστωτικής κάρτας, ή ανάληψης Πειραιώς. Μην επικολλήσεις κωδικούς ασφαλείας. Δεν διαβάζονται άλλα μηνύματα.")
                OutlinedTextField(
                    value = text,
                    onValueChange = { value ->
                        if (value.length <= 1500) { text = value; rejected = false }
                        else { text = ""; rejected = true }
                    },
                    label = { Text("Κείμενο ειδοποίησης") },
                    modifier = Modifier.fillMaxWidth().testTag("bank_alert_text"),
                    minLines = 3, maxLines = 6,
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                )
                if (rejected) Text(
                    "Δεν αναγνωρίστηκε με ασφάλεια αγορά ή ανάληψη σε ευρώ. Συνέχισε χειροκίνητα.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.testTag("bank_alert_rejected"),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = {
                    val alert = BankAlertParser.parse(text)
                    if (alert == null) { text = ""; rejected = true }
                    else { text = ""; onParsed(alert) }
                },
                modifier = Modifier.testTag("bank_alert_parse"),
            ) { Text("Ανάλυση") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } },
    )
}

@Composable
internal fun ReceiptReviewDialog(
    proposal: ReceiptProposal,
    activityItems: List<ActivityItem>,
    onDismiss: () -> Unit,
    onApply: (ReceiptPrefill) -> Unit,
    categoryOptions: List<ActivityCategoryOption> = emptyList(),
    onSaveAlias: (suspend (String) -> Boolean)? = null,
) {
    val aliasScope = rememberCoroutineScope()
    var aliasBusy by remember(proposal) { mutableStateOf(false) }
    var aliasStatus by remember(proposal) { mutableStateOf<String?>(null) }
    var amount by remember(proposal) {
        mutableStateOf(proposal.totalCents?.takeIf { proposal.currency == ReceiptCurrency.EUR }
            ?.let(::receiptAmountText).orEmpty())
    }
    var merchant by remember(proposal) { mutableStateOf(proposal.merchant) }
    var date by remember(proposal) { mutableStateOf(proposal.date?.toString().orEmpty()) }
    var confirmedEuro by remember(proposal) { mutableStateOf(proposal.currency == ReceiptCurrency.EUR) }
    var useSplit by remember(proposal) { mutableStateOf(false) }
    var lines by remember(proposal) { mutableStateOf(proposal.items.map { receiptAmountText(it.cents) }) }
    var duplicateConfirmed by remember(amount, date, merchant, activityItems, confirmedEuro) { mutableStateOf(false) }
    val qr = proposal.qrReview
    val fiscal = qr?.fiscal
    val qrDisagreement = qr?.requiresConfirmation == true
    var qrConfirmed by remember(proposal, amount, date, confirmedEuro) { mutableStateOf(false) }
    val bankAlert = proposal.bankAlert
    val withdrawal = bankAlert?.kind == BankAlertParser.Kind.WITHDRAWAL
    val creditPurchase = bankAlert?.kind == BankAlertParser.Kind.CREDIT_PURCHASE
    var bankConfirmed by remember(proposal, amount, date, merchant) { mutableStateOf(false) }
    val suggestion = if (withdrawal) null else receiptCategorySuggestion(activityItems, categoryOptions, merchant)
    var useSuggestion by remember(suggestion, merchant, useSplit) { mutableStateOf(false) }
    val cents = receiptReviewedCents(amount)
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()?.takeIf { it.year in 2000..2100 }
    val matches = if (!confirmedEuro) emptyList() else if (withdrawal)
        bankWithdrawalMatches(activityItems, cents, parsedDate)
    else receiptPurchaseMatches(activityItems, cents, parsedDate, merchant)
    val reviewedLines = proposal.items.mapIndexedNotNull { index, item ->
        receiptReviewedCents(lines[index])?.let { ReceiptLine(item.label, it) }
    }
    // Split is only available for original confirmed EUR receipts, never foreign/unknown amounts.
    val parts = if (reviewedLines.size == proposal.items.size && proposal.currency == ReceiptCurrency.EUR)
        reviewedReceiptSplit(reviewedLines, cents, confirmedEuro) else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bankAlert != null) "Έλεγχος τραπεζικής ειδοποίησης" else "Έλεγχος απόδειξης") },
        properties = DialogProperties(securePolicy = if (bankAlert != null) SecureFlagPolicy.SecureOn else SecureFlagPolicy.Inherit),
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (bankAlert != null) "Έλεγξε την κίνηση. Καμία καταχώριση δεν γίνεται πριν επιλέξεις λογαριασμό και πατήσεις Καταχώριση."
                    else "Έλεγξε τα στοιχεία της απόδειξης. Η καταχώριση γίνεται μόνο στην επόμενη φόρμα.")
                if (bankAlert != null) {
                    Text((when { withdrawal -> "Ανάληψη προς μετρητά"; creditPurchase -> "Αγορά με πιστωτική"; else -> "Αγορά με χρεωστική" }) +
                        bankAlert.lastFour.takeIf { it.isNotBlank() }?.let { " · κάρτα •••• $it" }.orEmpty() +
                        bankAlert.accountHint.takeIf { it.isNotBlank() }?.let { " · λογ/μός SMS: $it" }.orEmpty() +
                        (bankAlert.time?.let { " · " + it.toString() } ?: "") +
                        bankAlert.country.takeIf { it.isNotBlank() }?.let { " · " + it }.orEmpty(),
                        modifier = Modifier.testTag("bank_alert_summary"))
                    Text("Το μήνυμα δεν επαληθεύεται από την τράπεζα. Δεν αντιστοιχίζεται αυτόματα λογαριασμός.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = bankConfirmed, onCheckedChange = { bankConfirmed = it },
                            modifier = Modifier.testTag("bank_alert_confirmation"))
                        Text(if (creditPurchase) "Επιβεβαιώνω ότι ολοκληρώθηκε με πιστωτική κάρτα" else "Επιβεβαιώνω ότι ολοκληρώθηκε με χρεωστική κάρτα")
                    }
                } else if (fiscal != null) {
                    Text(
                        "QR ΑΑΔΕ · " + receiptAmountText(fiscal.totalCents) + " € · " +
                            fiscal.issuedAt.toString().replace('T', ' '),
                        modifier = Modifier.testTag("receipt_qr_source"),
                    )
                    Text("Τα στοιχεία διαβάστηκαν από τον κωδικό, χωρίς ηλεκτρονική επαλήθευση.")
                    if (qrDisagreement) {
                        Text(
                            "Διαφορά QR και OCR. OCR: " +
                                (qr?.ocrTotalCents?.let(::receiptAmountText) ?: "—") + " · " +
                                (qr?.ocrDate?.toString() ?: "—") +
                                (if (qr?.ocrAmbiguous == true) " · Πολλαπλές τιμές OCR." else "") +
                                ". Έλεγξε ποσό, ημερομηνία και νόμισμα.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.testTag("receipt_qr_conflict"),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = qrConfirmed, onCheckedChange = { qrConfirmed = it },
                                modifier = Modifier.testTag("receipt_qr_confirmation"))
                            Text("Έλεγξα και επιβεβαιώνω τα τελικά στοιχεία")
                        }
                    }
                } else if (qr?.status == ReceiptQrStatus.AMBIGUOUS) {
                    Text("Βρέθηκαν διαφορετικοί QR κωδικοί. Χρησιμοποιήθηκε μόνο OCR.", color = MaterialTheme.colorScheme.error)
                } else if (qr?.status == ReceiptQrStatus.UNSUPPORTED) {
                    Text("Ο QR κωδικός δεν υποστηρίζεται. Χρησιμοποιήθηκε μόνο OCR, χωρίς άνοιγμα συνδέσμου.")
                } else {
                    Text("Το OCR μπορεί να κάνει λάθος.")
                }
                if (proposal.currency != ReceiptCurrency.EUR) {
                    Text(
                        if (proposal.currency == ReceiptCurrency.FOREIGN)
                            "Εντοπίστηκε ξένο νόμισμα. Δήλωσε το πραγματικό ποσό χρέωσης σε ευρώ."
                        else "Το νόμισμα δεν αναγνωρίστηκε. Επιβεβαίωσε το ποσό σε ευρώ.",
                        color = MaterialTheme.colorScheme.error,
                    )
                    if (proposal.currency == ReceiptCurrency.UNKNOWN && proposal.totalCents != null)
                        Text("Αναγνωρισμένο σύνολο: " + receiptAmountText(proposal.totalCents))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmedEuro, onCheckedChange = { confirmedEuro = it })
                        Text("Το ποσό που εισάγω είναι σε ευρώ")
                    }
                }
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.take(20) },
                    label = { Text("Σύνολο (€)") }, modifier = Modifier.fillMaxWidth().testTag("receipt_review_amount"),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = merchant, onValueChange = { merchant = it.take(90); aliasStatus = null },
                    label = { Text("Κατάστημα / περιγραφή") }, modifier = Modifier.fillMaxWidth().testTag("receipt_review_merchant"),
                    singleLine = true,
                )
                if (bankAlert != null && onSaveAlias != null) {
                    Text("Όνομα τράπεζας: " + bankAlert.merchant)
                    if (proposal.bankAliasLookupFailed) Text("Δεν ήταν διαθέσιμες οι τοπικές αντιστοιχίσεις.", color = MaterialTheme.colorScheme.error)
                    TextButton(
                        enabled = !aliasBusy && validMerchantAliasName(merchant) && merchantAliasKey(merchant) != merchantAliasKey(bankAlert.merchant),
                        onClick = {
                            val nickname = merchant
                            aliasBusy = true
                            aliasScope.launch {
                                aliasStatus = if (onSaveAlias(nickname)) "Αποθηκεύτηκε μόνο στη συσκευή." else "Η αντιστοίχιση δεν αποθηκεύτηκε."
                                aliasBusy = false
                            }
                        }, modifier = Modifier.testTag("bank_alert_save_alias"),
                    ) { Text("Αποθήκευση ονόματος στη συσκευή") }
                    aliasStatus?.let { Text(it, modifier = Modifier.testTag("bank_alert_alias_status")) }
                }
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) },
                    label = { Text("Ημερομηνία (ΕΕΕΕ-ΜΜ-ΗΗ)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (suggestion != null && !useSplit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useSuggestion, onCheckedChange = { useSuggestion = it },
                            modifier = Modifier.testTag("receipt_category_confirmation"))
                        Text("Πρόταση από προηγούμενες κινήσεις: " + suggestion.category +
                            suggestion.subcategory.takeIf(String::isNotBlank)?.let { " / " + it }.orEmpty())
                    }
                }
                if (matches.isNotEmpty()) {
                    Text("Πιθανή προηγούμενη καταχώριση", color = MaterialTheme.colorScheme.error)
                    matches.forEach { item ->
                        Text(item.rawDate + " · " + item.note.take(90) + " · " + receiptAmountText(cents ?: 0) + " €")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = duplicateConfirmed, onCheckedChange = { duplicateConfirmed = it },
                            modifier = Modifier.testTag("receipt_duplicate_confirmation"))
                        Text("Επιβεβαιώνω ότι πρόκειται για νέα κίνηση")
                    }
                }
                if (proposal.currency == ReceiptCurrency.EUR && proposal.items.size in 2..12) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = useSplit, onCheckedChange = { useSplit = it })
                        Text("Κατανομή σε κατηγορίες ανά είδος")
                    }
                    if (useSplit) {
                        proposal.items.forEachIndexed { index, item ->
                            OutlinedTextField(
                                value = lines[index], onValueChange = { value ->
                                    lines = lines.toMutableList().apply { set(index, value.take(20)) }
                                },
                                label = { Text(item.label) }, modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                            )
                        }
                        if (parts.isEmpty()) Text("Τα είδη πρέπει να ισούνται ακριβώς με το σύνολο.", color = MaterialTheme.colorScheme.error)
                        else Text("Επίλεξε κατηγορία και υποκατηγορία ανά είδος στην επόμενη φόρμα.")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !aliasBusy && confirmedEuro && cents != null && parsedDate != null &&
                    (matches.isEmpty() || duplicateConfirmed) && (!useSplit || parts.isNotEmpty()) &&
                    (!qrDisagreement || qrConfirmed) && (bankAlert == null || bankConfirmed),
                onClick = {
                    if (cents != null && parsedDate != null) onApply(ReceiptPrefill(
                        receiptAmountText(cents), parsedDate.toString(), merchant.trim(),
                        if (useSplit) parts else emptyList(),
                        suggestion.takeIf { useSuggestion && !useSplit },
                        bankAlert?.kind,
                        bankAlert?.lastFour?.takeIf { creditPurchase },
                    ))
                },
                modifier = Modifier.testTag("receipt_review_apply"),
            ) { Text("Συνέχεια") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("receipt_review_cancel")) { Text("Ακύρωση") } },
    )
}
