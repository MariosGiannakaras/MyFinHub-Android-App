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

## Shared server app-lock compatibility (issue #345)

The existing Android local biometric/PIN gate is independent of the optional four-ASCII-digit
server privacy PIN. On authenticated AAL2 readiness, Android checks the authoritative
`GET /api/auth/app-lock` status before mounting finance. A verified enabled status requires
explicit shared PIN verification; only the server's valid short-lived ticket grants access.
An unavailable, malformed, unknown or rejected status must not be interpreted as disabled.
Recovery is a separate explicit password + fresh TOTP operation; its success never unlocks
finance, and the new PIN must be verified. Android does not configure or enable shared PIN.

- All protected native finance, history, account/device metadata and card-vault requests share
  one process-memory authorization boundary. The unlock header is bound to the exact authenticated
  owner/bearer/AAL2 and publication generation; no cookie fallback or redirects. The same grant
  is required across the history precondition and finance PUT. HTTP 423 and explicit server
  app-lock-unavailability responses revoke current access without clearing encrypted auth
  credentials or automatically reissuing a financial write. Old responses cannot reopen finance
  or revoke a newer grant.
- Foreground user interaction may renew a live lease with the server `touch` action. The app
  independently enforces a conservative monotonic idle deadline; no background heartbeat.
  Background, session replacement, failed touch, logout, or loss of network with a live ticket
  removes local presentation/network authority. Background/logout attempt best-effort server
  lock; server expiry and existing device/session checks remain authoritative.
- Offline policy: an authoritative **disabled** response is retained only in process memory
  for its exact bearer. It permits local-only encrypted-cache access after local unlock.
  It supplies no online grant; reconnect revalidates auth and authoritative shared-PIN status
  before sync. Cold offline start, changed bearer, unknown status, or previously enabled PIN
  cannot establish shared-PIN authority. An offline device cannot observe a remote policy change;
  local-only access under the previously confirmed disabled proof is not a fresh server assertion.
  The existing encrypted cache and pending queue are preserved; no new cache is created.
- PIN, recovery credentials and bearer tickets never enter persistence, telemetry, diagnostics,
  or navigation arguments. UI uses transient, masked inputs. No live PIN activation, backend
  migration or production financial test is authorized by this implementation.

### Acceptance gates before shared PIN activation or release

The Android JVM and hosted phone tests exercise the real client against MockWebServer and the
native lock/recovery presentation. These are not a substitute for isolated Web+Supabase
integration. In a disposable owner test environment, verify mandatory owner+AAL2 and active-device
policy; disabled, enabled, correct/wrong/rate-limited PIN; exact bearer header, expired/missing/
forged ticket (423), failed touch, lock, fresh password+TOTP recovery, direct JWT RLS denial
when locked, and relock on background/session change/network loss. Verify Android's cold
offline behavior and existing encrypted offline queue on the physical S24 before release.
Do not test with the owner's actual finance data or enable the production global PIN.
