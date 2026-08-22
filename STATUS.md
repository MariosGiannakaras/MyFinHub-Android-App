# MyFinHub Android status

## 2026-08-22 — Phase 0 research foundation

The repository has been bootstrapped for a native Android client. No Android feature UI or production backend integration has been implemented yet.

### Settled baseline decisions

- Native Kotlin + Jetpack Compose; no WebView/PWA shell/browser launcher.
- Material 3 + Material 3 Adaptive.
- Mobile-first redesign that preserves MyFinHub product capability and finance semantics rather than desktop layout geometry.
- Window-size/posture adaptive layouts; no orientation/aspect-ratio lock.
- Compact primary navigation uses a small stable destination set; initial prototype hypothesis is Home / Activity / Money / Plan / Insights, with Settings outside the bottom bar.
- Complex desktop tables become task-specific compact lists + detail; wider windows may use list-detail/supporting panes.
- Quick Entry remains a first-class fast action.
- Accessibility, predictive back, edge-to-edge, 48dp targets, large font behavior, and non-gesture-only actions are implementation gates.
- Existing MyFinHub backend/Supabase remains canonical; Android does not create a second finance database.
- Android requires a reviewed native bearer-auth path in the main MyFinHub backend while preserving existing web/desktop cookie + same-origin security.
- PAN/expiry remain server-vault-backed; CVV remains device-local with Android Keystore-backed encryption.
- Public source repository is intentional for GitHub Actions economics.
- Signed private APKs must not be uploaded as public-repo Releases or workflow artifacts.
- Recommended private distribution path: release job in the public source repo signs on an ephemeral runner and uploads directly to a separate private GitHub distribution repository.

### Phase 0 documentation

- `AGENTS.md` — durable engineering/security/workflow rules.
- `docs/RESEARCH_MOBILE_UX_2026.md` — current Android standards and product-example research.
- `docs/MOBILE_DESIGN_CONTRACT.md` — MyFinHub-specific mobile interaction contract and prototype targets.
- `docs/ANDROID_ARCHITECTURE.md` — native client, auth, storage, sync, and security boundaries.
- `docs/GITHUB_DELIVERY.md` — GitHub Actions, testing, signing, private distribution, and unavoidable non-GitHub steps.

### External constraints identified in advance

Most engineering/release work can remain in GitHub, but four activities cannot be honestly treated as fully GitHub-only:

1. the phone must authorize/install an APK;
2. Android's 2027 global developer-verification model may make a one-time Android Developer Console limited-distribution/device-registration flow the cleanest personal-use path;
3. the long-lived Android signing key needs a recoverable offline encrypted backup in addition to its GitHub Actions CI copy;
4. final real-device/TalkBack/biometric/install smoke is valuable because emulator CI cannot perfectly model hardware/OEM/human behavior.

### Git state

- `main`: bootstrap commit only.
- `develop`: created from bootstrap.
- `research/mobile-ux-2026`: Phase 0 research/documentation branch.
- Tracker: issue #1.

### Next gate

Before production data integration, implement and test the native-bearer authentication/API contract in the main `MyFinHub` repository. Before broad feature implementation, bootstrap the Android project and build representative synthetic-data prototypes for Home, Activity/Quick Entry, secure card flow, Plan editor, Insights, and adaptive list-detail.