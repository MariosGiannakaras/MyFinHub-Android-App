# Android auth and app-unlock contract

This document defines the Android-only authentication, secure-session and local app-unlock boundary for MyFinHub.

## User experience

- The Android user never enters Supabase, Vercel, API or project keys in the app.
- Project configuration is supplied at build/deployment time.
- The user performs normal account authentication when no valid server session exists.
- After a valid account session is established, session material is persisted encrypted at rest.
- Reopening the app uses local biometric unlock first when available, with a local PIN fallback.
- The local PIN only unlocks the installed app. It is never sent to the server and never substitutes for account password, TOTP/AAL2 or bearer-token validity.
- A revoked, expired or otherwise invalid server session returns the user to normal account authentication.

## Security boundaries

- Passwords and TOTP codes are never persisted.
- Access and refresh tokens are never stored in plaintext.
- A non-exportable Android Keystore AES key protects persisted session payloads.
- Ciphertext/IV metadata may live in app-private DataStore; the encryption key remains in Android Keystore.
- Authentication tokens, passwords, TOTP values, PIN values and finance payloads must not be logged.
- Biometric/PIN unlock is a local privacy gate only. Backend authorization still requires a valid owner session and AAL2.
- Bearer requests fail closed; an invalid bearer token must never fall back to browser cookies.

## Session state machine

`NoSession -> AccountAuthentication -> AAL2 -> SessionStored -> Locked -> LocalUnlock -> SessionValidation -> App`

Failure transitions:

- local biometric unavailable/failed -> offer local PIN fallback;
- local PIN invalid -> remain locked;
- encrypted session cannot be decrypted -> clear unusable session material and return to account authentication;
- refresh/access session rejected or revoked -> clear server session material and return to account authentication;
- logout -> clear persisted session material and return to `NoSession`.

## Configuration

The public repository must build without real backend values. Production values will be injected later through build/deployment configuration. Runtime UI may report that the build is not configured, but it must not present project-key input fields to the user.

Expected build-time values:

- MyFinHub API base URL;
- Supabase project URL;
- Supabase publishable key.

No service-role key, card-vault key, GitHub credential or signing password may be packaged into the application.

## Storage design

The final session store should persist only an encrypted envelope (IV + ciphertext + non-secret metadata) in app-private storage. Encryption/decryption is delegated to `SecureValueCipher` / Android Keystore. This avoids making DataStore itself a trust boundary and avoids deprecated `EncryptedSharedPreferences`.

The local PIN verifier must likewise be stored as protected local material and must not be recoverable as plaintext. PIN setup/change/reset is an Android-local flow; resetting it may require normal account re-authentication when the protected local unlock state cannot be recovered.
