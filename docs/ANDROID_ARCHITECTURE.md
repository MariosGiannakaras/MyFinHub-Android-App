# Android architecture and security boundary

Status: Phase 0 baseline  
Date: 2026-08-22

## Decision

MyFinHub Android is a native Kotlin/Jetpack Compose client. It is a new presentation/client implementation over the existing MyFinHub product and backend contracts, not a second finance system.

## Runtime stack

Baseline choices:

- Kotlin.
- Jetpack Compose + Material 3.
- Material 3 Adaptive.
- Navigation 3 / supported Compose navigation APIs with predictive-back support.
- Single-activity application unless a concrete platform requirement justifies another activity.
- ViewModel + StateFlow + lifecycle-aware collection.
- Coroutines for asynchronous work.
- OkHttp-based HTTPS client; add Retrofit or another typed adapter only if it reduces contract/error-handling complexity.
- kotlinx.serialization or an equivalent explicitly typed serializer for API DTOs.
- Android Keystore for non-extractable local encryption keys.

Exact library versions are pinned when the Gradle project is bootstrapped. Do not put floating/latest versions in reproducible builds.

## Layering

```text
Compose UI
  -> feature ViewModel/state/events
    -> repositories/use-case boundary
      -> MyFinHub API client / Auth gateway / local secure vault
        -> existing MyFinHub API + Supabase Auth/PostgreSQL
```

Suggested logical packages/modules:

- `app`: activity, root navigation, application setup.
- `core/designsystem`: theme and reusable accessible components.
- `core/model`: Android-side DTO/domain representations that mirror canonical contracts without redefining accounting semantics.
- `core/network`: HTTP, serialization, typed API errors, revision headers.
- `core/auth`: session/MFA gateway and secure token persistence.
- `core/security`: Keystore primitives and secure-window helpers.
- `data`: repositories coordinating network/session/in-memory state.
- `feature/*`: Home, Activity, Money, Plan, Insights, Settings and focused child features.

Do not create many Gradle modules before build/test performance or ownership boundaries justify them. Logical package separation is sufficient initially.

## Canonical data boundary

The existing MyFinHub backend remains authoritative for durable finance state and mutation semantics.

Android must preserve:

- one canonical Supabase/PostgreSQL state;
- server-side validation;
- configured owner UID restriction;
- mandatory AAL2 before finance access;
- PostgreSQL RLS/RPC authorization;
- optimistic `If-Match` revisions;
- stale-write conflict behavior;
- backup/import/audit semantics;
- card metadata vs secret-vault separation.

Android must not introduce a second canonical Room/SQLite finance database. A future offline cache would require an explicit ADR covering encryption, stale-state conflict resolution, secret exposure, and reconciliation. The Phase 0 default is server source of truth + in-memory loaded state.

## Native authentication

The current web/desktop API obtains Supabase access/refresh tokens from HttpOnly cookies. Android should not emulate a browser cookie jar merely to fit this model.

Required backend evolution in the main MyFinHub repository:

- Continue to accept existing HttpOnly cookie sessions for web/desktop.
- Add a native-client authentication path accepting `Authorization: Bearer <Supabase access JWT>`.
- Resolve/refresh native sessions through an explicit Android auth flow, not ambient same-origin cookies.
- Preserve `isOwner(accessToken)` and `aal2` checks before finance access.
- Preserve RLS/RPC access using the authenticated user's JWT.
- Do not relax same-origin checks for browser-cookie mutations. Native bearer mutations use a separate request-authentication branch where CSRF/same-origin protections are not applicable because credentials are not ambient cookies.

The exact auth transport/API change belongs in `MariosGiannakaras/MyFinHub` and must be reviewed there before Android feature integration.

## Supabase Auth client choice

Supabase documents Kotlin support, including MFA/TOTP, through `supabase-kt`, which is community-maintained. Do not couple the whole application to that library.

Create an `AuthGateway` interface so the implementation may be:

1. `supabase-kt` behind the gateway, if current dependency/security review is acceptable; or
2. direct Supabase Auth REST calls over the shared HTTP stack.

Finance data access does not use direct client-side PostgREST as a shortcut around the existing MyFinHub API.

## Session persistence

Never store access/refresh tokens in plaintext SharedPreferences, logs, saved-state bundles, screenshots, or test reports.

Baseline design:

- Generate a non-extractable AES key in Android Keystore.
- Store only encrypted token/session ciphertext plus non-secret metadata in app-private storage.
- Use AES-GCM with unique nonces/IVs and authenticated additional data where useful.
- Clear persisted session material on explicit logout/revocation failure according to the auth contract.
- Treat process memory as sensitive; do not expose token values through observable UI state.

`EncryptedSharedPreferences` is not selected as a new architecture dependency because current Android security APIs in `androidx.security:security-crypto` are deprecated; use Keystore primitives directly.

## Finance state synchronization

Expected Android data flow:

1. Authenticate and reach AAL2.
2. `GET /api/data` using native bearer auth.
3. Keep returned finance document + revision in application memory/state.
4. User mutation produces the canonical mutable-state payload.
5. `PUT /api/data` with the expected revision in `If-Match`.
6. On success, accept returned revision/timestamp.
7. On conflict, do not overwrite. Enter an explicit stale/conflict state and reload/reconcile according to the same product semantics as web/desktop.

Do not add periodic full-document polling merely to simulate realtime. Refresh occurs on meaningful lifecycle/user events and after writes; future realtime transport requires an explicit design decision.

## Card secrets

### PAN / expiry

- Remain in the existing encrypted server vault.
- Android calls the authenticated MyFinHub card-secret API.
- `CARD_VAULT_KEY` never exists in Android code, APK resources, Gradle config, CI public logs, or source history.
- Reveal/edit flows should consider secure-window flags and biometric confirmation as an additional local privacy control, without pretending biometrics replace server AAL2 authorization.

### CVV

CVV remains device-local and never reaches MyFinHub/Supabase.

Baseline native vault:

- Keystore-backed AES-GCM key, preferably requiring user authentication for reveal if device capability/policy supports it.
- ciphertext stored only in app-private local storage.
- AAD binds the encrypted value to the card identity and local vault version.
- no backup/export/sync of CVV.
- archiving a card removes its local CVV record, matching the existing product boundary.

## Network security

- HTTPS only for production API/Supabase endpoints.
- No cleartext traffic in production manifest/network-security config.
- Follow platform trust store/certificate validation. Do not add brittle certificate pinning without an explicit threat model and rotation plan.
- Apply strict request size/timeouts and typed error handling.
- Do not log request/response bodies containing finance or auth data.
- Public repository configuration may contain values intended to be public (for example a Supabase publishable key) only after confirming the existing backend security model does not treat them as secrets.

## UI privacy

Evaluate `FLAG_SECURE` or equivalent secure-window behavior for screens that reveal full card secrets. Do not apply it globally without UX evaluation, because users may legitimately need screenshots of non-secret reports; scope it to sensitive reveal contexts.

Android recent-task thumbnails must not expose currently revealed PAN/expiry/CVV. Sensitive Compose semantics must not read full secrets aloud unless the user explicitly requests a reveal and the flow is designed for that behavior.

## Process/lifecycle

- UI state is restored where safe using saved state; finance documents/tokens should not be indiscriminately serialized into saved-state bundles.
- Network requests are lifecycle-aware but business operations should survive normal recomposition.
- Process death returns to a secure session restoration path rather than persisting arbitrary full UI state containing secrets.

## Performance

- Long transaction/history screens use lazy layouts and stable keys.
- Derived finance calculations should reuse canonical selectors/semantics from the main product where possible and be memoized/cached at the repository/view-model boundary rather than recomputed per composable.
- Release builds use R8.
- Generate app-specific Baseline Profiles for startup/Home/Activity/Quick Entry and validate with Macrobenchmark.

## Backend change gate

Before feature implementation that reads/writes real MyFinHub data, the main repository must have a reviewed native-bearer API contract and tests demonstrating:

- cookie clients unchanged;
- bearer client valid owner+AAL2 accepted;
- invalid/expired token rejected;
- AAL1 rejected;
- non-owner rejected;
- mutation same-origin remains required for cookie clients;
- native bearer mutation accepted without weakening cookie CSRF protection;
- `If-Match` conflict behavior unchanged;
- card-secret endpoint preserves the same authorization and secret validation.

Until that gate exists, Android UI/prototypes use synthetic data/fake repositories only.