# MyFinHub Android — supported device contract

## Sole supported device

MyFinHub Android is developed for one physical device only:

- **Samsung Galaxy S24 Ultra** (Galaxy S24 Ultra / SM-S928 family; the owner's physical phone is authoritative).
- Samsung's hardware specification for the Galaxy S24 Ultra display is **6.8 inches, 3120 × 1440 (Quad HD+), up to 120 Hz**.

This is a deliberate product constraint, not a temporary test limitation.

## Acceptance scope

- The supported form factor is a normal compact phone UI on the owner's Galaxy S24 Ultra.
- Tablets, foldables, desktop-like Android layouts and other device classes are **not supported targets** and are not merge/release acceptance gates.
- Do not spend implementation or CI time fixing behavior that is only broken on an unsupported tablet/foldable layout unless it also affects the Galaxy S24 Ultra.
- Existing responsive code may remain when harmless; there is no requirement to remove it merely because other form factors are unsupported.
- 150% font-scale/adaptive-device matrices are not mandatory acceptance gates. Device-specific accessibility/display settings should be validated against the owner's actual S24 Ultra configuration when physical-device validation is performed.

## Automated validation

A hosted stock-Android compact-phone emulator may be used as a representative automated host for instrumentation tests. It is **not** treated as an exact Samsung One UI / Galaxy S24 Ultra simulation.

Automated merge validation should focus on:

1. normal Android compile/unit/lint/security/release-policy checks;
2. real Compose screenshot regression for retained Android UI;
3. one representative compact-phone instrumentation suite.

Do not run automatic tablet/foldable device matrices for ordinary Android work.

Hosted-emulator Macrobenchmark/Baseline Profile runs are optional diagnostic tools rather than a device-specific acceptance result. The benchmark/profile tooling must continue to compile, but actual device-specific performance acceptance belongs to the physical Galaxy S24 Ultra handoff.

## Physical-device authority

The owner's physical Samsung Galaxy S24 Ultra remains the final authority for:

- actual Samsung One UI rendering and system bars;
- the owner's current display resolution, display zoom and font-size settings;
- startup/performance behavior on real hardware;
- physical-device Auth/API smoke validation.

Production signing is already enrolled and protected. Ordinary implementation work must still not create, rotate or expose signing material or publish signed releases. Each new production candidate requires an explicit release checkpoint and physical S24 Ultra acceptance before stable promotion.

## D7 physical-device read-only preflight

For owner-supervised physical acceptance, connect the **actual S24 Ultra via USB** with authorized ADB access, then run:

```sh
python3 scripts/s24_physical_preflight.py --json
```

When a **newer, owner-authorized same-signer candidate** has actually been published and its
exact versionCode is known from verified release metadata, the same read-only tool may
enforce that the installed package matches that candidate, for example:

```sh
python3 scripts/s24_physical_preflight.py --json --expected-version-code 10012
```

**10012 is the published rc13 versionCode** (protected run #42790602178, immutable GitHub release v1.0.0-rc13, 2026-11-10), not an illustrative number. The published rc13 source PR #446 predates merged UI PRs #448/#449, so this version cannot validate the current develop UI. A newer acceptance candidate needs a separate explicit owner release authorization. Rc12/10011 or older codes are rejected in this mode. A missing/mismatched installed candidate exits nonzero without exposing
package paths or raw ADB diagnostics. A matching code **does not verify the signing
certificate, source provenance, that the update happened in place, account/session
continuity, or owner acceptance**. Those remain separate D7 checks.

This is an optional, reproducible **read-only inspection**, not an installation or acceptance test. It queries only the Samsung SM-S928 model family, Android release, font scale and the installed `app.myfinhub.android` package/version. It does not retrieve the USB serial, account identifiers, finance data, session tokens, card secrets, app files or APK; it never modifies device settings, requests permissions or installs an update. All ADB failures are summarized without printing raw device diagnostics. The script requires a single authorized physical **USB** phone, not an emulator or a Wi-Fi ADB endpoint.

Installed-version checks fail closed unless `pm path` returns an empty absent-result or valid APK paths and `dumpsys package` contains exactly one matching `Package [app.myfinhub.android]` section with a single valid version code and name. Unrelated package version records, duplicate records and malformed package output are never treated as the target build; raw output remains private.

A reported `older_than_published_rc13` build is insufficient for post-D6 acceptance. Even a `published_rc13_version_only_requires_signer_and_owner_verification` result is only a metadata match, never proof of the installed signer or physical acceptance of post-#448 UI. A higher version code still requires independent release-source and **same-signer** verification, an in-place installation/continuity check and explicit owner acceptance of the real user journeys. For the known rc13 code, mismatched versionName fails an explicit expected-code check. A matching name/code can still be spoofed; only independently verified package signer, source and owner observations can establish continuity. The script always reports `physical_acceptance: not_assessed`. Never expose account details, PAN/CVV or unmasked balances in issue screenshots or logs. The current required checklist and blocked release-source gate remain in issue #149; owner authorization is required for a new signed candidate.
