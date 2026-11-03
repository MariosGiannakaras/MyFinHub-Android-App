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

## Read-only D7 installed-build preflight

For an owner-authorized, production-signed **new** candidate installed **in place** on the physical Galaxy S24 Ultra, a desktop with Android Platform Tools can run:

```bash
python3 scripts/s24_device_preflight.py \
  --previous-version-code 10011 \
  --expected-version-code NEW_SIGNED_VERSION_CODE \
  --expected-version-name NEW_SIGNED_VERSION_NAME
```

Substitute the version fields from the actual, separately approved protected release; **do not** assume a future version or reuse rc12. With multiple connected Android devices, use `--serial` to select the intended device. The script performs read-only `adb devices`, `getprop`, and `dumpsys package` queries. Its JSON report contains only model, Android release, package/version metadata and explicit unverified acceptance fields: it never prints device serials, financial data, credentials, signer material, raw dumpsys or app-private storage.

A matching report **does not** attest to production signing, in-place-update preservation, owner+AAL2/PIN/biometric/vault continuity, appearance, functionality or D7 acceptance. These require direct owner verification and privacy-safe evidence in [issue #149](https://github.com/MariosGiannakaras/MyFinHub-Android-App/issues/149). No script action installs, updates, exports files or launches the protected publisher.
