package app.myfinhub.android.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.fragment.app.FragmentActivity
import app.myfinhub.android.core.security.AndroidBiometricGateway
import app.myfinhub.android.core.security.BiometricCapability
import app.myfinhub.android.core.security.BiometricGateway
import app.myfinhub.android.core.security.BiometricResult
import app.myfinhub.android.designsystem.MyFinHubBrandMark
import app.myfinhub.android.designsystem.MyFinHubBrandMode
import app.myfinhub.android.designsystem.MyFinHubDesignMetrics
import app.myfinhub.android.designsystem.MyFinHubOutlinedAction
import app.myfinhub.android.designsystem.MyFinHubOutlinedField
import app.myfinhub.android.designsystem.MyFinHubPrimaryAction
import app.myfinhub.android.designsystem.MyFinHubSpacing

@Composable
fun AuthShellScreen(
    state: AuthShellUiState,
    onSignIn: (String, CharArray) -> Unit,
    onSubmitTotp: (CharArray) -> Unit,
    onEnrollPin: (CharArray, CharArray) -> Unit,
    onVerifyPin: (CharArray) -> Unit,
    onBiometricSuccess: () -> Unit,
    onPinFallbackRequested: () -> Unit,
    readyContent: @Composable () -> Unit,
    biometricGateway: BiometricGateway = remember { AndroidBiometricGateway() },
) {
    when (state) {
        AuthShellUiState.Loading -> LoadingScreen()
        is AuthShellUiState.Unconfigured -> AuthMessageScreen(
            title = "Η εφαρμογή δεν είναι έτοιμη",
            message = state.message,
        )
        is AuthShellUiState.Login -> LoginScreen(state.message, onSignIn)
        is AuthShellUiState.Mfa -> MfaScreen(state.message, onSubmitTotp)
        is AuthShellUiState.PinEnrollment -> PinEnrollmentScreen(state.message, onEnrollPin)
        is AuthShellUiState.Locked -> LockedScreen(
            state = state,
            gateway = biometricGateway,
            onBiometricSuccess = onBiometricSuccess,
            onPinFallbackRequested = onPinFallbackRequested,
            onVerifyPin = onVerifyPin,
        )
        is AuthShellUiState.Ready -> readyContent()
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AuthMessageScreen(title: String, message: String) {
    AuthSurface {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoginScreen(
    message: String?,
    onSignIn: (String, CharArray) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AuthSurface {
        Text("Σύνδεση στο MyFinHub", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Συνδέσου με το email και τον κωδικό του λογαριασμού σου.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MyFinHubOutlinedField(
            value = email,
            onValueChange = { email = it },
            label = "Email",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next,
            ),
        )
        MyFinHubOutlinedField(
            value = password,
            onValueChange = { password = it },
            label = "Κωδικός",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
        )
        message?.let { ErrorMessage(it) }
        MyFinHubPrimaryAction(
            label = "Σύνδεση",
            onClick = {
                val chars = password.toCharArray()
                password = ""
                onSignIn(email, chars)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = email.isNotBlank() && password.isNotEmpty(),
            icon = null,
        )
    }
}

@Composable
private fun MfaScreen(
    message: String?,
    onSubmitTotp: (CharArray) -> Unit,
) {
    var code by remember { mutableStateOf("") }

    AuthSurface {
        Text("Επαλήθευση δύο παραγόντων", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Άνοιξε την εφαρμογή authenticator και πληκτρολόγησε τον τρέχοντα κωδικό TOTP.")
        MyFinHubOutlinedField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = "Κωδικός TOTP",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword,
                imeAction = ImeAction.Done,
            ),
            visualTransformation = PasswordVisualTransformation(),
        )
        message?.let { ErrorMessage(it) }
        MyFinHubPrimaryAction(
            label = "Επαλήθευση",
            onClick = {
                val chars = code.toCharArray()
                code = ""
                onSubmitTotp(chars)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = code.length == 6,
            icon = null,
        )
    }
}

@Composable
private fun PinEnrollmentScreen(
    message: String?,
    onEnrollPin: (CharArray, CharArray) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }

    AuthSurface {
        Text("Τοπικό PIN", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Διάλεξε 4–12 ψηφία για fallback όταν δεν είναι διαθέσιμα τα βιομετρικά. Το PIN ξεκλειδώνει μόνο την εφαρμογή και δεν αντικαθιστά το TOTP.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PinField("PIN", pin, ImeAction.Next) { pin = it }
        PinField("Επιβεβαίωση PIN", confirmation, ImeAction.Done) { confirmation = it }
        message?.let { ErrorMessage(it) }
        MyFinHubPrimaryAction(
            label = "Αποθήκευση PIN",
            onClick = {
                val first = pin.toCharArray()
                val second = confirmation.toCharArray()
                pin = ""
                confirmation = ""
                onEnrollPin(first, second)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = pin.length >= 4 && confirmation.length >= 4,
            icon = null,
        )
    }
}

@Composable
private fun LockedScreen(
    state: AuthShellUiState.Locked,
    gateway: BiometricGateway,
    onBiometricSuccess: () -> Unit,
    onPinFallbackRequested: () -> Unit,
    onVerifyPin: (CharArray) -> Unit,
) {
    val activity = LocalActivity.current as? FragmentActivity
    val capability = activity?.let(gateway::capability) ?: BiometricCapability.UNAVAILABLE
    var promptAttempted by remember(state.session.userId) { mutableStateOf(false) }

    fun showBiometricPrompt() {
        val host = activity ?: return
        if (capability != BiometricCapability.AVAILABLE) {
            onPinFallbackRequested()
            return
        }
        gateway.authenticate(host) { result ->
            when (result) {
                BiometricResult.Success -> onBiometricSuccess()
                BiometricResult.PinFallbackRequested -> onPinFallbackRequested()
                BiometricResult.Cancelled -> Unit
                is BiometricResult.Error -> Unit
            }
        }
    }

    LaunchedEffect(state.session.userId, capability, state.showPin) {
        if (!promptAttempted && capability == BiometricCapability.AVAILABLE && !state.showPin) {
            promptAttempted = true
            showBiometricPrompt()
        }
    }

    var pin by remember { mutableStateOf("") }
    AuthSurface {
        Text("Το MyFinHub είναι κλειδωμένο", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Η αποθηκευμένη συνεδρία θα ελεγχθεί ξανά στον server μετά το τοπικό ξεκλείδωμα.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (capability == BiometricCapability.AVAILABLE) {
            MyFinHubPrimaryAction(
                label = "Ξεκλείδωμα με βιομετρικά",
                onClick = ::showBiometricPrompt,
                modifier = Modifier.fillMaxWidth(),
                icon = null,
            )
        }
        if (capability != BiometricCapability.AVAILABLE || state.showPin) {
            PinField("PIN εφαρμογής", pin, ImeAction.Done) { pin = it }
            state.message?.let { ErrorMessage(it) }
            if (!state.pinStatus.allowed) {
                Text(
                    "Το PIN fallback είναι προσωρινά κλειδωμένο.",
                    color = MaterialTheme.colorScheme.error,
                )
            }
            MyFinHubPrimaryAction(
                label = "Ξεκλείδωμα με PIN",
                onClick = {
                    val chars = pin.toCharArray()
                    pin = ""
                    onVerifyPin(chars)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length >= 4 && state.pinStatus.allowed,
                icon = null,
            )
        } else {
            MyFinHubOutlinedAction(
                label = "Χρήση PIN",
                onClick = onPinFallbackRequested,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PinField(
    label: String,
    value: String,
    imeAction: ImeAction,
    onValueChange: (String) -> Unit,
) {
    MyFinHubOutlinedField(
        value = value,
        onValueChange = { next -> onValueChange(next.filter(Char::isDigit).take(12)) },
        label = label,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.NumberPassword,
            imeAction = imeAction,
        ),
        visualTransformation = PasswordVisualTransformation(),
    )
}

@Composable
private fun ErrorMessage(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AuthSurface(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(
                horizontal = MyFinHubSpacing.xl,
                vertical = MyFinHubSpacing.xxl,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = MyFinHubDesignMetrics.authContentMaxWidth)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(MyFinHubSpacing.md),
            ) {
                MyFinHubBrandMark(
                    mode = MyFinHubBrandMode.Lockup,
                    iconSize = MyFinHubDesignMetrics.authBrandMarkSize,
                    subtitle = "Smart. Clear. In Control.",
                )
                content()
            }
        }
    }
}
