# Android UI/UX interaction refinement — 2026-11-19

Scope: source-only follow-up to the already completed 2026 redesign. The live Android `develop` branch remains authoritative for navigation, finance behavior, authentication, security, and updates. No backend or distribution changes.

## Checkpoint 1 — shared density and device Settings

- Retain existing application screens, actions, Android notification-settings intent and fallback, privacy preference persistence, update controller states, install verification behavior and package settings links.
- Introduce flat Material 3 settings sections and accessible 48dp clickable/toggleable settings rows, including a single unified switch target. Preserve larger-font appearance choice layout.
- Replace oversized secondary settings actions with contextual rows; keep real download/install actions visually primary.
- Preserve the update failure/recovery mapping, especially blocked APK install behavior with no bypass or retry.
- Make common app spacing and non-primary action geometry more compact while preserving at least 48dp touch targets.
- Keep the application version and build in About, and diagnostics/history reachable.
- Change source only: builds, tests, UI snapshots, lint, analysis and other validation commands were explicitly prohibited, so none were run. Visual/device acceptance is not claimed.

Additional screens and flows continue in subsequent source-only checkpoints on this branch. Release and APK publishing are out of scope.

## Checkpoint 2 — real-screen density and navigation

- Home: preserve all real-history account charts and amounts but surface urgent attention immediately after the available balance, before the account roster. Flatten recent and attention rows (no outer nested cards) while preserving detail destinations and disabled-row logic.
- Wallet: remove the duplicated `New card` entry when the card list is empty; the existing empty-state action remains discoverable. Preserve secure-card workflows and destructive confirmation.
- Plan: retain the forecast and budget's substantive cards; turn five static navigation cards into full-width compact, labelled 48dp+ links with their existing actions and test tags.
- Analysis: flatten calendar-month navigation and replace the redundant net-position card/button combination with a single secondary link. Preserve date-period semantics and report calculations.
- Notification history: use actual lazy rows instead of composing every notice in one large card; retain event titles, dates, sanitized descriptions and selectable support codes.
- Diagnostics: move the safe summary to the same flat-section system and use compact secondary text actions; preserve support-copy output, confidentiality and expandable technical information.
- Reviewed Activity, Quick Entry and authentication surface boundaries; their existing current handling of scope, draft preservation, blocked finance writes, privacy and secure navigation is kept intact rather than replaced by speculative refactoring.
- Source-only checkpoint. No validation commands or physical UI review performed; no certification of behavior is implied.
