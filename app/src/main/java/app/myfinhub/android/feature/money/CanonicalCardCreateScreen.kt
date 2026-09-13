package app.myfinhub.android.feature.money

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import app.myfinhub.android.core.ui.FinancialProvider
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubScreenHeader
import app.myfinhub.android.designsystem.MyFinHubSectionCard
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
    FinancialProvider.REVOLUT,
    FinancialProvider.PAYZY,
    FinancialProvider.VIVA,
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
    onCreate: (CardCreateRequest) -> Unit,
    onBack: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var selectedProvider by remember { mutableStateOf<FinancialProvider?>(FinancialProvider.PIRAEUS) }
    var customBank by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("debit") }
    var network by remember { mutableStateOf("visa") }
    var formFactor by remember { mutableStateOf("physical") }
    var last4 by remember { mutableStateOf("") }
    var creditLimit by remember { mutableStateOf("") }
    var validation by remember { mutableStateOf<String?>(null) }
    var submittedId by remember { mutableStateOf<String?>(null) }
    var discardDialogOpen by rememberSaveable { mutableStateOf(false) }
    var activePicker by rememberSaveable { mutableStateOf<String?>(null) }

    val dirty = nickname.isNotBlank() ||
        selectedProvider != FinancialProvider.PIRAEUS ||
        customBank.isNotBlank() ||
        kind != "debit" ||
        network != "visa" ||
        formFactor != "physical" ||
        last4.isNotBlank() ||
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

    val providerOptions = cardProviders.map { CardCreateOption(it.key, it.institutionLabel) } +
        CardCreateOption("custom", "Άλλος εκδότης")
    val selectedProviderValue = selectedProvider?.key ?: "custom"
    val selectedProviderLabel = selectedProvider?.institutionLabel ?: "Άλλος εκδότης"

    ScaffoldCardCreate(
        requestBack = requestBack,
    ) { contentModifier ->
        Column(
            modifier = contentModifier
                .verticalScroll(rememberScrollState())
                .padding(MyFinHubSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
        ) {
            MyFinHubSectionCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Η κάρτα συγχρονίζεται με το οικονομικό σου αρχείο. Τα ευαίσθητα στοιχεία προστατεύονται και δεν εμφανίζονται σε στιγμιότυπα οθόνης ή διαγνωστικά.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

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
            if (selectedProvider == null) {
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
                value = last4,
                onValueChange = { if (it.length <= 4 && it.all(Char::isDigit)) last4 = it; validation = null },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Τελευταία 4 ψηφία (προαιρετικό)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                    val normalizedBank = selectedProvider?.key ?: customBank.trim()
                    val normalizedLast4 = last4.trim().takeIf(String::isNotBlank)
                    val limit = creditLimit.trim().replace(',', '.').takeIf(String::isNotBlank)?.toDoubleOrNull()
                    validation = when {
                        normalizedNickname.isBlank() -> "Συμπλήρωσε όνομα κάρτας."
                        normalizedBank.isBlank() -> "Συμπλήρωσε τράπεζα ή εκδότη."
                        normalizedLast4 != null && normalizedLast4.length != 4 -> "Τα τελευταία ψηφία πρέπει να είναι ακριβώς τέσσερα."
                        kind == "credit" && creditLimit.isNotBlank() && (limit == null || limit <= 0.0) -> "Το πιστωτικό όριο πρέπει να είναι μεγαλύτερο από μηδέν."
                        else -> null
                    }
                    if (validation == null) {
                        val id = "card-android-${UUID.randomUUID()}"
                        submittedId = id
                        onCreate(
                            CardCreateRequest(
                                cardId = id,
                                bankId = normalizedBank,
                                nickname = normalizedNickname,
                                kind = kind,
                                network = network,
                                formFactor = formFactor,
                                last4 = normalizedLast4,
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
                    "provider" -> selectedProvider = cardProviders.firstOrNull { it.key == value }
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
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            Text("Αλλαγή", style = MaterialTheme.typography.labelLarge)
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
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(
                start = MyFinHubSpacing.lg,
                end = MyFinHubSpacing.lg,
                bottom = MyFinHubSpacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            options.forEach { option ->
                val selected = option.value == selectedValue
                Surface(
                    onClick = { onSelect(option.value) },
                    modifier = Modifier.fillMaxWidth().testTag("card_create_${picker}_${option.value}"),
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                ) {
                    Row(
                        modifier = Modifier.padding(MyFinHubSpacing.md),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(option.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (selected) {
                            Text("Επιλεγμένο", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

private fun List<CardCreateOption>.labelFor(value: String): String =
    firstOrNull { it.value == value }?.label ?: value
