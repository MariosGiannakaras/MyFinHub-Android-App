# MyFinHub Android — Phase 6 physical Galaxy S24 Ultra handoff

## Purpose

This is the executable physical handoff for the final post-rc6 correction release. The **owner's Samsung Galaxy S24 Ultra is the sole supported device and authoritative physical acceptance environment**.

Hosted CI/device-lab evidence validates build/runtime contracts, but it does not replace owner acceptance on the real phone.

## Current checkpoint — 2026-09-08

- `1.0.0-rc6` / versionCode `10005` is a valid production-signed technical baseline but was physically rejected for product/UI reasons.
- The owner-rejected correction pass is merged to `develop` and already passed fresh real Compose light/dark/150%-font inspection plus hosted Android gates.
- The long-lived production signing identity already exists and is enrolled. **Never create, replace, rotate or expose it.**
- The owner has explicitly authorized final production signing/publication and GitHub Release creation.
- Android Developer Console Limited distribution is the selected Google-supported non-public distribution path. The owner reports `app.myfinhub.android`, the production signing certificate and the S24 Ultra are authorized.
- The next intended candidate is `1.0.0-rc7`, produced through the protected same-signer release path.
- `main` remains release-only and must not be promoted until the corrected candidate is physically accepted.

Because rc6 is production-signed, authoritative continuity acceptance requires a strictly higher package signed by the **same enrolled production identity**. A debug/test-signed build, uninstall, or parallel package is not valid continuity evidence.

## Release prerequisites

Before touching the phone:

1. The active final release-preparation PR must be merged to `develop` with Project Tracking, Android CI/R8, screenshot regression and representative S24-target instrumentation green.
2. Create an exact no-functional-change release-source PR from current `develop` for `1.0.0-rc7`.
3. Require exact-head Project Tracking, Android `verify`, screenshot regression and representative S24-target instrumentation to pass on that source PR.
4. Trigger the protected production publisher using the guarded production release request.
5. Verify the publisher uses the existing enrolled signer, plans a versionCode strictly higher than `10005`, publishes the direct APK to the private production update channel, re-reads the exact bytes, and creates the immutable GitHub prerelease/checksums/safe metadata.
6. Do not expose or move signing secrets outside the protected environment.

## Physical installation / continuity

On the owner's authorized Galaxy S24 Ultra:

1. Record current Samsung One UI / Android version, display resolution, screen zoom and font size. Do not change settings merely to make the UI pass.
2. Preserve the installed rc6 package and application data.
3. Update **in place** from rc6 to rc7 through the private updater or the signed GitHub Release APK. Do not uninstall first.
4. Confirm Android reports the expected newer version and no parallel package was created.
5. Confirm the encrypted server session remains present.
6. Confirm existing local PIN/biometric enrollment remains present where applicable.
7. Confirm device-local encrypted CVV state remains present where applicable.
8. After local unlock, confirm the stored server session validates/refreshes normally without an unnecessary full email/password/TOTP login.

Signer mismatch, forced uninstall, lost application data, lost PIN/CVV state or unexplained full-login requirement are blockers.

## Corrected product acceptance

### Home / identity / provider

- Semantic account role/name is separate from institution/provider.
- Supported provider mapping/branding is correct.
- Identity remains readable at normal and large-font settings.

### Cards

- Canonical card creation is obvious and completes through the supported mutation boundary.
- Card detail shows correct account/card/provider identity.
- Credit-card detail exposes linked canonical purchase/payment history when synchronized data exists.
- Card-stack indicators reflect and control the active card reliably.
- Card deletion uses the approved transition quality.
- PAN/expiry remain behind the server vault + owner/AAL2 boundary; CVV remains device-local and protected.

### Εικόνα

- The redesigned comparison/trend/category hierarchy is materially clearer than the rejected rc6 presentation.
- No clipping, overlap, unreadable labels or broken hierarchy appears in light, dark or large-font settings.
- Drill-in/navigation does not expose stale nested state.

## Production Auth/API and finance smoke

1. Cold launch the updated app and verify no configuration/error loop.
2. Confirm canonical finance data loads and visible totals/lists are plausible.
3. Open Money and Plan and verify truthful synchronized state with no fabricated finance details.
4. Open Quick Entry, choose at least one transaction type and verify the canonical form opens correctly.
5. Verify date selection, monetary input, validation and first-invalid-field focus/scroll on the physical Samsung keyboard/display.
6. Perform one reversible finance mutation and verify it syncs exactly once.
7. Test one offline mutation: optimistic/pending local state should appear, relaunch while offline should preserve it, then reconnect should reload fresh server state and reconcile without duplicate writes.
8. Test one transient load failure/reconnect and confirm recovery without a loading loop.
9. Open a card-secret flow and re-check owner+AAL2, PAN/expiry server-vault and device-local CVV boundaries.
10. Verify Settings logout is present and diagnostics remain privacy-safe.
11. Perform logout/re-auth only as the final deliberate auth-flow check.

## Rendering / accessibility / navigation

Inspect current real application surfaces on the owner's unchanged S24 settings:

- Home;
- Activity / Κινήσεις and filters;
- Money / Περιουσία, card stack/detail/create;
- Plan / Πλάνο;
- Εικόνα;
- Quick Entry;
- Settings/Diagnostics/Updates;
- login/TOTP/PIN/local-unlock states;
- loading/empty/offline/retry/pending states;
- Snackbar/dialog/detail surfaces.

Reject clipped text, inaccessible controls, overlap with bottom navigation/FAB/Snackbar, unreadable long labels, duplicate navigation, stale nested detail, broken card indicators or stuck loading/recovery states.

Verify specifically:

- comfortable touch targets at the owner's display/zoom settings;
- useful TalkBack labels/state for stateful controls;
- validation errors understandable without color alone;
- large-font settings do not hide primary actions or break Activity filters/navigation;
- dark mode keeps muted/status/disabled content readable;
- top-level destination reselect returns to root;
- cross-destination drill-in does not expose stale nested detail.

Only current real screenshots from the supported phone count as device-specific acceptance evidence.

## Performance acceptance

Verify on the physical S24 Ultra:

- cold start reaches expected auth/product state without abnormal delay;
- Home and Activity scroll smoothly with realistic data;
- Quick Entry opens/submits without visible duplicate or jank;
- Money/Plan/card detail navigation remains responsive;
- top-level navigation and local unlock remain responsive;
- offline → online recovery does not freeze navigation;
- update checking/downloading remains nonblocking until Android installation begins.

A reproducible physical performance defect blocks acceptance.

## Limited distribution

Android Developer Console Limited distribution does not host the release as a public Play listing. The signed direct APK may be delivered by the existing private updater or GitHub Release to authorized devices.

Keep package `app.myfinhub.android` and the enrolled production signing certificate registered. Additional devices must be explicitly authorized through the supported Limited-distribution flow and remain within the plan's device limit.

See `docs/ANDROID_DEVELOPER_CONSOLE_LIMITED_DISTRIBUTION.md`.

## Completion record

Record authoritative acceptance in issue #73:

- device/One UI/Android + display/font settings;
- installed rc6 baseline and tested rc7 candidate;
- in-place same-signer update result;
- session/PIN/biometric/CVV continuity;
- Home/provider/card/Εικόνα results;
- Activity/navigation/large-font/light/dark results;
- production Auth/API and canonical Money/Plan/Quick Entry results;
- offline/reconnect/exactly-once result;
- updater result;
- current real-device screenshots;
- performance result;
- explicit owner acceptance or rejection.

Overall progress remains **4/6** until these authoritative physical requirements are satisfied. Hosted green checks alone cannot close #73 after the prior physical rejection.
