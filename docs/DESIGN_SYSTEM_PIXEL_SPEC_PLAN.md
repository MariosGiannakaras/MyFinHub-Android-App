# MyFinHub Android — Design System & Pixel-Spec hardening completion record

Status: implementation and visual acceptance complete; final merge gates run on the accepted reference-sync head
Tracker: issue #47
Scope: Android only. Physical Galaxy S24 Ultra acceptance, production Auth/API validation and production signing/release remain Phase 6.

## Outcome

The retained Android product now has an explicit, testable visual contract rather than screen-local approximations. Product-owned geometry is represented by shared tokens/components, Material 3 inheritance is documented where appropriate, light/dark contrast is regression-tested, and the visually changed Compose surfaces have been rendered and personally inspected before canonical reference acceptance.

No finance business semantics, canonical data ownership, auth assurance boundary, server-vault PAN/expiry boundary or device-local CVV persistence boundary was changed by this workstream.

## Completed workstreams

- [x] Foundation token audit: spacing, typography, shapes, borders, elevation, icon sizes and touch targets.
- [x] Color/contrast audit with >=4.5:1 normal-text and >=3:1 large-text/essential-graphics thresholds.
- [x] Component specification for buttons, outlined actions, destructive actions, icon buttons, fields, selectors, date controls, chips, cards, rows, headers, navigation, FAB, sheets, dialogs, snackbars and switches.
- [x] Product-owned ad-hoc dimensions moved to shared MyFinHub metrics/components where ownership is meaningful.
- [x] Screen-rhythm audit across Home, Activity, Quick Entry, canonical Money, canonical Plan, Insights and Settings/Utilities.
- [x] Authentication controls aligned to the same 56dp field / 48dp action system with explicit auth layout metrics.
- [x] Production canonical Card Detail aligned to secure shared controls without changing secret lifecycle/security boundaries.
- [x] Light/dark/large-font states rendered and inspected for the changed canonical surfaces.
- [x] Truthful empty/history and system recovery states retained; obsolete production-facing “synthetic” Insights copy removed.
- [x] Real Compose screenshot candidates generated after visual batches and personally inspected.
- [x] Canonical screenshot acceptance expanded from 38 to 41 references with three sanitized Card Detail states.
- [x] Zero unresolved PR review threads at merge-readiness audit.

## Executable foundation contract

### Spacing

| Token | Value |
| --- | ---: |
| micro | 2dp |
| xxs | 4dp |
| xs | 8dp |
| sm | 12dp |
| md | 16dp |
| lg | 20dp |
| xl | 24dp |
| xxl | 32dp |

Derived relationships are also named in code:

- compact navigation-content clearance: 96dp = 80dp navigation + 16dp spacing;
- signed-in Snackbar clearance: 152dp = 80dp navigation + 48dp primary action + 24dp spacing.

### Shapes

| Role | Radius |
| --- | ---: |
| extraSmall | 8dp |
| small | 12dp |
| medium | 16dp |
| large | 24dp |
| extraLarge | 28dp |

### Core geometry

| Component/role | Accepted specification |
| --- | --- |
| Minimum interactive touch target | 48dp × 48dp |
| Compact screen horizontal edge | 20dp |
| Standard card content padding | 16dp |
| Standard card border | 1dp decorative outlineVariant |
| Finance row padding | 16dp horizontal / 12dp vertical |
| Icon badge | 40dp container / 20dp icon |
| Standard icon | 20dp |
| Compact action icon | 18dp |
| Primary/outlined finance action | >=48dp visible height, 20dp horizontal padding, 12dp vertical padding |
| Outlined text field / selector | >=56dp height |
| Text-field borders | 1dp unfocused / 2dp focused-error Material baseline |
| Bottom navigation | Material 3: 80dp container, 24dp icon, 64×32dp active indicator |
| Auth content | 24dp horizontal / 32dp vertical, 480dp max width, 40dp brand mark |
| Card-secret value label column | 56dp |

### Typography

The theme consumes explicit `MyFinHubTypographySpec` values for headlineLarge 30/36sp, headlineMedium 25/31sp, headlineSmall 21/27sp, titleLarge 19/25sp, titleMedium 16/22sp, bodyLarge 16/23sp, bodyMedium 14/20sp and labelLarge 14/18sp with the documented weights/letter spacing.

## Contrast acceptance

Light semantic finance accents were corrected where the prior palette fell below normal-text contrast. Unit contracts now protect both light and dark semantic pairs on their actual surface/container backgrounds and protect essential outlines at the graphical threshold.

The accepted dark semantic pairs have substantial headroom: semantic accent/container combinations remain above roughly 6.6:1 and the essential dark outline is roughly 5.8:1 against the dark surface.

## Production component migration

### Quick Entry

- Shared 56dp amount/person/reconciliation/note/split fields.
- Shared 56dp selectors with explicit error semantics.
- 48dp date-picker icon target.
- Shared primary/outlined actions.
- Existing first-invalid focus, type-specific accounting rules, DatePicker fallback and split scroll behavior preserved.

### Activity / Plan / Home / Utilities

- Activity edit controls and save action use shared contracts.
- Canonical Plan budget fields use shared fields.
- Settings logout and Change History actions use shared action geometry.
- Home quick-entry sheet uses shared actions; compact Home/Activity use common edge/clearance metrics.

### Authentication

- Login, TOTP, PIN enrollment, PIN unlock and biometric/PIN fallback use the shared secure-field/action system.
- Appropriate IME actions are explicit without changing credential handling.
- Auth surface layout has named 24/32dp padding, 480dp max width, 40dp brand and 16dp main rhythm.

### Production Card Detail

- Canonical signed-in navigation uses a dedicated secure pixel-spec Card Detail surface.
- Reveal/retry/save-CVV use shared 48dp actions.
- CVV input uses the shared 56dp secure field.
- Local CVV deletion uses explicit destructive visual language and a 48dp target.
- `SecureWindowProtection` and the owner+AAL2/server-vault versus device-local-CVV boundary remain intact.
- Screenshot fixtures contain no real/reusable PAN/CVV secrets.

## Screenshot acceptance

The accepted renderer set contains 41 canonical references. The previous 38 accepted surfaces were re-rendered after the Auth migration; the final implementation render was byte-identical to that inspected 38-image set. The final workstream added three Card Detail references:

- hidden light;
- hidden dark;
- revealed sanitized at 150% font scale.

All three were personally inspected at the 412×915 compact-phone target. No clipping, overlap or unreadable state was accepted. The large-font secure state keeps PAN/expiry/CVV rows, the secure CVV field, disabled save state and destructive action visible and distinguishable.

## Final gate policy

After the accepted reference tree is committed, the exact merge head must satisfy all of the following before merge:

- normal Android CI, including benchmark/Baseline Profile tooling, unit tests, instrumentation compile, lint/debug assembly, optimized unsigned release/R8 analysis and release-policy audit;
- screenshot regression success **without candidate regeneration**;
- representative S24-target instrumentation success;
- zero unresolved review threads.

A hosted-emulator provisioning/download failure is infrastructure noise, not a product pass; the S24-target gate is only accepted when the interaction suite itself completes successfully on a clean retry.

## Phase 6 boundary

This pass does not claim Samsung-specific physical acceptance. `docs/PHASE_6_DEVICE_HANDOFF.md` and issue #14 remain authoritative for:

- production-configured Auth/API on the owner’s physical Galaxy S24 Ultra;
- real One UI/display/font/dark-mode/TalkBack rendering;
- real-device Quick Entry, canonical Money/Plan and secure Card Detail/CVV behavior;
- offline/reconnect and reversible mutation smoke;
- physical performance acceptance;
- release-candidate decision, production signing key and production-signed APK only after owner authorization.
