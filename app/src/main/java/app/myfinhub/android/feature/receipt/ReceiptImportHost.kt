package app.myfinhub.android.feature.receipt

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.myfinhub.android.feature.activity.ActivityItem
import app.myfinhub.android.feature.auth.AuthShellUiState
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal data class ReceiptImportController(
    val open: () -> Unit,
    val prefill: ReceiptPrefill?,
    val consume: () -> Boolean,
)
internal val LocalReceiptImportController = staticCompositionLocalOf<ReceiptImportController?> { null }

private enum class ReceiptPhase { CHOOSE, CAMERA, PICKER, RECOGNIZE, REVIEW, ERROR }
private enum class ReceiptFailure { CAMERA_LAUNCH, PICKER_LAUNCH, RECOGNITION }
private data class ReceiptRequest(
    val ownerId: String,
    val phase: ReceiptPhase,
    val image: Uri? = null,
    val cameraFile: File? = null,
    val proposal: ReceiptProposal? = null,
    val failure: ReceiptFailure? = null,
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
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
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

    val readyOwner = (auth as? AuthShellUiState.Ready)?.session?.userId
        ?.takeIf { authorized }
    val active = request
    val requestOwner = active?.ownerId ?: reviewed?.first
    LaunchedEffect(auth, requestOwner) {
        if (requestOwner != null && !mayKeepReceiptRequest(requestOwner, auth)) clear()
    }
    LaunchedEffect(active, readyOwner) {
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
            title = { Text("Εισαγωγή απόδειξης") },
            text = { Text("Η αναγνώριση γίνεται στη συσκευή. Θα ελέγξεις τα στοιχεία πριν από την καταχώριση.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (!mayEdit(active.ownerId)) return@TextButton
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
                    },
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
        ReceiptPhase.RECOGNIZE -> AlertDialog(
            onDismissRequest = { clear() },
            title = { Text("Αναγνώριση στη συσκευή") },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator()
                    Text("Επεξεργασία ελληνικών και αγγλικών…")
                }
            },
            confirmButton = { TextButton(onClick = { clear() }) { Text("Ακύρωση") } },
        )
        ReceiptPhase.REVIEW -> active.proposal?.let { proposal ->
            ReceiptReviewDialog(
                proposal = proposal,
                activityItems = activityItems,
                onDismiss = { clear() },
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
                ReceiptFailure.CAMERA_LAUNCH -> "Δεν άνοιξε η κάμερα"
                ReceiptFailure.PICKER_LAUNCH -> "Δεν άνοιξε η επιλογή φωτογραφίας"
                else -> "Δεν έγινε αναγνώριση"
            }, modifier = Modifier.testTag("receipt_error_title")) },
            text = { Text(when (active.failure) {
                ReceiptFailure.CAMERA_LAUNCH -> "Δοκίμασε ξανά ή επίλεξε φωτογραφία. Δεν έγινε αναγνώριση εικόνας."
                ReceiptFailure.PICKER_LAUNCH -> "Δοκίμασε ξανά ή χρησιμοποίησε την κάμερα. Δεν έγινε αναγνώριση εικόνας."
                else -> "Χρησιμοποίησε καθαρή φωτογραφία JPEG ή PNG έως 12 MB, ή συνέχισε χειροκίνητα."
            }) },
            confirmButton = { TextButton(onClick = { request = ReceiptRequest(active.ownerId, ReceiptPhase.CHOOSE) }) { Text("Ξανά") } },
            dismissButton = { TextButton(onClick = { clear() }) { Text("Χειροκίνητα") } },
        )
        ReceiptPhase.CAMERA, ReceiptPhase.PICKER -> Unit
    }
}

@Composable
internal fun ReceiptReviewDialog(
    proposal: ReceiptProposal,
    activityItems: List<ActivityItem>,
    onDismiss: () -> Unit,
    onApply: (ReceiptPrefill) -> Unit,
) {
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
    val cents = receiptReviewedCents(amount)
    val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()?.takeIf { it.year in 2000..2100 }
    val matches = if (confirmedEuro) receiptPurchaseMatches(activityItems, cents, parsedDate, merchant) else emptyList()
    val reviewedLines = proposal.items.mapIndexedNotNull { index, item ->
        receiptReviewedCents(lines[index])?.let { ReceiptLine(item.label, it) }
    }
    // Split is only available for original confirmed EUR receipts, never foreign/unknown amounts.
    val parts = if (reviewedLines.size == proposal.items.size && proposal.currency == ReceiptCurrency.EUR)
        reviewedReceiptSplit(reviewedLines, cents, confirmedEuro) else emptyList()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Έλεγχος απόδειξης") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Το OCR μπορεί να κάνει λάθος. Έλεγξε όλα τα στοιχεία. Η καταχώριση γίνεται μόνο στην επόμενη φόρμα.")
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
                    value = merchant, onValueChange = { merchant = it.take(90) },
                    label = { Text("Κατάστημα / περιγραφή") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = date, onValueChange = { date = it.take(10) },
                    label = { Text("Ημερομηνία (ΕΕΕΕ-ΜΜ-ΗΗ)") }, modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
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
                enabled = confirmedEuro && cents != null && parsedDate != null &&
                    (matches.isEmpty() || duplicateConfirmed) && (!useSplit || parts.isNotEmpty()),
                onClick = {
                    if (cents != null && parsedDate != null) onApply(ReceiptPrefill(
                        receiptAmountText(cents), parsedDate.toString(), merchant.trim(),
                        if (useSplit) parts else emptyList(),
                    ))
                },
                modifier = Modifier.testTag("receipt_review_apply"),
            ) { Text("Συνέχεια") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ακύρωση") } },
    )
}
