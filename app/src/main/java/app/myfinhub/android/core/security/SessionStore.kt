package app.myfinhub.android.core.security

import app.myfinhub.android.core.auth.AuthSession

interface SessionStore {
    suspend fun load(): AuthSession?
    suspend fun save(session: AuthSession)
    suspend fun clear()
}
