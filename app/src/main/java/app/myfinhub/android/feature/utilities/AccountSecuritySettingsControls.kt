package app.myfinhub.android.feature.utilities

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import app.myfinhub.android.core.network.WebAccountSecurityChange
import app.myfinhub.android.core.network.normalizedAccountSecurityEmail
import app.myfinhub.android.core.network.validAccountSecurityPassword
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubSpacing

/** Only transient text fields: never rememberSaveable, preferences, logs or offline queue. */
@Composable
fun AccountSecuritySettingsControls(
    enabled: Boolean,
    saving: Boolean,
    feedback: String?,
    onSubmit: (WebAccountSecurityChange) -> Unit,
) {
    var mode by remember { mutableStateOf<String?>(null) }
    var email by remember(mode) { mutableStateOf("") }
    var currentPassword by remember(mode) { mutableStateOf("") }
    var newPassword by remember(mode) { mutableStateOf("") }
    var confirmation by remember(mode) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.xs)) {
        Text("Στοιχεία σύνδεσης", style = MaterialTheme.typography.titleMedium)
        Text("Οι αλλαγές αποστέλλονται αποκλειστικά στον ασφαλή server με AAL2. Δεν αποθηκεύονται στη συσκευή.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        MyFinHubOutlinedAction(
            label = "Αλλαγή email",
            modifier = Modifier.fillMaxWidth().testTag("s9_account_email_open"),
            onClick = { mode = "email" },
            enabled = enabled && !saving,
        )
        MyFinHubOutlinedAction(
            label = "Αλλαγή κωδικού",
            modifier = Modifier.fillMaxWidth().testTag("s9_account_password_open"),
            onClick = { mode = "password" },
            enabled = enabled && !saving,
        )
        if (!enabled) Text("Για αλλαγή χρειάζεται ενεργή, online συνεδρία με επαλήθευση AAL2 και ορατά προσωπικά δεδομένα.",
            style = MaterialTheme.typography.bodySmall)
        if (saving) Text("Αναμονή επιβεβαίωσης από server…",
            style = MaterialTheme.typography.bodySmall)
        feedback?.let { Text(it, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }

    if (mode != null && enabled && !saving) {
        val emailChange = mode == "email"
        val allowed = if (emailChange) normalizedAccountSecurityEmail(email) != null
            else validAccountSecurityPassword(currentPassword, newPassword) &&
                newPassword == confirmation
        fun dismiss() {
            email = ""
            currentPassword = ""
            newPassword = ""
            confirmation = ""
            mode = null
        }
        AlertDialog(
            onDismissRequest = ::dismiss,
            title = { Text(if (emailChange) "Αλλαγή email" else "Αλλαγή κωδικού") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.sm)) {
                    if (emailChange) {
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it.take(254) },
                            label = { Text("Νέο email") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("s9_account_email_input"),
                        )
                        Text("Η αλλαγή ενδέχεται να χρειάζεται επιβεβαίωση από το νέο email.",
                            style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(
                            value = currentPassword,
                            onValueChange = { currentPassword = it.take(512) },
                            label = { Text("Τρέχων κωδικός") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("s9_account_current_password"),
                        )
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it.take(512) },
                            label = { Text("Νέος κωδικός") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("s9_account_new_password"),
                        )
                        OutlinedTextField(
                            value = confirmation,
                            onValueChange = { confirmation = it.take(512) },
                            label = { Text("Επανάληψη νέου κωδικού") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("s9_account_confirm_password"),
                        )
                        Text("12–512 χαρακτήρες με πεζό, κεφαλαίο, αριθμό και σύμβολο. Διαφορετικός από τον τρέχοντα.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Text("Επιβεβαίωση: τα στοιχεία θα αλλάξουν στον server, όχι τοπικά.",
                        style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = allowed,
                    modifier = Modifier.testTag("s9_account_security_confirm"),
                    onClick = {
                        val change = if (emailChange) {
                            WebAccountSecurityChange.Email(normalizedAccountSecurityEmail(email)!!)
                        } else {
                            WebAccountSecurityChange.Password(currentPassword, newPassword)
                        }
                        dismiss()
                        onSubmit(change)
                    },
                ) { Text("Επιβεβαίωση αλλαγής") }
            },
            dismissButton = { TextButton(onClick = ::dismiss) { Text("Ακύρωση") } },
        )
    }
}
