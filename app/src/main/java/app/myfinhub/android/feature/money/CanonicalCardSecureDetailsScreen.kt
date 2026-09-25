package app.myfinhub.android.feature.money

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch

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
    val coroutineScope = rememberCoroutineScope()
    var cvvDraft by remember(cardId) { mutableStateOf("") }
    var editorOpen by remember(cardId) { mutableStateOf(false) }
    var panDraft by remember(cardId) { mutableStateOf("") }
    var expiryDraft by remember(cardId) { mutableStateOf("") }
    var validation by remember(cardId) { mutableStateOf<String?>(null) }
    var clipboardMessage by remember(cardId) { mutableStateOf<String?>(null) }

    LaunchedEffect(cardId) {
        onReveal()
    }

    fun closeDetailsSurface() {
        onBack()
    }

    BackHandler(onBack = ::closeDetailsSurface)

    fun openEditor() {
        val revealed = relevantState as? CardSecretUiState.Revealed
        panDraft = revealed?.pan.orEmpty().filter(Char::isDigit).take(19)
        expiryDraft = revealed?.expiry.orEmpty()
        validation = null
        editorOpen = true
    }

    fun copySensitive(label: String, value: String) {
        val marker = "MyFinHub:$cardId:$label:${System.nanoTime()}"
        if (!setSensitiveClipboard(context, marker, value)) {
            clipboardMessage = "Η αντιγραφή δεν είναι διαθέσιμη σε αυτή τη συσκευή."
            return
        }
        clipboardMessage = "$label: αντιγράφηκε προσωρινά."
        coroutineScope.launch {
            delay(SENSITIVE_CLIPBOARD_MILLIS)
            clearClipboardOnlyIfUnchanged(context, marker, value)
            clipboardMessage = null
        }
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
                    if (editorOpen) {
                        MyFinHubOutlinedField(
                            value = panDraft,
                            onValueChange = { input ->
                                panDraft = input.filter(Char::isDigit).take(19)
                                validation = null
                            },
                            label = "Αριθμός κάρτας",
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
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        )
                        validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        MyFinHubPrimaryAction(
                            label = if (relevantState is CardSecretUiState.Saving) "Αποθήκευση…" else "Αποθήκευση αριθμού / λήξης",
                            onClick = {
                                val normalizedPan = panDraft.filter(Char::isDigit)
                                val normalizedExpiry = expiryDraft.trim()
                                validation = when {
                                    normalizedPan.length !in 12..19 -> "Ο αριθμός κάρτας πρέπει να έχει 12 έως 19 ψηφία."
                                    !isValidSecureCardExpiry(normalizedExpiry) -> "Η λήξη πρέπει να είναι MM/YY ή MM/YYYY."
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
                            enabled = relevantState !is CardSecretUiState.Saving,
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
                                enabled = !relevantState.cvvSaving && cvvDraft.length in 3..4,
                                icon = null,
                            )
                            relevantState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!editorOpen && relevantState !is CardSecretUiState.Saving && relevantState !is CardSecretUiState.AuthRejected) {
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

private fun isValidSecureCardExpiry(value: String): Boolean =
    Regex("^(0[1-9]|1[0-2])/(\\d{2}|\\d{4})$").matches(value)

private fun setSensitiveClipboard(context: Context, marker: String, value: String): Boolean = runCatching {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(marker, value)
    clip.description.extras = PersistableBundle().apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        } else {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
}.isSuccess

private fun clearClipboardOnlyIfUnchanged(context: Context, marker: String, value: String) {
    runCatching {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val current = clipboard.primaryClip ?: return
        val unchanged = current.description.label?.toString() == marker &&
            current.itemCount > 0 &&
            current.getItemAt(0).coerceToText(context).toString() == value
        if (!unchanged) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            clipboard.clearPrimaryClip()
        } else {
            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
        }
    }
}
