package app.myfinhub.android.core.security

interface LocalPinVerifier {
    suspend fun isEnrolled(): Boolean
    suspend fun enroll(pin: CharArray)
    suspend fun verify(pin: CharArray): Boolean
    suspend fun clear()
}
