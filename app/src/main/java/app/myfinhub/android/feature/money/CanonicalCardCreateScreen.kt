package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.myfinhub.android.core.ui.FinancialProvider
import app.myfinhub.android.designsystem.MyFinHubBackButton
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubProviderMark
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

private val cardProviders = listOf(
    FinancialProvider.PIRAEUS,
    FinancialProvider.ALPHA,
    FinancialProvider.REVOLUT,
    FinancialProvider.PAYZY,
    FinancialProvider.VIVA,
)

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

    LaunchedEffect(cards, submittedId) {
        val id = submittedId ?: return@LaunchedEffect
        if (cards.any { it.id == id }) onBack()
    }

    Scaffold(
        topBar = {
            MyFinHubScreenHeader(
                title = "Νέα κάρτα",
                subtitle = "Χρεωστική, προπληρωμένη ή πιστωτική",
                navigation = { MyFinHubBackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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

            Text("Τράπεζα / εκδότης", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                cardProviders.forEach { provider ->
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { selectedProvider = provider; validation = null },
                        label = { Text(provider.institutionLabel) },
                        leadingIcon = {
                            MyFinHubProviderMark(provider, modifier = Modifier.size(20.dp), contentDescription = null)
                        },
                        enabled = submittedId == null,
                    )
                }
                FilterChip(
                    selected = selectedProvider == null,
                    onClick = { selectedProvider = null; validation = null },
                    label = { Text("Άλλος εκδότης") },
                    enabled = submittedId == null,
                )
            }
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

            Text("Τύπος", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("debit" to "Χρεωστική", "prepaid" to "Προπληρωμένη", "credit" to "Πιστωτική").forEach { (value, label) ->
                    FilterChip(
                        selected = kind == value,
                        onClick = { kind = value; validation = null },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
            Text("Δίκτυο", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("visa" to "Visa", "mastercard" to "Mastercard", "other" to "Άλλο").forEach { (value, label) ->
                    FilterChip(
                        selected = network == value,
                        onClick = { network = value },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
            Text("Μορφή", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xxs),
            ) {
                listOf("physical" to "Φυσική", "virtual" to "Virtual").forEach { (value, label) ->
                    FilterChip(
                        selected = formFactor == value,
                        onClick = { formFactor = value },
                        label = { Text(label) },
                        enabled = submittedId == null,
                    )
                }
            }
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
}
