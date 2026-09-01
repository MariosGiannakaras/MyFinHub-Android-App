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

When Phase 6 begins, the owner's physical Samsung Galaxy S24 Ultra is the final authority for:

- actual Samsung One UI rendering and system bars;
- the owner's current display resolution, display zoom and font-size settings;
- startup/performance behavior on real hardware;
- physical-device Auth/API smoke validation.

Phase 6 remains separate. Do not create a production signing key, production-signed APK or release as part of ordinary implementation/redesign work.
