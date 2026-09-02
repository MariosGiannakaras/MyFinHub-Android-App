package app.myfinhub.android.core.ui

import app.myfinhub.android.core.auth.AuthFailureKind
import app.myfinhub.android.core.auth.AuthResult
import app.myfinhub.android.core.network.ApiFailureKind
import app.myfinhub.android.core.network.ApiResult

/**
 * Safe, user-visible operational failure. Details must never contain credentials, tokens,
 * finance payloads, PAN/CVV, PIN/TOTP values, passwords, or raw server response bodies.
 */
data class UserNotice(
    val message: String,
    val details: String,
    val diagnosticCode: String,
)

fun ApiResult.Failure.toUserNotice(operation: String): UserNotice = UserNotice(
    message = apiFailureMessage(kind),
    details = buildSafeDetails(
        operation = operation,
        kind = kind.name,
        statusCode = statusCode,
        retryable = retryable,
    ),
    diagnosticCode = diagnosticCode("API", kind.name, statusCode),
)

fun AuthResult.Failure.toUserNotice(operation: String): UserNotice = UserNotice(
    message = authFailureMessage(kind),
    details = buildSafeDetails(
        operation = operation,
        kind = kind.name,
        statusCode = statusCode,
        retryable = retryable,
    ),
    diagnosticCode = diagnosticCode("AUTH", kind.name, statusCode),
)

fun offlineUserNotice(
    operation: String,
    pendingMutation: Boolean = false,
): UserNotice = UserNotice(
    message = if (pendingMutation) {
        "Δεν υπάρχει σύνδεση. Η αλλαγή διατηρείται μέχρι να επανέλθει το δίκτυο."
    } else {
        "Δεν υπάρχει διαθέσιμη σύνδεση δικτύου."
    },
    details = buildString {
        append("Ενέργεια: ")
        append(operation)
        append("\nΚατηγορία: OFFLINE")
        append("\nΑίτημα προς server: ")
        append(if (pendingMutation) "δεν στάλθηκε" else "δεν ξεκίνησε")
        append("\nΔεν αποθηκεύονται credentials, payloads ή άλλα ευαίσθητα δεδομένα στα διαγνωστικά.")
    },
    diagnosticCode = if (pendingMutation) "MFH-NET-OFFLINE-PENDING" else "MFH-NET-OFFLINE",
)

fun unexpectedUserNotice(
    operation: String,
    throwable: Throwable,
    message: String = "Παρουσιάστηκε μη αναμενόμενο σφάλμα.",
): UserNotice = UserNotice(
    message = message,
    details = buildString {
        append("Ενέργεια: ")
        append(operation)
        append("\nΤύπος: UNEXPECTED_")
        append(throwable::class.java.simpleName.ifBlank { "ERROR" })
        append("\nΜπορείς να δοκιμάσεις ξανά. Αν επαναληφθεί, χρησιμοποίησε τον παρακάτω κωδικό.")
    },
    diagnosticCode = diagnosticCode("APP", throwable::class.java.simpleName.ifBlank { "ERROR" }, null),
)

fun apiFailureMessage(kind: ApiFailureKind): String = when (kind) {
    ApiFailureKind.BUILD_NOT_CONFIGURED -> "Η εφαρμογή δεν έχει έγκυρη ρύθμιση σύνδεσης με το MyFinHub."
    ApiFailureKind.AUTH_REQUIRED -> "Η συνεδρία έληξε. Χρειάζεται νέα σύνδεση."
    ApiFailureKind.MFA_REQUIRED -> "Χρειάζεται ξανά επαλήθευση δύο παραγόντων."
    ApiFailureKind.REVISION_CONFLICT -> "Τα δεδομένα άλλαξαν αλλού και χρειάζονται συγχρονισμό."
    ApiFailureKind.PRECONDITION_REQUIRED -> "Δεν υπάρχει έγκυρη έκδοση δεδομένων για ασφαλή αποθήκευση."
    ApiFailureKind.INVALID_DATA -> "Τα δεδομένα δεν έγιναν δεκτά από το MyFinHub."
    ApiFailureKind.RATE_LIMITED -> "Έγιναν πολλά αιτήματα. Δοκίμασε ξανά σε λίγο."
    ApiFailureKind.NETWORK -> "Δεν ήταν δυνατή η σύνδεση με το MyFinHub. Έλεγξε το δίκτυό σου."
    ApiFailureKind.SERVER -> "Το MyFinHub δεν είναι προσωρινά διαθέσιμο."
    ApiFailureKind.MALFORMED_RESPONSE -> "Το MyFinHub επέστρεψε μη αναμενόμενη απάντηση."
    ApiFailureKind.UNSUPPORTED_IN_SYNTHETIC_MODE -> "Η λειτουργία δεν είναι διαθέσιμη σε αυτό το test περιβάλλον."
}

fun authFailureMessage(kind: AuthFailureKind): String = when (kind) {
    AuthFailureKind.BUILD_NOT_CONFIGURED -> "Η εφαρμογή δεν έχει έγκυρη ρύθμιση σύνδεσης."
    AuthFailureKind.INVALID_CREDENTIALS -> "Το email ή ο κωδικός πρόσβασης δεν είναι σωστά."
    AuthFailureKind.MFA_REQUIRED -> "Χρειάζεται επαλήθευση δύο παραγόντων."
    AuthFailureKind.INVALID_MFA_CODE -> "Ο κωδικός επαλήθευσης δεν είναι σωστός ή έχει λήξει."
    AuthFailureKind.SESSION_EXPIRED -> "Η συνεδρία έληξε. Συνδέσου ξανά."
    AuthFailureKind.UNAUTHORIZED -> "Η συνεδρία δεν είναι πλέον έγκυρη."
    AuthFailureKind.RATE_LIMITED -> "Έγιναν πολλά αιτήματα σύνδεσης. Δοκίμασε ξανά σε λίγο."
    AuthFailureKind.NETWORK -> "Δεν ήταν δυνατή η σύνδεση για έλεγχο ταυτότητας."
    AuthFailureKind.SERVER -> "Η υπηρεσία σύνδεσης δεν είναι προσωρινά διαθέσιμη."
    AuthFailureKind.MALFORMED_RESPONSE -> "Η υπηρεσία σύνδεσης επέστρεψε μη αναμενόμενη απάντηση."
}

private fun buildSafeDetails(
    operation: String,
    kind: String,
    statusCode: Int?,
    retryable: Boolean,
): String = buildString {
    append("Ενέργεια: ")
    append(operation)
    append("\nΚατηγορία: ")
    append(kind)
    statusCode?.let {
        append("\nHTTP: ")
        append(it)
    }
    append("\nΕπανάληψη: ")
    append(if (retryable) "επιτρέπεται" else "δεν προτείνεται αυτόματα")
    append("\nΔεν εμφανίζονται ευαίσθητα δεδομένα ή περιεχόμενο απάντησης για λόγους ασφαλείας.")
}

private fun diagnosticCode(prefix: String, kind: String, statusCode: Int?): String = buildString {
    append("MFH-")
    append(prefix)
    append('-')
    append(kind.replace(Regex("[^A-Za-z0-9]+"), "_").uppercase())
    statusCode?.let {
        append('-')
        append(it)
    }
}
