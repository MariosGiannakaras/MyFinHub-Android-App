package app.myfinhub.android.feature.money

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import kotlinx.coroutines.delay

private const val SENSITIVE_CLIPBOARD_MILLIS = 30_000L

@Composable
fun CanonicalCardSecureDetailsScreen(
    cardId: String,
    card: MoneyCard?,
    secretState: CardSecretUiState,
    onReveal: () -> Unit,
    onSaveServerSecrets: (CharArray, CharArray) -> Unit,
    onSaveCvv: (CharArray) -> Unit,
    onBack: () -> Unit,
) {
    val relevantState = secretState.forCard(cardId)
    val context = LocalContext.current
    val providedClipboard = LocalSensitiveClipboardController.current
    val clipboard = remember(cardId, providedClipboard) {
        providedClipboard ?: SensitiveCardClipboard(context.applicationContext)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var cvvDraft by remember(cardId) { mutableStateOf("") }
    var editorOpen by remember(cardId) { mutableStateOf(false) }
    var panDraft by remember(cardId) { mutableStateOf("") }
    var expiryDraft by remember(cardId) { mutableStateOf("") }
    var validation by remember(cardId) { mutableStateOf<String?>(null) }
    var clipboardMessage by remember(cardId) { mutableStateOf<String?>(null) }
    // A Compose coroutine is cancelled when this screen disappears: clipboard
    // expiry must instead be owned by the main looper and survive navigation.
    DisposableEffect(cardId, clipboard, lifecycleOwner, relevantState is CardSecretUiState.Revealed) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) clipboard.clear()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            clipboard.clear()
        }
    }
    LaunchedEffect(clipboardMessage) {
        if (clipboardMessage != null) {
            delay(SENSITIVE_CLIPBOARD_MILLIS)
            clipboardMessage = null
        }
    }
    // Never leave a composed PAN/expiry editor or CVV draft accessible when the
    // owner+AAL2 vault read loses authority, even if navigation retains the screen.
    val vaultReadAuthorized = relevantState is CardSecretUiState.Revealed
    LaunchedEffect(cardId, vaultReadAuthorized) {
        if (!vaultReadAuthorized) {
            clipboard.clear()
            editorOpen = false
            panDraft = ""
            expiryDraft = ""
            cvvDraft = ""
            validation = null
            clipboardMessage = null
        }
    }

    LaunchedEffect(cardId) {
        onReveal()
    }

    fun closeDetailsSurface() {
        clipboard.clear()
        onBack()
    }

    BackHandler(onBack = ::closeDetailsSurface)

    fun openEditor() {
        val revealed = relevantState as? CardSecretUiState.Revealed ?: return
        panDraft = revealed.pan.orEmpty().filter(Char::isDigit).take(19)
        expiryDraft = revealed.expiry.orEmpty()
        validation = null
        editorOpen = true
    }

    fun copySensitive(label: String, value: String) {
        if (!clipboard.copy(value)) {
            clipboardMessage = "Η αντιγραφή δεν είναι διαθέσιμη σε αυτή τη συσκευή."
            return
        }
        clipboardMessage = "$label: αντιγράφηκε προσωρινά."
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Στοιχεία κάρτας",
                subtitle = card?.nickname ?: "Κάρτα",
                navigation = { MyFinHubBackButton(::closeDetailsSurface) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubDesignMetrics.screenHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text(
                        "Ο αριθμός, η λήξη και το CVV αποθηκεύονται κρυπτογραφημένα στο κοινό card vault και συγχρονίζονται στις εγκεκριμένες εφαρμογές.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (editorOpen && vaultReadAuthorized) {
                        MyFinHubOutlinedField(
                            value = panDraft,
                            onValueChange = { input ->
                                panDraft = input.filter(Char::isDigit).take(19)
                                validation = null
                            },
                            label = "Αριθμός κάρτας",
                            modifier = Modifier.testTag("card_secure_pan_editor"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        )
                        MyFinHubOutlinedField(
                            value = expiryDraft,
                            onValueChange = { input ->
                                val digits = input.filter(Char::isDigit).take(6)
                                expiryDraft = if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
                                validation = null
                            },
                            label = "Λήξη (MM/YY ή MM/YYYY)",
                            modifier = Modifier.testTag("card_secure_expiry_editor"),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        )
                        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        MyFinHubPrimaryAction(
                            label = if (relevantState is CardSecretUiState.Saving) "Αποθήκευση…" else "Αποθήκευση αριθμού / λήξης",
                            onClick = {
                                val normalizedPan = panDraft.filter(Char::isDigit)
                                val normalizedExpiry = expiryDraft.trim()
                                validation = when {
                                    CardVaultWriteContract.normalizePan(normalizedPan) == null -> "Ο αριθμός κάρτας πρέπει να έχει ακριβώς 16 ψηφία."
                                    CardVaultWriteContract.normalizeExpiry(normalizedExpiry) == null -> "Η λήξη πρέπει να είναι μη ληγμένη (MM/YY ή MM/YYYY)."
                                    else -> null
                                }
                                if (validation == null) {
                                    val panChars = normalizedPan.toCharArray()
                                    val expiryChars = normalizedExpiry.toCharArray()
                                    panDraft = ""
                                    expiryDraft = ""
                                    onSaveServerSecrets(panChars, expiryChars)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = vaultReadAuthorized,
                            icon = null,
                        )
                        TextButton(
                            onClick = {
                                panDraft = ""
                                expiryDraft = ""
                                validation = null
                                editorOpen = false
                            },
                            enabled = relevantState !is CardSecretUiState.Saving,
                        ) { Text("Ακύρωση") }
                    }

                    when (relevantState) {
                        is CardSecretUiState.Hidden -> {
                            CircularProgressIndicator()
                            Text("Φόρτωση στοιχείων κάρτας…")
                        }
                        is CardSecretUiState.Saving -> {
                            CircularProgressIndicator()
                            Text("Αποθήκευση ασφαλών στοιχείων…")
                        }
                        is CardSecretUiState.Loading -> {
                            CircularProgressIndicator()
                            Text("Ανάκτηση ασφαλών στοιχείων…")
                        }
                        is CardSecretUiState.Failure -> {
                            Text(relevantState.message, color = MaterialTheme.colorScheme.error)
                            if (relevantState.retryable) {
                                MyFinHubPrimaryAction(
                                    label = "Δοκιμή ξανά",
                                    onClick = onReveal,
                                    modifier = Modifier.fillMaxWidth(),
                                    icon = null,
                                )
                            }
                        }
                        CardSecretUiState.AuthRejected -> Text(
                            "Η ασφαλής συνεδρία έληξε. Χρειάζεται νέα σύνδεση.",
                            color = MaterialTheme.colorScheme.error,
                        )
                        is CardSecretUiState.Revealed -> {
                            SecureValueRow("Αριθμός", relevantState.pan, "Δεν έχει αποθηκευτεί") { value ->
                                copySensitive("Αριθμός", value)
                            }
                            SecureValueRow("Λήξη", relevantState.expiry, "Δεν έχει αποθηκευτεί") { value ->
                                copySensitive("Λήξη", value)
                            }
                            SecureValueRow("CVV", relevantState.cvv, "Δεν έχει αποθηκευτεί") { value ->
                                copySensitive("CVV", value)
                            }
                            clipboardMessage?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            MyFinHubOutlinedField(
                                value = cvvDraft,
                                onValueChange = { input -> cvvDraft = input.filter { it in '0'..'9' }.take(4) },
                                label = "Νέο CVV",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            )
                            MyFinHubPrimaryAction(
                                label = if (relevantState.cvvSaving) "Αποθήκευση…" else "Συγχρονισμός CVV",
                                onClick = {
                                    val chars = cvvDraft.toCharArray()
                                    cvvDraft = ""
                                    onSaveCvv(chars)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !relevantState.cvvSaving && CardVaultWriteContract.normalizeCvv(cvvDraft) != null,
                                icon = null,
                            )
                            relevantState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!editorOpen && vaultReadAuthorized) {
                        TextButton(onClick = ::openEditor) {
                            Text(if (relevantState is CardSecretUiState.Revealed) "Αλλαγή αριθμού / λήξης" else "Προσθήκη αριθμού / λήξης")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SecureValueRow(
    label: String,
    value: String?,
    emptyLabel: String,
    onCopy: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(MyFinHubDesignMetrics.secretValueLabelWidth), style = MaterialTheme.typography.labelMedium)
        Text(
            value ?: emptyLabel,
            modifier = Modifier.weight(1f).clearAndSetSemantics {
                contentDescription = if (value == null) "$label. $emptyLabel" else "$label. Πλήρης τιμή εμφανίζεται."
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (value != null) TextButton(onClick = { onCopy(value) }) { Text("Αντιγραφή") }
    }
}

private fun CardSecretUiState.forCard(cardId: String): CardSecretUiState = when (this) {
    is CardSecretUiState.Hidden -> takeIf { this.cardId == null || this.cardId == cardId }
    is CardSecretUiState.Saving -> takeIf { this.cardId == cardId }
    is CardSecretUiState.Loading -> takeIf { this.cardId == cardId }
    is CardSecretUiState.Revealed -> takeIf { this.cardId == cardId }
    is CardSecretUiState.Failure -> takeIf { this.cardId == cardId }
    CardSecretUiState.AuthRejected -> this
} ?: CardSecretUiState.Hidden(cardId)


