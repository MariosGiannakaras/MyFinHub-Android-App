package app.myfinhub.android.feature.money

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.network.FinancialProviderRecord
import app.myfinhub.android.core.ui.FinancialProvider
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubIcons
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSearchField
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.util.UUID

data class CardCreateRequest(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val kind: String,
    val network: String,
    val formFactor: String,
    val last4: String?,
    val creditLimit: Double?,
)

private data class CardCreateOption(
    val value: String,
    val label: String,
)

private val cardProviders = listOf(
    FinancialProvider.PIRAEUS,
    FinancialProvider.ALPHA,
    FinancialProvider.NATIONAL,
    FinancialProvider.EUROBANK,
    FinancialProvider.REVOLUT,
    FinancialProvider.VIVA,
    FinancialProvider.PAYZY,
    FinancialProvider.PAYPAL,
)

private val cardKindOptions = listOf(
    CardCreateOption("debit", "Χρεωστική"),
    CardCreateOption("prepaid", "Προπληρωμένη"),
    CardCreateOption("credit", "Πιστωτική"),
)

private val cardNetworkOptions = listOf(
    CardCreateOption("visa", "Visa"),
    CardCreateOption("mastercard", "Mastercard"),
    CardCreateOption("other", "Άλλο"),
)

private val cardFormFactorOptions = listOf(
    CardCreateOption("physical", "Φυσική"),
    CardCreateOption("virtual", "Εικονική"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CanonicalCardCreateScreen(
    cards: List<MoneyCard>,
    financialProviderCatalog: List<FinancialProviderRecord> = emptyList(),
    onCreate: (CardCreateRequest) -> Unit,
    onSaveCardDetails: (String, CharArray, CharArray, CharArray) -> Unit = { _, pan, expiry, cvv ->
        pan.fill('\u0000')
        expiry.fill('\u0000')
        cvv.fill('\u0000')
    },
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var selectedProviderKey by remember { mutableStateOf(FinancialProvider.PIRAEUS.key) }
    var customBank by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("debit") }
    var network by remember { mutableStateOf("visa") }
    var formFactor by remember { mutableStateOf("physical") }
    var pan by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("") }
    var cvv by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var submittedId by remember { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    var activePicker by rememberSaveable { mutableStateOf<String?>(null) }

    val dirty = nickname.isNotBlank() ||
        selectedProviderKey != FinancialProvider.PIRAEUS.key ||
        customBank.isNotBlank() ||
        kind != "debit" ||
        network != "visa" ||
        formFactor != "physical" ||
        pan.isNotBlank() ||
        expiry.isNotBlank() ||
        cvv.isNotBlank() ||
        (kind == "credit" && creditLimit.isNotBlank())
    val requestBack = {
        when {
            submittedId != null -> Unit
            dirty -> discardDialogOpen = true
            else -> onBack()
        }
    }

    BackHandler(onBack = requestBack)

    LaunchedEffect(cards, submittedId) {
        val id = submittedId ?: return@LaunchedEffect
        if (cards.any { it.id == id }) onBack()
    }

    // Server-provided canonical IDs remain data, not an implicit logo or guessed bank name.
    val providerOptions = (
        cardProviders.map { CardCreateOption(it.key, it.institutionLabel) } +
            financialProviderCatalog.map { CardCreateOption(it.id, it.displayName) }
        ).distinctBy(CardCreateOption::value) + CardCreateOption("__manual_issuer__", "Άλλος εκδότης")
    val selectedProviderValue = selectedProviderKey
    val selectedProviderLabel = providerOptions.firstOrNull {
        it.value == selectedProviderValue
    }?.label ?: "Άλλος εκδότης"

    ScaffoldCardCreate(
        requestBack = requestBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            Text(
                "Το προφίλ συγχρονίζεται με το MyFinHub. Αριθμός, λήξη και CVV φυλάσσονται " +
                    "κρυπτογραφημένα στη συσκευή και εμφανίζονται μόνο στα ασφαλή στοιχεία κάρτας.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Όνομα κάρτας") },
                singleLine = true,
                enabled = submittedId == null,
            )

            CardCreateSelectionField(
                label = "Τράπεζα / εκδότης",
                value = selectedProviderLabel,
                testTag = "card_create_provider",
                enabled = submittedId == null,
                onClick = { activePicker = "provider" },
            )
            if (selectedProviderKey == "__manual_issuer__") {
                OutlinedTextField(
                    value = customBank,
                    onValueChange = { customBank = it; validation = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Όνομα εκδότη") },
                    singleLine = true,
                    enabled = submittedId == null,
                )
            }

            CardCreateSelectionField(
                label = "Τύπος",
                value = cardKindOptions.labelFor(kind),
                testTag = "card_create_kind",
                enabled = submittedId == null,
                onClick = { activePicker = "kind" },
            )
            CardCreateSelectionField(
                label = "Δίκτυο",
                value = cardNetworkOptions.labelFor(network),
                testTag = "card_create_network",
                enabled = submittedId == null,
                onClick = { activePicker = "network" },
            )
            CardCreateSelectionField(
                label = "Μορφή",
                value = cardFormFactorOptions.labelFor(formFactor),
                testTag = "card_create_form_factor",
                enabled = submittedId == null,
                onClick = { activePicker = "form_factor" },
            )

            OutlinedTextField(
                value = pan,
                onValueChange = { input ->
                    pan = input.filter(Char::isDigit).take(19)
                    validation = null
                },
                modifier = Modifier.fillMaxWidth().testTag("card_create_pan"),
                label = { Text("Αριθμός κάρτας") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = submittedId == null,
            )
            OutlinedTextField(
                value = expiry,
                onValueChange = { input ->
                    val digits = input.filter(Char::isDigit).take(4)
                    expiry = if (digits.length <= 2) digits else "${digits.take(2)}/${digits.drop(2)}"
                    validation = null
                },
                modifier = Modifier.fillMaxWidth().testTag("card_create_expiry"),
                label = { Text("Λήξη (MM/YY)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                enabled = submittedId == null,
            )
            OutlinedTextField(
                value = cvv,
                onValueChange = { input ->
                    cvv = input.filter(Char::isDigit).take(4)
                    validation = null
                },
                modifier = Modifier.fillMaxWidth().testTag("card_create_cvv"),
                label = { Text("CVV") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                enabled = submittedId == null,
            )
            if (kind == "credit") {
                OutlinedTextField(
                    value = creditLimit,
                    onValueChange = { creditLimit = it; validation = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Πιστωτικό όριο (προαιρετικό)") },
                    suffix = { Text("€") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    enabled = submittedId == null,
                )
            }

            validation?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            MyFinHubPrimaryAction(
                label = if (submittedId == null) "Δημιουργία κάρτας" else "Αποθήκευση…",
                onClick = {
                    val normalizedNickname = nickname.trim()
                    val normalizedBank = if (selectedProviderKey == "__manual_issuer__") customBank.trim()
                        else selectedProviderKey
                    val normalizedPan = pan.filter(Char::isDigit)
                    val normalizedExpiry = expiry.trim()
                    val normalizedCvv = cvv.filter(Char::isDigit)
                    val limit = creditLimit.trim().replace(',', '.').takeIf(String::isNotBlank)?.toDoubleOrNull()
                    validation = when {
                        normalizedNickname.isBlank() -> "Συμπλήρωσε όνομα κάρτας."
                        normalizedBank.isBlank() -> "Συμπλήρωσε τράπεζα ή εκδότη."
                        CardVaultWriteContract.normalizePan(normalizedPan) == null -> "Ο αριθμός κάρτας πρέπει να έχει ακριβώς 16 ψηφία."
                        CardVaultWriteContract.normalizeExpiry(normalizedExpiry) == null -> "Η λήξη πρέπει να είναι μη ληγμένη, σε MM/YY."
                        CardVaultWriteContract.normalizeCvv(normalizedCvv) == null -> "Το CVV πρέπει να έχει ακριβώς 3 ψηφία."
                        kind == "credit" && creditLimit.isNotBlank() && (limit == null || limit <= 0.0) -> "Το πιστωτικό όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                        else -> null
                    }
                    if (validation == null) {
                        val id = "card-android-${UUID.randomUUID()}"
                        submittedId = id
                        val panChars = normalizedPan.toCharArray()
                        val expiryChars = normalizedExpiry.toCharArray()
                        val cvvChars = normalizedCvv.toCharArray()
                        onSaveCardDetails(id, panChars, expiryChars, cvvChars)
                        onCreate(
                            CardCreateRequest(
                                cardId = id,
                                bankId = normalizedBank,
                                nickname = normalizedNickname,
                                kind = kind,
                                network = network,
                                formFactor = formFactor,
                                last4 = normalizedPan.takeLast(4),
                                creditLimit = if (kind == "credit") limit else null,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = submittedId == null,
                icon = null,
            )
        }
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = { discardDialogOpen = false },
            title = { Text("Απόρριψη αλλαγών;") },
            text = { Text("Οι αλλαγές στη νέα κάρτα δεν έχουν αποθηκευτεί.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        discardDialogOpen = false
                        onBack()
                    },
                ) { Text("Απόρριψη") }
            },
            dismissButton = {
                TextButton(onClick = { discardDialogOpen = false }) { Text("Συνέχεια") }
            },
        )
    }

    activePicker?.let { picker ->
        val title: String
        val options: List<CardCreateOption>
        val selected: String
        when (picker) {
            "provider" -> {
                title = "Τράπεζα / εκδότης"
                options = providerOptions
                selected = selectedProviderValue
            }
            "kind" -> {
                title = "Τύπος κάρτας"
                options = cardKindOptions
                selected = kind
            }
            "network" -> {
                title = "Δίκτυο κάρτας"
                options = cardNetworkOptions
                selected = network
            }
            else -> {
                title = "Μορφή κάρτας"
                options = cardFormFactorOptions
                selected = formFactor
            }
        }

        CardCreatePickerSheet(
            title = title,
            picker = picker,
            options = options,
            selectedValue = selected,
            onDismiss = { activePicker = null },
            onSelect = { value ->
                when (picker) {
                    "provider" -> selectedProviderKey = value
                    "kind" -> kind = value
                    "network" -> network = value
                    "form_factor" -> formFactor = value
                }
                validation = null
                activePicker = null
            },
        )
    }
}

@Composable
private fun ScaffoldCardCreate(
    requestBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    androidx.compose.material3.Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κάρτα",
                subtitle = "Χρεωστική, προπληρωμένη ή πιστωτική",
                navigation = { MyFinHubBackButton(requestBack) },
            )
        },
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
private fun CardCreateSelectionField(
    label: String,
    value: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MyFinHubDesignMetrics.textFieldMinHeight)
            .testTag(testTag),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MyFinHubSpacing.md,
                vertical = MyFinHubSpacing.xs,
            ),
            horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Icon(
                imageVector = MyFinHubIcons.ChevronDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardCreatePickerSheet(
    title: String,
    picker: String,
    options: List<CardCreateOption>,
    selectedValue: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    val searchable = picker == "provider" && options.size > 8
    var query by rememberSaveable(picker) { mutableStateOf("") }
    val filteredOptions = if (searchable && query.isNotBlank()) {
        val normalized = query.trim()
        options.filter { option ->
            option.label.contains(normalized, ignoreCase = true) ||
                option.value.contains(normalized, ignoreCase = true)
        }
    } else options
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (searchable) {
                MyFinHubSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = "Αναζήτηση τράπεζας ή εκδότη",
                    modifier = Modifier.testTag("card_create_provider_search"),
                )
            }
            if (filteredOptions.isEmpty()) {
                Text(
                    "Δεν βρέθηκε εκδότης.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("card_create_provider_search_empty"),
                )
            }
            // Many certified providers can be returned from the session catalog.
            // Keep every choice reachable on a small phone or with enlarged fonts.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .testTag("card_create_${picker}_choices"),
                contentPadding = PaddingValues(bottom = MyFinHubSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
            ) {
                items(filteredOptions, key = { it.value }) { option ->
                    val isSelected = option.value == selectedValue
                    Surface(
                        onClick = { onSelect(option.value) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MyFinHubDesignMetrics.minimumTouchTarget)
                            .semantics { selected = isSelected }
                            .testTag("card_create_${picker}_${option.value}"),
                        shape = MaterialTheme.shapes.small,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surface,
                    ) {
                        if (largeFont) {
                            Column(
                                modifier = Modifier.padding(MyFinHubSpacing.md),
                                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
                            ) {
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                                if (isSelected) {
                                    Text(
                                        "Επιλεγμένο",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.padding(MyFinHubSpacing.md),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f),
                                )
                                if (isSelected) {
                                    Text(
                                        "Επιλεγμένο",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<CardCreateOption>.labelFor(value: String): String =
    firstOrNull { it.value == value }?.label ?: value
