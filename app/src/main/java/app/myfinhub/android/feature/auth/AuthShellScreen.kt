package app.myfinhub.android.feature.auth

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import kotlinx.coroutines.delay

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
    onRetrySharedLock: () -> Unit = {},
    onVerifySharedPin: (CharArray) -> Unit = { it.fill('\u0000') },
    onRecoverSharedPin: (String, CharArray, CharArray, CharArray) -> Unit = { _, password, code, pin ->
        password.fill('\u0000'); code.fill('\u0000'); pin.fill('\u0000')
    },
    onSharedLogout: () -> Unit = {},
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
        is AuthShellUiState.SharedLocked -> SharedLockedScreen(
            state, onRetrySharedLock, onVerifySharedPin, onRecoverSharedPin, onSharedLogout,
        )
        is AuthShellUiState.Ready -> readyContent()
    }
}

@Composable
private fun SharedLockedScreen(
    state: AuthShellUiState.SharedLocked,
    onRetry: () -> Unit,
    onVerify: (CharArray) -> Unit,
    onRecover: (String, CharArray, CharArray, CharArray) -> Unit,
    onLogout: () -> Unit,
) {
    var pin by remember(state.session) { mutableStateOf("") }
    var recovery by remember(state.session) { mutableStateOf(false) }
    var email by remember(state.session) { mutableStateOf("") }
    var password by remember(state.session) { mutableStateOf("") }
    var code by remember(state.session) { mutableStateOf("") }
    var newPin by remember(state.session) { mutableStateOf("") }
    var confirmation by remember(state.session) { mutableStateOf("") }
    var retryRemaining by remember(state.retryAfterMillis, state.busy) { mutableStateOf(state.retryAfterMillis) }
    LaunchedEffect(state.retryAfterMillis, state.busy) {
        while (retryRemaining > 0) {
            delay(minOf(1_000L, retryRemaining))
            retryRemaining = (retryRemaining - 1_000L).coerceAtLeast(0)
        }
    }
    fun clearRecovery() {
        email = ""; password = ""; code = ""; newPin = ""; confirmation = ""
    }
    AuthSurface {
        Text("Κοινό PIN MyFinHub", style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Text(
            if (state.requiresPin) "Το κοινό PIN προστατεύει την πρόσβαση στα οικονομικά δεδομένα και είναι διαφορετικό από το τοπικό PIN."
            else "Απαιτείται έλεγχος της κοινής προστασίας πριν εμφανιστούν οικονομικά δεδομένα.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (state.busy) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (state.requiresPin && !recovery) {
            SharedPinField("Κοινό PIN (4 ψηφία)", pin) { pin = it }
            if (retryRemaining > 0) Text("Δοκίμασε ξανά σε ${formatRetrySeconds(retryRemaining)}.", color = MaterialTheme.colorScheme.error)
            MyFinHubPrimaryAction(
                label = "Ξεκλείδωμα",
                onClick = {
                    val chars = pin.toCharArray()
                    pin = ""
                    onVerify(chars)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = pin.length == 4 && retryRemaining == 0L,
                icon = null,
            )
            MyFinHubOutlinedAction(
                label = "Ξέχασα το κοινό PIN",
                onClick = { pin = ""; recovery = true },
                modifier = Modifier.fillMaxWidth(),
                icon = null,
            )
        } else if (state.requiresPin && recovery) {
            Text("Ανάκτηση με νέο έλεγχο email, κωδικού και TOTP. Θα ακυρωθούν τα προηγούμενα ξεκλειδώματα.")
            MyFinHubOutlinedField(
                value = email, onValueChange = { email = it.take(254) }, label = "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            )
            MyFinHubOutlinedField(
                value = password, onValueChange = { password = it.take(512) }, label = "Κωδικός λογαριασμού",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            )
            MyFinHubOutlinedField(
                value = code, onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) },
                label = "Κωδικός TOTP",
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Next),
            )
            SharedPinField("Νέο κοινό PIN", newPin) { newPin = it }
            SharedPinField("Επιβεβαίωση νέου PIN", confirmation) { confirmation = it }
            MyFinHubPrimaryAction(
                label = "Αλλαγή κοινού PIN",
                onClick = {
                    val submittedEmail = email
                    val submittedPassword = password.toCharArray()
                    val submittedCode = code.toCharArray()
                    val submittedPin = newPin.toCharArray()
                    clearRecovery()
                    recovery = false
                    onRecover(submittedEmail, submittedPassword, submittedCode, submittedPin)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = email.isNotBlank() && password.length >= 8 && code.length == 6 &&
                    newPin.length == 4 && newPin == confirmation,
                icon = null,
            )
            MyFinHubOutlinedAction(
                label = "Επιστροφή στο PIN", onClick = { clearRecovery(); recovery = false },
                modifier = Modifier.fillMaxWidth(), icon = null,
            )
        }
        state.message?.let { ErrorMessage(it) }
        if (!state.busy && !recovery) {
            MyFinHubOutlinedAction(
                label = "Νέος έλεγχος προστασίας", onClick = { pin = ""; onRetry() },
                modifier = Modifier.fillMaxWidth(), icon = null,
            )
        }
        if (!state.busy) {
            MyFinHubOutlinedAction(
                label = "Αποσύνδεση", onClick = { pin = ""; clearRecovery(); onLogout() },
                modifier = Modifier.fillMaxWidth(), icon = null,
            )
        }
    }
}

@Composable
private fun SharedPinField(label: String, value: String, onValueChange: (String) -> Unit) {
    MyFinHubOutlinedField(
        value = value,
        onValueChange = { onValueChange(it.filter { c -> c in '0'..'9' }.take(4)) },
        label = label,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
    )
}

@Composable
private fun LoadingScreen() {
    AuthSurface {
        Text("MyFinHub", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Έλεγχος ασφαλούς συνεδρίας…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
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
        Text("Επαλήθευση λογαριασμού", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("Άνοιξε την εφαρμογή επαλήθευσης και πληκτρολόγησε τον εξαψήφιο κωδικό.")
        MyFinHubOutlinedField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = "Εξαψήφιος κωδικός",
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
            "Διάλεξε 4–12 ψηφία. Το PIN ξεκλειδώνει μόνο αυτή την εφαρμογή και χρησιμοποιείται όταν δεν είναι διαθέσιμα τα βιομετρικά.",
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
    var retryRemainingMillis by remember(state.pinStatus.retryAfterMillis) {
        mutableStateOf(state.pinStatus.retryAfterMillis)
    }
    LaunchedEffect(state.pinStatus.retryAfterMillis) {
        retryRemainingMillis = state.pinStatus.retryAfterMillis
        while (retryRemainingMillis > 0L) {
            delay(minOf(1_000L, retryRemainingMillis))
            retryRemainingMillis = (retryRemainingMillis - 1_000L).coerceAtLeast(0L)
        }
    }
    val pinAllowedNow = state.pinStatus.allowed || retryRemainingMillis <= 0L
    AuthSurface {
        Text("Το MyFinHub είναι κλειδωμένο", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text(
            "Ξεκλείδωσε την εφαρμογή με βιομετρικά ή με το τοπικό PIN.",
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
            if (!pinAllowedNow) {
                Text(
                    "Πολλές αποτυχημένες προσπάθειες. Δοκίμασε ξανά σε ${formatRetrySeconds(retryRemainingMillis)}.",
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
                enabled = pin.length >= 4 && pinAllowedNow,
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
                    .imePadding()
                    .navigationBarsPadding()
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

internal fun formatRetrySeconds(retryAfterMillis: Long): String {
    val seconds = ((retryAfterMillis.coerceAtLeast(0L) + 999L) / 1_000L).coerceAtLeast(1L)
    return if (seconds == 1L) "1 δευτερόλεπτο" else "$seconds δευτερόλεπτα"
}
