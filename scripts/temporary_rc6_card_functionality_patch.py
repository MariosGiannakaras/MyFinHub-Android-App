from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"anchor not found in {path}: {old[:100]!r}")
    p.write_text(text.replace(old, new, 1))

# Card secret controller: expose the already-supported native PUT /api/card-secrets boundary.
vm = "app/src/main/java/app/myfinhub/android/feature/money/CardSecretViewModel.kt"
replace_once(
    vm,
    "import app.myfinhub.android.core.network.ApiResult\n",
    "import app.myfinhub.android.core.network.ApiResult\nimport app.myfinhub.android.core.network.CardSecretUpdate\n",
)
replace_once(
    vm,
    "    data class Loading(val cardId: String) : CardSecretUiState\n\n    data class Revealed(\n",
    "    data class Loading(val cardId: String) : CardSecretUiState\n    data class Saving(val cardId: String) : CardSecretUiState\n\n    data class Revealed(\n",
)
replace_once(
    vm,
    "        if (mutableState.value is CardSecretUiState.Loading) return\n",
    "        if (mutableState.value is CardSecretUiState.Loading || mutableState.value is CardSecretUiState.Saving) return\n",
)
replace_once(
    vm,
    "    fun saveCvv(cvv: CharArray) {\n",
    r'''    fun saveServerSecrets(pan: CharArray, expiry: CharArray) {
        val session = currentSession
        val cardId = currentCardId
        val panCopy = pan.copyOf()
        val expiryCopy = expiry.copyOf()
        pan.fill('\u0000')
        expiry.fill('\u0000')

        val normalizedPan = try {
            panCopy.concatToString().filter(Char::isDigit)
        } finally {
            panCopy.fill('\u0000')
        }
        val normalizedExpiry = try {
            normalizeServerExpiry(expiryCopy.concatToString())
        } finally {
            expiryCopy.fill('\u0000')
        }

        if (session == null || cardId == null) {
            mutableNotices.tryEmit(
                UserNotice(
                    message = "Τα στοιχεία κάρτας δεν αποθηκεύτηκαν επειδή η ασφαλής συνεδρία δεν είναι διαθέσιμη.",
                    details = "Ενέργεια: Αποθήκευση PAN/λήξης\nΚατηγορία: STALE_CARD_STATE",
                    diagnosticCode = "MFH-APP-STALE_CARD_STATE",
                ),
            )
            return
        }
        if (normalizedPan.length !in 12..19 || normalizedExpiry == null) {
            mutableState.value = CardSecretUiState.Failure(
                cardId = cardId,
                message = "Έλεγξε τον αριθμό κάρτας και τη λήξη (MM/YY ή MM/YYYY).",
                retryable = false,
            )
            return
        }

        mutableState.value = CardSecretUiState.Saving(cardId)
        viewModelScope.launch {
            when (
                val result = safeApiCall {
                    api.saveCardSecrets(
                        session = session,
                        cardId = cardId,
                        update = CardSecretUpdate(pan = normalizedPan, expiry = normalizedExpiry),
                    )
                }
            ) {
                is ApiResult.Success -> {
                    if (!stillCurrent(session, cardId)) return@launch
                    revealWithLocalCvv(
                        session = session,
                        cardId = cardId,
                        pan = normalizedPan,
                        expiry = normalizedExpiry,
                        message = "PAN και λήξη αποθηκεύτηκαν στο ασφαλές server vault.",
                    )
                }

                is ApiResult.Failure -> when {
                    result.kind == ApiFailureKind.AUTH_REQUIRED || result.kind == ApiFailureKind.MFA_REQUIRED -> {
                        mutableNotices.emit(result.toUserNotice("Αποθήκευση ασφαλών στοιχείων κάρτας"))
                        mutableState.value = CardSecretUiState.AuthRejected
                    }

                    else -> {
                        mutableState.value = CardSecretUiState.Failure(
                            cardId = cardId,
                            message = apiFailureMessage(result.kind),
                            retryable = result.retryable,
                        )
                        mutableNotices.emit(result.toUserNotice("Αποθήκευση ασφαλών στοιχείων κάρτας"))
                    }
                }
            }
        }
    }

    fun saveCvv(cvv: CharArray) {
''',
)
replace_once(
    vm,
    "    private fun stillCurrent(session: AuthSession, cardId: String): Boolean =\n        currentSession?.userId == session.userId && currentCardId == cardId\n\n    private companion object {\n",
    r'''    private fun stillCurrent(session: AuthSession, cardId: String): Boolean =
        currentSession?.userId == session.userId && currentCardId == cardId

    private fun normalizeServerExpiry(raw: String): String? {
        val compact = raw.trim().replace(" ", "")
        val match = Regex("^(0[1-9]|1[0-2])/(\\d{2}|\\d{4})$").matchEntire(compact) ?: return null
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private companion object {
''',
)

# Production card detail: full safe-secret editing and direct canonical purchase/payment entry.
detail = "app/src/main/java/app/myfinhub/android/feature/money/CanonicalCardDetailScreen.kt"
Path(detail).write_text(r'''package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.security.SecureWindowProtection
import app.myfinhub.android.core.ui.financialProvider
import app.myfinhub.android.designsystem.FinanceTone
import app.myfinhub.android.designsystem.MyFinHubAmountText
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubDestructiveTextAction
import app.myfinhub.android.designsystem.MyFinHubIconBadge
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.text.NumberFormat
import java.util.Locale

@Composable
fun CanonicalCardDetailScreen(
    card: MoneyCard?,
    secretState: CardSecretUiState = CardSecretUiState.Hidden(),
    onReveal: () -> Unit = {},
    onHideSecrets: () -> Unit = {},
    onSaveServerSecrets: (CharArray, CharArray) -> Unit = { pan, expiry ->
        pan.fill('\u0000')
        expiry.fill('\u0000')
    },
    onSaveCvv: (CharArray) -> Unit = { value -> value.fill('\u0000') },
    onDeleteCvv: () -> Unit = {},
    onAddPurchase: () -> Unit = {},
    onPayCard: () -> Unit = {},
    onBack: () -> Unit,
) {
    val relevantState = when (secretState) {
        is CardSecretUiState.Hidden -> secretState.takeIf { it.cardId == null || it.cardId == card?.id }
        is CardSecretUiState.Loading -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Saving -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Revealed -> secretState.takeIf { it.cardId == card?.id }
        is CardSecretUiState.Failure -> secretState.takeIf { it.cardId == card?.id }
        CardSecretUiState.AuthRejected -> secretState
    } ?: CardSecretUiState.Hidden(card?.id)

    var cvvDraft by remember(card?.id) { mutableStateOf("") }
    var secretEditorOpen by remember(card?.id) { mutableStateOf(false) }
    var panDraft by remember(card?.id) { mutableStateOf("") }
    var expiryDraft by remember(card?.id) { mutableStateOf("") }
    var secretValidation by remember(card?.id) { mutableStateOf<String?>(null) }
    val provider = card?.let { financialProvider(it.bankId, it.nickname) }
    val isCredit = card?.let { it.canonicalKind == "credit" || it.kind.contains("Πιστωτική", ignoreCase = true) } == true

    SecureWindowProtection(
        active = relevantState is CardSecretUiState.Revealed ||
            relevantState is CardSecretUiState.Saving ||
            secretEditorOpen,
    )

    LaunchedEffect(relevantState) {
        if (secretEditorOpen && relevantState is CardSecretUiState.Revealed) {
            secretEditorOpen = false
            panDraft = ""
            expiryDraft = ""
            secretValidation = null
        }
    }

    fun openSecretEditor() {
        val revealed = relevantState as? CardSecretUiState.Revealed
        panDraft = revealed?.pan.orEmpty().filter(Char::isDigit).take(19)
        expiryDraft = revealed?.expiry.orEmpty()
        secretValidation = null
        secretEditorOpen = true
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = card?.nickname ?: "Κάρτα",
                subtitle = provider?.institutionLabel ?: card?.kind ?: "Δεν είναι διαθέσιμη",
                navigation = { MyFinHubBackButton(onBack) },
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
            if (card == null) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Η κάρτα δεν είναι διαθέσιμη.")
                }
                return@Column
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (provider != null) {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(36.dp), contentDescription = provider.institutionLabel)
                        } else {
                            MyFinHubIconBadge(MyFinHubIcons.Card, FinanceTone.Transfer, null)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(card.nickname, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            provider?.institutionLabel?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(
                            if (card.last4.isBlank()) "••••" else "•••• ${card.last4}",
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                    Text(card.kind, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (isCredit) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Τρέχουσα οφειλή", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MyFinHubAmountText(formatCardEuro(card.currentBalance), FinanceTone.Expense, style = MaterialTheme.typography.titleLarge)
                            }
                            card.limit?.takeIf { it > 0.0 }?.let { limit ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Όριο", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatCardEuro(limit), style = MaterialTheme.typography.titleMedium)
                                }
                            }
                        }
                    }
                }
            }

            if (isCredit) {
                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Ενέργειες πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Οι ενέργειες ανοίγουν την υπάρχουσα canonical καταχώριση και περνούν από το ίδιο offline-safe mutation queue.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        MyFinHubPrimaryAction(
                            label = "Καταχώριση αγοράς",
                            onClick = onAddPurchase,
                            modifier = Modifier.fillMaxWidth(),
                            icon = null,
                        )
                        MyFinHubPrimaryAction(
                            label = "Πληρωμή πιστωτικής",
                            onClick = onPayCard,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = card.currentBalance > 0.005,
                            icon = null,
                        )
                    }
                }

                MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
                        Text("Κινήσεις πιστωτικής", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Αγορές και πληρωμές που είναι πραγματικά συνδεδεμένες με αυτή την κάρτα.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (card.activity.isEmpty()) {
                            Text("Δεν υπάρχουν ακόμη συνδεδεμένες κινήσεις.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            card.activity.take(20).forEachIndexed { index, item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MyFinHubIconBadge(
                                        icon = if (item.kind == MoneyCardActivityKind.PAYMENT) MyFinHubIcons.Income else MyFinHubIcons.Card,
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                        contentDescription = null,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                                        Text(item.dateLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    MyFinHubAmountText(
                                        text = formatSignedCardEuro(item.amount),
                                        tone = if (item.kind == MoneyCardActivityKind.PAYMENT) FinanceTone.Income else FinanceTone.Expense,
                                    )
                                }
                                if (index != card.activity.take(20).lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            }

            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    Text("Ασφαλή στοιχεία", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "PAN/λήξη αποθηκεύονται μόνο μέσω του owner+AAL2 server vault. Το CVV παραμένει αποκλειστικά στο κρυπτογραφημένο vault αυτής της συσκευής.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (secretEditorOpen) {
                        MyFinHubOutlinedField(
                            value = panDraft,
                            onValueChange = { input ->
                                panDraft = input.filter(Char::isDigit).take(19)
                                secretValidation = null
                            },
                            label = "Αριθμός κάρτας (PAN)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
                            visualTransformation = PasswordVisualTransformation(),
                        )
                        MyFinHubOutlinedField(
                            value = expiryDraft,
                            onValueChange = { input ->
                                val digits = input.filter(Char::isDigit).take(6)
                                expiryDraft = if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
                                secretValidation = null
                            },
                            label = "Λήξη (MM/YY ή MM/YYYY)",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        )
                        secretValidation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        MyFinHubPrimaryAction(
                            label = if (relevantState is CardSecretUiState.Saving) "Αποθήκευση…" else "Αποθήκευση PAN / λήξης",
                            onClick = {
                                val normalizedPan = panDraft.filter(Char::isDigit)
                                val normalizedExpiry = expiryDraft.trim()
                                secretValidation = when {
                                    normalizedPan.length !in 12..19 -> "Ο αριθμός κάρτας πρέπει να έχει 12 έως 19 ψηφία."
                                    !isValidCardExpiry(normalizedExpiry) -> "Η λήξη πρέπει να είναι MM/YY ή MM/YYYY."
                                    else -> null
                                }
                                if (secretValidation == null) {
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
                                secretValidation = null
                                secretEditorOpen = false
                            },
                            enabled = relevantState !is CardSecretUiState.Saving,
                        ) { Text("Ακύρωση") }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }

                    when (relevantState) {
                        is CardSecretUiState.Hidden -> {
                            MyFinHubPrimaryAction(
                                label = "Αποκάλυψη ασφαλών στοιχείων",
                                onClick = onReveal,
                                modifier = Modifier.fillMaxWidth(),
                                icon = null,
                            )
                        }
                        is CardSecretUiState.Loading -> {
                            CircularProgressIndicator()
                            Text("Ανάκτηση ασφαλών στοιχείων…")
                        }
                        is CardSecretUiState.Saving -> {
                            CircularProgressIndicator()
                            Text("Αποθήκευση PAN / λήξης στο ασφαλές vault…")
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
                        CardSecretUiState.AuthRejected -> {
                            Text("Η ασφαλής συνεδρία δεν είναι πλέον έγκυρη. Θα χρειαστεί νέα σύνδεση.", color = MaterialTheme.colorScheme.error)
                        }
                        is CardSecretUiState.Revealed -> {
                            SecretValue("PAN", relevantState.pan ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("Λήξη", relevantState.expiry ?: "Δεν έχει αποθηκευτεί")
                            SecretValue("CVV", relevantState.cvv ?: "Δεν έχει αποθηκευτεί στη συσκευή")
                            TextButton(onClick = onHideSecrets) { Text("Απόκρυψη στοιχείων") }
                            MyFinHubOutlinedField(
                                value = cvvDraft,
                                onValueChange = { input -> cvvDraft = input.filter { it in '0'..'9' }.take(4) },
                                label = "Νέο CVV για αυτή τη συσκευή",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            MyFinHubPrimaryAction(
                                label = if (relevantState.cvvSaving) "Αποθήκευση…" else "Αποθήκευση CVV στη συσκευή",
                                onClick = {
                                    val chars = cvvDraft.toCharArray()
                                    cvvDraft = ""
                                    onSaveCvv(chars)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !relevantState.cvvSaving && cvvDraft.length in 3..4,
                                icon = null,
                            )
                            if (relevantState.cvv != null) {
                                MyFinHubDestructiveTextAction(
                                    label = "Διαγραφή τοπικού CVV",
                                    onClick = onDeleteCvv,
                                    enabled = !relevantState.cvvSaving,
                                )
                            }
                            relevantState.message?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!secretEditorOpen && relevantState !is CardSecretUiState.Saving && relevantState !is CardSecretUiState.AuthRejected) {
                        TextButton(onClick = ::openSecretEditor) {
                            Text(if (relevantState is CardSecretUiState.Revealed) "Αλλαγή PAN / λήξης" else "Προσθήκη PAN / λήξης")
                        }
                    }
                    Text(
                        "Η προστασία screenshot/recent-app thumbnail ενεργοποιείται όσο εμφανίζονται ή επεξεργάζονται πραγματικά στοιχεία.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SecretValue(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.width(MyFinHubDesignMetrics.secretValueLabelWidth), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun isValidCardExpiry(value: String): Boolean =
    Regex("^(0[1-9]|1[0-2])/(\\d{2}|\\d{4})$").matches(value)

private fun formatCardEuro(value: Double): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("el-GR")).format(value)

private fun formatSignedCardEuro(value: Double): String =
    (if (value > 0.005) "+" else "") + formatCardEuro(value)
''')

# Wire the card detail into the existing canonical quick-entry mutation path.
app = "app/src/main/java/app/myfinhub/android/app/MyFinHubApp.kt"
replace_once(
    app,
    "    onSaveLocalCvv: (CharArray) -> Unit = { value -> value.fill('\\u0000') },\n    onDeleteLocalCvv: () -> Unit = {},\n",
    "    onSaveServerCardSecrets: (CharArray, CharArray) -> Unit = { pan, expiry -> pan.fill('\\u0000'); expiry.fill('\\u0000') },\n    onSaveLocalCvv: (CharArray) -> Unit = { value -> value.fill('\\u0000') },\n    onDeleteLocalCvv: () -> Unit = {},\n",
)
replace_once(
    app,
    "                            onReveal = onRevealCardSecrets,\n                            onHideSecrets = onHideCardSecrets,\n                            onSaveCvv = onSaveLocalCvv,\n                            onDeleteCvv = onDeleteLocalCvv,\n                            onBack = { moneyBackStack.removeLastOrNull() },\n",
    "                            onReveal = onRevealCardSecrets,\n                            onHideSecrets = onHideCardSecrets,\n                            onSaveServerSecrets = onSaveServerCardSecrets,\n                            onSaveCvv = onSaveLocalCvv,\n                            onDeleteCvv = onDeleteLocalCvv,\n                            onAddPurchase = {\n                                onQuickEntryAction(QuickEntryAction.Reset)\n                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PURCHASE))\n                                onQuickEntryAction(QuickEntryAction.CardChanged(route.cardId))\n                                moneyBackStack.pushIfNew(AppRoute.QuickEntry)\n                            },\n                            onPayCard = {\n                                onQuickEntryAction(QuickEntryAction.Reset)\n                                onQuickEntryAction(QuickEntryAction.SelectKind(QuickEntryKind.CARD_PAYMENT))\n                                onQuickEntryAction(QuickEntryAction.CardChanged(route.cardId))\n                                moneyBackStack.pushIfNew(AppRoute.QuickEntry)\n                            },\n                            onBack = { moneyBackStack.removeLastOrNull() },\n",
)

# Wire secure PAN/expiry save from the production root to CardSecretViewModel.
root = "app/src/main/java/app/myfinhub/android/app/MyFinHubRoot.kt"
replace_once(
    root,
    "                            onSaveLocalCvv = cardSecretViewModel::saveCvv,\n                            onDeleteLocalCvv = cardSecretViewModel::deleteCvv,\n",
    "                            onSaveServerCardSecrets = cardSecretViewModel::saveServerSecrets,\n                            onSaveLocalCvv = cardSecretViewModel::saveCvv,\n                            onDeleteLocalCvv = cardSecretViewModel::deleteCvv,\n",
)
replace_once(
    root,
    "    onSaveLocalCvv: (CharArray) -> Unit,\n    onDeleteLocalCvv: () -> Unit,\n",
    "    onSaveServerCardSecrets: (CharArray, CharArray) -> Unit,\n    onSaveLocalCvv: (CharArray) -> Unit,\n    onDeleteLocalCvv: () -> Unit,\n",
)
replace_once(
    root,
    "                    onSaveLocalCvv = onSaveLocalCvv,\n                    onDeleteLocalCvv = onDeleteLocalCvv,\n",
    "                    onSaveServerCardSecrets = onSaveServerCardSecrets,\n                    onSaveLocalCvv = onSaveLocalCvv,\n                    onDeleteLocalCvv = onDeleteLocalCvv,\n",
)

# Regression test for the server vault write path and caller-buffer zeroing.
test = "app/src/androidTest/java/app/myfinhub/android/feature/money/CardSecretViewModelTest.kt"
replace_once(
    test,
    "    @Test\n    fun purgeCard_clearsRevealedStateAndDeviceLocalCvv() = runBlocking {\n",
    r'''    @Test
    fun saveServerPanAndExpiry_usesNativeVaultBoundary_zeroesInputs_andRevealsSavedValues() = runBlocking {
        val api = FakeCardApi(ApiResult.Failure(ApiFailureKind.INVALID_DATA))
        val vault = FakeCvvVault(initial = charArrayOf('3', '2', '1'))
        val viewModel = CardSecretViewModel(application, api, vault)

        viewModel.attachSession(session)
        viewModel.openCard("card-1")
        val pan = "4242424242424242".toCharArray()
        val expiry = "12/30".toCharArray()
        viewModel.saveServerSecrets(pan, expiry)

        assertTrue(pan.all { it == '\u0000' })
        assertTrue(expiry.all { it == '\u0000' })
        waitUntil { viewModel.state.value is CardSecretUiState.Revealed }

        val revealed = viewModel.state.value as CardSecretUiState.Revealed
        assertEquals("4242", revealed.pan?.takeLast(4))
        assertEquals("12/30", revealed.expiry)
        assertEquals("321", revealed.cvv)
        assertEquals(1, api.serverSecretWriteCalls)
        assertFalse(revealed.toString().contains("4242424242424242"))
    }

    @Test
    fun purgeCard_clearsRevealedStateAndDeviceLocalCvv() = runBlocking {
''',
)

print("rc6 card functionality patch applied")
