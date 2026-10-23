package app.myfinhub.android.feature.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.myfinhub.android.designsystem.MyFinHubSpacing
import java.util.Locale

/**
 * Deliberately excludes PAN, expiry, CVV, last4, card kind, vault identity, and event history.
 * Those identities cannot be silently changed from a financial-profile editor.
 */
data class CardProfileEditRequest(
    val cardId: String,
    val bankId: String,
    val nickname: String,
    val network: String,
    val formFactor: String,
    val creditLimit: Double?,
)

@Composable
fun CanonicalCardProfileEditor(
    card: MoneyCard,
    providerOptions: List<Pair<String, String>>,
    mutationBlocked: Boolean,
    onDismiss: () -> Unit,
    onSave: (CardProfileEditRequest) -> Unit,
) {
    var nickname by rememberSaveable(card.id) { mutableStateOf(card.nickname) }
    var bankId by rememberSaveable(card.id) { mutableStateOf(card.bankId) }
    var network by rememberSaveable(card.id) { mutableStateOf(card.network.lowercase(Locale.ROOT)) }
    var formFactor by rememberSaveable(card.id) {
        mutableStateOf(card.formFactor?.lowercase(Locale.ROOT) ?: "physical")
    }
    var creditLimitText by rememberSaveable(card.id) {
        mutableStateOf(card.limit?.toString().orEmpty())
    }
    var bankMenu by remember { mutableStateOf(false) }
    var networkMenu by remember { mutableStateOf(false) }
    var formFactorMenu by remember { mutableStateOf(false) }
    val isCredit = card.canonicalKind == "credit" ||
        card.kind.contains("Πιστωτ", ignoreCase = true)
    val limit = creditLimitText.trim().replace(',', '.').takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val limitValid = !isCredit || creditLimitText.isBlank() ||
        (limit != null && limit.isFinite() && limit > 0.0)
    val knownBanks = (
        providerOptions + listOf(bankId to bankId)
    ).distinctBy { it.first }.filter { it.first.isNotBlank() }
    val canSave = !mutationBlocked && card.id.isNotBlank() &&
        nickname.trim().isNotBlank() && nickname.trim().length <= 500 &&
        bankId.isNotBlank() && network in setOf("visa", "mastercard", "other") &&
        formFactor in setOf("physical", "virtual") && limitValid

    AlertDialog(
        onDismissRequest = { if (!mutationBlocked) onDismiss() },
        title = { Text("Επεξεργασία προφίλ κάρτας") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm),
            ) {
                Text(
                    "Το ιστορικό, ο τύπος κάρτας και τα κρυπτογραφημένα στοιχεία " +
                        "(αριθμός, λήξη, CVV) παραμένουν ανέπαφα.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    enabled = !mutationBlocked,
                    label = { Text("Όνομα κάρτας") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("card_profile_name"),
                )
                TextButton(
                    onClick = { bankMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_profile_bank"),
                ) {
                    Text("Εκδότης: " +
                        (knownBanks.firstOrNull { it.first == bankId }?.second ?: bankId))
                }
                DropdownMenu(expanded = bankMenu, onDismissRequest = { bankMenu = false }) {
                    knownBanks.forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { bankId = id; bankMenu = false },
                        )
                    }
                }
                TextButton(
                    onClick = { networkMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_profile_network"),
                ) { Text("Δίκτυο: " + network.uppercase(Locale.ROOT)) }
                DropdownMenu(expanded = networkMenu, onDismissRequest = { networkMenu = false }) {
                    listOf("visa" to "Visa", "mastercard" to "Mastercard",
                        "other" to "Άλλο").forEach { (id, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { network = id; networkMenu = false },
                        )
                    }
                }
                TextButton(
                    onClick = { formFactorMenu = true }, enabled = !mutationBlocked,
                    modifier = Modifier.fillMaxWidth().testTag("card_profile_form_factor"),
                ) { Text(if (formFactor == "virtual") "Εικονική κάρτα" else "Φυσική κάρτα") }
                DropdownMenu(expanded = formFactorMenu, onDismissRequest = { formFactorMenu = false }) {
                    listOf("physical" to "Φυσική", "virtual" to "Εικονική")
                        .forEach { (id, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { formFactor = id; formFactorMenu = false },
                            )
                        }
                }
                if (isCredit) {
                    OutlinedTextField(
                        value = creditLimitText,
                        onValueChange = { creditLimitText = it },
                        enabled = !mutationBlocked,
                        label = { Text("Πιστωτικό όριο (προαιρετικό)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("card_profile_limit"),
                    )
                }
                if (!limitValid) {
                    Text("Το όριο πρέπει να είναι θετικός αριθμός.",
                        color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                modifier = Modifier.testTag("card_profile_save"),
                onClick = {
                    onSave(CardProfileEditRequest(
                        cardId = card.id,
                        bankId = bankId,
                        nickname = nickname.trim(),
                        network = network,
                        formFactor = formFactor,
                        creditLimit = if (isCredit) limit else null,
                    ))
                },
            ) { Text("Αποθήκευση") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !mutationBlocked) { Text("Ακύρωση") }
        },
    )
}
