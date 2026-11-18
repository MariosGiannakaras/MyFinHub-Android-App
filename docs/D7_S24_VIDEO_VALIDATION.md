# D7 — Samsung Galaxy S24 Ultra physical video validation

This is the **current mobile acceptance checklist**. The historical `docs/PHASE_6_DEVICE_HANDOFF.md` applies to an old rc6→rc7 candidate and must not be used to claim current acceptance. The authoritative release, integration and gate state remains `tracking/android-project-state.json`, plus actual GitHub CI and signed release metadata.

## Preconditions — do not record yet if any fail

1. A **separately owner-authorized** production candidate must be published from an open, non-draft Android release-source PR with successful exact-head tracking, Android CI, screenshot and both hosted S24 emulator shards/aggregate. A merged `develop` commit or debug APK is **not** the phone-update artifact.
2. Confirm the candidate's immutable GitHub Release and private updater metadata agree: package `app.myfinhub.android`, expected higher versionCode/versionName, APK SHA-256, and **same enrolled production signing identity**. Do not derive these from the intended name or source-only PR.
3. Confirm Google Play Protect permits a normal, consented **in-place** install on the owner's authorized SM-S928 Samsung phone. If it reports **Harmful app blocked** again, STOP, preserve the warning evidence and follow `docs/PLAY_PROTECT_INCIDENT_20261114.md`; do **not** select “Install anyway”, turn off scanning, uninstall, wipe data or rotate signer.
4. Preserve the installed app, encrypted offline data and pending transaction queue. Do not use a different package or test key as a workaround. Confirm the new version is installed using the app's Settings screen and, where USB access is available, the read-only `scripts/s24_physical_preflight.py --json --expected-version-code <PUBLISHED_CODE>` utility. Its metadata output does not prove signer, Play Protect status, successful sync or owner acceptance.

## Phone recording: acceptance sequence

Use the phone's **normal** display scale/font and connectivity. Record only sanitized financial data: hide account/IBAN details, names, PAN, CVV, passwords, TOTP, tokens, notification content and any other private information. Never record the signing keystore or diagnostics containing secrets.

1. **Install and privacy:** Show the actual installed version and that an authorized in-place upgrade preserved the session, local lock and app data. Verify biometric/PIN relock, app switching/Recents preview privacy, quick Home visibility toggle, and Settings sign-out availability without signing out during earlier tests.
2. **Finance source:** Open Home after fresh online authentication. Confirm the finance GET succeeds without the previous `HTTP 400 / INVALID_DATA` error. Distinguish server-confirmed data from encrypted offline cache and queued local changes. Do not treat cached balances as proof that the server is healthy.
3. **Home and accounts:** Verify the headline total includes **Cash + Payroll only**, never Savings/reserve/other banks. Inspect all **three primary account cards** (Cash, Payroll, Savings), their real history curves, 30-day deltas, contextual details, savings target/comparison, IBAN where available, and relevant actions. Secondary accounts remain compact; verify light/dark legibility and scrolling.
4. **Activity / Κινήσεις:** Open full history, search, type/account/category/date filters, monthly overview, selected-month indicator, and return from an **empty selected month** via “Όλο το ιστορικό”. Verify whitespace-only search can be cleared and regular filtered lists recover. Inspect transaction detail/edit navigation and back behavior; do not delete a real transaction as a visual test.
5. **Cards and credit:** Inspect the real selected debit/credit card's identity, card-stack navigation, debt/limit, statements, purchase and repayment history, and payment-source eligibility. Confirm the two historically reported November 12 card purchases against the **authoritative synchronized ledger** before treating the issue as resolved. If absent or unlinked, STOP mutating money and record the exact non-secret discrepancy.
6. **Quick Entry:** Verify expense, income, credit purchase, card repayment and person-to-person repayment entry forms as appropriate. Inspect date picker, category/person/card changes, preflight debt limits, validation, keyboard/focus, draft retention when sync is blocked, and disabled Save during a blocking error. Use **no real finance mutation** unless the owner independently approves a legitimate transaction and its expected canonical effects.
7. **Offline/recovery:** With the app already showing known data, check truthful cache/pending labels on Home, Activity and Cards. Observe offline→online recovery without false sync success, duplicated transactions or lost local state; don't create synthetic production finance records.
8. **Usability and safety:** Check dark/light and larger font where feasible, scroll targets, keyboard insets, clipping, modal/dialog accessibility, touch targets and navigation between the three primary destinations. Record crashes, stuck loading, blocked buttons, stale information or unintended disclosure.

## Evidence and decision

Record the actual candidate release tag, code/name, published APK hash, test device model/Android version, original installed version, in-place update result, Play Protect result, real finance GET state, any observed error request ID (without credentials), credit purchase findings, offline/cache status and each user journey's PASS/FAIL/SKIPPED outcome. Attach sanitized video/screenshots to the relevant project issue.

An **owner-observed PASS** is required before marking deployment D7 complete or W6.4 accepted. Successful hosted emulator tests, a published artifact or a phone Settings version label alone are insufficient. Production promotion/D8 is a separate decision. If any safety or finance-integrity gate fails, do not continue with financial writes or declare the build ready.

## Current rc18 candidate observation — 2026-11-18

The protected publisher released `v1.0.0-rc18` / `10017` (direct APK SHA-256 `40360b5678225300c7cd8f8fe1dd1f727cbb14dee9bd5dde2f33ace329fbdc0d`). The owner reports this candidate passed Google Play Protect on the Galaxy S24 Ultra. **Only the Play Protect result is reported:** real installed-version and in-place upgrade continuity, live finance GET, credit-purchase linkage and functional screen/video acceptance still require separate device evidence. D7/W6.4 are not complete.
