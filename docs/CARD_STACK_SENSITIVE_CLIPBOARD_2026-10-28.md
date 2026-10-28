# Card Stack sensitive clipboard lifecycle — 28 Oct 2026

The mixed debit/credit HTML-reference stack can display and copy full PAN, expiration and CVV only while its existing `CardSecretUiState.Revealed` matches the active card. AAL2/owner-vault authorization continues to be handled by the existing secure card state; no direct server auth changes or new secret storage.

The stack previously used `LocalClipboardManager.setText` without sensitive metadata, timeout or revocation. It now shares `SensitiveCardClipboard`, which marks Android copies sensitive, schedules 30-second main-looper expiry and clears only unchanged app-owned copies. The stack itself revokes on active card switch, loss of reveal, disposal/navigation and lifecycle `ON_STOP`; another application's newer copy must remain intact.

Tests use the production controller with an observable in-memory clipboard port because hosted Android can restrict clipboard readback; they verify AAL loss, switching, disposal and foreign clipboard ownership, alongside the secure details tests. No Web/backend/schema, real finance writes, SMS or release/signing change.
