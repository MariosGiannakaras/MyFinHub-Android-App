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

## Checkpoint 3 — form density, accessibility and route inventory

- Activity: remove the redundant Κινήσεις/Ιστορικό κινήσεων subtitle; retain the explicit analytics date scope, filters, monthly summary, detail/edit/confirmation and server-sync recovery.
- Quick Entry: make the three frequent transaction types at least 48dp tall and stack at 150% font. Retain the contextual credit-purchase shortcut as a less prominent action. Save gating, dirty Back, security states and canonical mutation callbacks are unchanged.
- Card detail: remove the duplicate nickname/institution immediately below the persistent title bar, retaining logo, masked digits, card network and all secure/credit actions.
- Wallet account rows: increase tappable vertical area. Home recent titles may wrap onto two lines.
- Login: remove the redundant description above the labelled fields; preserve MFA, PIN, biometric and auth error flows.

### Existing screen / route inventory considered

| Area | Routes and surfaces | Refinement / unchanged functionality |
| --- | --- | --- |
| Home | Home, HomeAttention | Urgency order, recent rows and shared geometry; primary-account charts and drilldowns retained |
| Activity | Activity, CategoryActivity, ActivityDetail, ActivityEdit | Compact header and common rows; search, filters, mutation/detail and confirmations retained |
| Quick Entry | QuickEntry, split/date/type pickers | Accessible type choice, contextual credit action, existing sticky Save and dirty Back |
| Wallet | Money, Accounts, AccountDetail, NetPosition, CardCreate, CardDetail, CardSecureDetails, Savings, Loans, Lending | Remove duplicate create, improve account rows and card header; preserve vault and financial identity |
| Plan | Plan, PlanForecast, PlanRecurring, PlanRecurringManage, PlanScheduled, PlanBudgets | Compact navigation and shared spacing; forecasts, budgets and editors unchanged |
| Analysis | Insights | Flat month controls, single net-position link and preserved chart calculations |
| Local utilities | Settings, Diagnostics, NoticeHistory | Flat preferences, update states, safe support and lazily rendered notices |
| Auth | Login, MFA, PIN enrollment/unlock, biometric, loading/error | Less redundant text and shared responsive components; security rules unchanged |

This branch delivers source-only UI refinements; pixel-level, device, runtime, and regression acceptance is NOT established. No validation commands or physical UI review have been performed. Existing finance/release workstream progress and release state remain unchanged.
