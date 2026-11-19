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

## Canonical tracking and Git integration state

The separate source-only workstream is recorded under `ui_ux_refinement_2026_11_19` in `tracking/android-project-state.json`. Historical completed redesign counts, active finance-contract issue #155, release candidate facts, server/API state and S24 physical acceptance are intentionally unchanged. Generated STATUS/TODO/handoff fields have not been changed. This feature branch is not merged to `develop` and has no pull request because the owner prohibited builds, tests and other validation, including CI; no APK/release publication was performed.

## Checkpoint 4 — recurring, scheduled and account details

- Recurring management: retain edit and real-payment entry directly visible; move pause/resume and stop to a labelled contextual menu with the existing stop confirmation. Keep disabled and duplicate-ID mutation fences and all original destination callbacks; no payment is posted by opening an action.
- Scheduled management: prioritize execution recording; place skip and cancellation behind a contextual menu. Preserve exact completion prefill, existing confirmation flow and non-writable invalid/duplicate rows.
- Make new recurring/scheduled creation an appropriately visible contextual primary operation rather than a bare full-width text link. Remove redundant headers above these lists.
- Account detail: present current balance before IBAN metadata/actions, preserve visibility and server-backed edit/delete conditions and existing histories. Remove repeated scoping explanation.
- Attention detail: keep primary navigation and the reversible hide action, but reduce redundant warning copy and make hide secondary.
- Changes are source-only, committed without builds, tests, lint, static analysis or device verification as required. Existing finance/domain semantics and physical-acceptance status are untouched.

### Scope correction for checkpoint 4

Corrected the closing scopes of the two newly introduced action sections. This is a source-only correction, not compiler or runtime evidence. The last checkpoint commit identifier was reconciled in canonical tracking.

## Checkpoint 6 — active Wallet and recurring overview

- Traced the real `AppRoute.Money` and nested `AppRoute.Accounts` destinations to the production `CanonicalWalletScreen` (not the older legacy Money surface).
- Show an explicit Back control only when Wallet is opened as the nested Accounts detail from Home; keep top-level Wallet without a misleading Back button. Do not alter navigation stacks or financial mutations.
- Remove the repeating selected section from the Wallet title/subtitle, maintain labelled account/card/debt selectors, and use compact account group headers.
- Flatten empty account/card states and archived-card rows, preserving card restore, archive and secure-card/vault behavior.
- Flatten the read-only recurring overview item list and surface its actual management entry more clearly; do not reinterpret estimates as actual payments.
- Changes remain unbuilt, untested, unlinted and visually unverified by explicit request. No release, APK or PR.

## Checkpoint 7 — accessibility and action prominence

- Make every flat settings section a TalkBack heading, use the design-system chevron instead of an unrelated text glyph, and expose one parent toggle target without duplicate internal Switch semantics.
- Keep manual update secondary actions at the product's minimum 48dp target without changing update, installation or blocked-file handling.
- Stack IBAN edit/delete actions at 150% font so Greek labels remain usable; keep all existing privacy and authenticated metadata requirements.
- On the actual Wallet Cards tab, use an appropriately prominent creation action only for the first card, or the existing companion Quick Entry flow; render additional-card creation as a secondary action when cards already exist. Retain archived-card restore with an accessible touch target.
- Source-only: no builds, lint, automated tests, screenshots or other validation. No release or PR.

## Checkpoint 8 — action discoverability and period controls

- Activity detail: a one-item "More" menu made Delete unnecessarily hard to discover; expose a secondary destructive text action directly while retaining exactly the same confirmation dialog and mutation-block conditions. Hide the empty subcategory placeholder without hiding actual historical values.
- Budget: replace the oversized month-selection card with a compact standalone section and remove repeated explanatory prose; retain the month picker, prior/next controls, existing callbacks, conditions, and test tags. Stack month controls at enlarged Android font scales.
- Recurring management: use the complete action label "Καταχώριση πληρωμής" to avoid implying that the button itself posts a payment; preserve the actual Quick Entry path and write gates. Display payment/edit actions vertically at enlarged font scales and disable the additional-actions opener during blocked mutations.
- All edits remain source-only. No builds, tests, lint, static analysis, screenshot comparison or device verification was performed.

## Checkpoint 9 — form and credit-card interaction refinement

- Quick Entry: correct the optional-note disclosure so an existing or prefilled note opens initially, but an explicit Hide/Show selection works even while the note has text. No note text, draft, or save logic is modified.
- Card detail: remove a redundant nested card around credit actions, retain the prominent payable action and render secondary or disabled actions as compact text buttons with the original callbacks and test tags. Keep the unavailable-payment reason and all secure-card data untouched.
- Card creation: replace four repeated "Αλλαγή" text labels on bottom-sheet selectors with a consistent, non-verbal dropdown chevron. Preserve labels, selected values, touch behavior and the sensitive-card input contract.
- Source-only edits, committed without automated checks, builds, lint or device verification.

## Checkpoint 10 — budget row actions and field sizing

- Consolidate category and subcategory budget edit/delete controls into one reusable responsive action row: compact on ordinary phones, stacked when the Android font scale is enlarged. Preserve source-ID uniqueness gates, blocked-mutation conditions, exact existing test tags, and both destructive confirmation dialogs.
- Maintain secondary destructive styling rather than treating Delete like a primary financial operation.
- Apply the design-system minimum touch target to existing card-create selection fields without changing PAN/expiry/CVV input or storage behavior.
- Remove repeated idle-state update instruction text when the actual update-check action already communicates its purpose; no state-machine or update verification changes.
- No tests, builds, static analysis, screenshot tooling or lint executed.

## Checkpoint 11 — finish responsive destructive states and reconcile tracking

- Explicitly mute disabled destructive labels in Activity and budget rows; do not visually imply a blocked operation can run.
- Record all three recent UI refinement commits in canonical project tracking.
- Keep the pre-existing finance Web-parity, signed-APK, device acceptance, and project progress fields unchanged.
- Source is deliberately unvalidated: no compilation, CI, tests, static analysis, lint, screenshots or physical acceptance. No pull request, merge, APK or release.

## Checkpoint 12 — card-entry density and financial choice accessibility

- Card creation: replace the large explanatory card with a concise, persistent privacy/synchronization note. Convert the four metadata selection buttons to compact native clickable rows with clear labels, selected values and a trailing chevron. Retain selection callbacks, test tags, minimum geometry and the untouched PAN/expiry/CVV inputs and encrypted-vault contracts.
- Loan payments: replace account names prefixed by a visual tick with full-width 48dp radio-selection rows, readable at enlarged font sizes and accessible as selected/unselected choices. Preserve eligible account filtering, explicit payer/destination selection, payment preview and confirm-only financial posting.
- Loan metadata: make the long-term recurring checkbox label a single tappable target, with one checkbox interaction instead of separate row/control targets. Preserve the original boolean state and validation/write callback.
- No builds, tests, lint, static analysis, or physical UI validation were executed by instruction.

## Checkpoint 13 — contextual savings, loans and lending lists

- Savings history uses compact read-only rows and dividers rather than individually framed cards; retain canonical row ordering, existing identity keys, test tags, amount privacy and all historical amounts. Avoid repeating "no separate comment" on every item.
- Loans sorting is a flat control group rather than a card nested between the hero and results; retain all four sort modes, the direction switch, pagination resets, tags and payment/editor behavior.
- Lending person selection and history filters are flat. The "show all" action appears only when a person is selected; no inactive all-people button consumes space on the default overview. Keep the existing query, selected person, filter state and history semantics.
- No domain finance, mutation, backend or persistence code was modified. UI changes remain unvalidated by instruction (no build, automated tests, lint or other checks).

## Checkpoint 14 — Lending item hierarchy and read-only history

- A selected person now has a noninteractive status label instead of an active button that selected the same person again and reset pagination. Unselected person rows retain the existing one-tap "Προβολή κινήσεων" action, identity, amounts and accessible tags.
- Historical lending transactions display as flat, divider-separated rows. Preserve every displayed note, original amount, direction, historical outstanding balance, account label, imported legacy marker and the indexed source identity; no history becomes a write target.
- Empty filtered history uses a compact explanatory message instead of a large card. Existing query/filter/clear and safe mutation flows remain unchanged.
- Source-only changes; no automated checks, static analysis, builds, lint, screenshot or device tests were run.

## Checkpoint 15 — Savings goal creation and empty states

- When no savings goal exists, elevate the existing "new goal" entry point to one tonal action labelled "Ορισμός στόχου αποταμίευσης", retaining the same form callback and mutation-block gate.
- When a goal already exists, retain a compact secondary "Νέος στόχος αποταμίευσης" action. Preserve existing goal progress and all advisory cards without changing calculations.
- Flatten the no-goal explanatory card into a short empty state; make goal deletion visibly destructive (and muted when blocked) while retaining the confirmation dialog and identity checks.
- No validation commands were executed. Device review and release remain separate pending gates.

## Checkpoint 16 — responsive core journeys and Settings preference consistency

- Home: the three canonical primary account cards retain all verified trends, comparison/target context, IBAN and contextual actions. At Android 130%+ font, display the masked balance above the full-width real trend chart instead of overlaying the two. Stack account actions without changing either callback. Standard-font Web-style card layout remains intact.
- Activity month overview: arrange income, expense and net values in readable full-width rows at enlarged font scales; place the month label and accessible prev/next actions on separate rows at that size. Keep existing data scope, count, privacy masking, date navigation, summaries and test tags.
- Settings: subscribe to the existing device SharedPreferences changes so the appearance and amount-visibility selection reflect changes from other active UI routes, including the Home privacy button. Preserve local-only storage and current callbacks. Enforce a minimum target for logout.
- Source-only commit; no builds, lint, automated tests, screenshot diffs or device verification were run.

## Checkpoint 17 — credit-card actions and large-font statement filters

- Align the actual Wallet inline credit workspace with card details: keep the primary pay-card action for payable balances (or primary purchase action otherwise), render secondary purchase and disabled pay-card actions as compact text buttons. Preserve the original callbacks, disabled-state explanatory text and statement/card safety gates.
- Change the card-details entry to a touch-sized secondary action without modifying the secure-card route or secret authorization.
- For large Android font scales, stack the existing All/Payable statement filters instead of compressing Greek labels into one row. Preserve the selected filter, pagination reset, exact test tags and payment identity guards.
- Improve Activity filter accessibility: the repeated Remove action now announces the filter being removed, and empty-state actions respect a 48dp minimum touch target.
- This remains a source-only, unvalidated checkpoint, with no change to finance, auth, backend, update contracts or release state.

## Checkpoint 18 — one-tap search clearing without resetting financial filters

- Give the existing native search input a trailing, accessible Clear icon only while query text is nonempty. The action uses the existing `onValueChange("")` callback: it clears just the query and leaves the currently selected account/category/date/type filter and month-source state intact.
- Retain the established "Καθαρισμός" for resetting all ordinary filters and the distinct "Όλο το ιστορικό" action for exiting an explicitly selected overview month. No reducer, canonical source or search indexing changes.
- Bring the Activity Filters and Clear button affordances to the same 48dp minimum touch target as the shared search field.
- Add a compact clear icon to the existing custom design-system icon registry, without introducing external dependencies.
- Source-only; builds, tests, lint, static analysis and device validation remain unexecuted by explicit request.

## Checkpoint 19 — scrollable card provider and metadata pickers

- The new-card picker previously rendered every available provider/option in a single non-scrollable bottom-sheet Column; with a large certified-provider catalog, the final choices could be unreachable.
- Replace the picker options body with a bounded, lazy, vertically scrollable list while keeping the title outside it. Retain all source values, selection callbacks, existing per-option test tags, conditional manual issuer behavior and sensitive PAN/expiry/CVV write flow.
- Give each choice a 48dp minimum touch target, expose its selected semantics and stack the visual selected marker at enlarged font scales.
- Remove an unused credit-workspace import from the preceding source-only checkpoint; no finance logic changes.
- No builds, tests, lint, static analysis, screenshots or device verification were run.

## Checkpoint 20 — financial numeric input ergonomics

- Use Android's decimal keyboard for total/periodic loan amounts, self-loan repayment amount and hypothetical credit payoff input; use the integer numeric keyboard for loan installment counts.
- Leave ISO dates on a keyboard capable of entering hyphens and keep all current parsing, validation, cent-precision checks, lender/borrower selection, confirmation and save callbacks exactly as before. Keyboard type is an input affordance, not a new restriction.
- Preserve encrypted card data, Web canonical finance contracts, installment payment preview and read-only simulation semantics.
- No builds, tests, static analysis, lint or device verification were executed.

## Checkpoint 21 — credit companion actions and statement details

- In the actual Wallet card companion pane, promote the payable credit-debt action when payable; otherwise promote purchase entry and leave the unavailable payment secondary/disabled. Preserve the exact established payment/purchase callbacks, existing test tags, source-identity checks and quick-entry gates.
- In card-detail statement history, stack All/Payable filter chips at 130%+ font without changing the selected filter, batch limit or expanded statement ID.
- At enlarged fonts, give linked statement identities and monetary amounts separate full-width rows, retaining the exact original amount privacy, canonical linked IDs, non-clickable unsafe-row behavior and navigation tags.
- All work remains source-only. No build, tests, lint, static analysis, visual comparison or device acceptance were executed.

## Checkpoint 22 — accessible Activity filter and Quick Entry choices

- Activity filter sheet: retain exact filter payloads, inclusive dates, historical category text and Apply/Reset callbacks, but stack its Apply and Reset actions when the Android font scale reaches 130% to avoid two cramped Greek labels. Leave the standard layout unchanged.
- Give both date-field clear actions a minimum 48dp touch target. For type/account/category dropdowns, identify the selected choice visibly and through native accessibility semantics without changing option values or the selected source IDs.
- In the Quick Entry More Types sheet, identify the currently selected kind through accessibility semantics, with a minimum whole-row touch target. Preserve all kinds, order, callbacks, and history security behavior.
- This checkpoint is source-only; no build, tests, lint, static analysis, screenshots, or device verification.

## Checkpoint 23 — current-card awareness and Wallet section selection

- The main card artwork retains its original proportions, swipe/stack behavior, expiry/PAN/CVV security and touch targets. At enlarged system fonts (130%+), expose the already-visible active card nickname as a separate native-sized label below the card-selector strip. This does not reveal card secrets or mutate selection.
- In the contextual Wallet section-picker sheet, expose the currently selected section both visually and via selected accessibility semantics. Keep the same three sections, routes and callbacks and make each sheet row at least 48dp high.
- Give the large-font Wallet section selector a consistent minimum touch target. No new primary navigation destinations were introduced.
- Source-only and unvalidated: no compilation, tests, lint, screenshots, CI, device acceptance or release work.

## Checkpoint 24 — Wallet selection semantics name resolution

- Use a local `isCurrentSection` boolean for the Wallet bottom-sheet selected semantics and selected visual state, avoiding ambiguity between the Compose semantics `selected` receiver property and the screen's `selected` enum value.
- This only clarifies the existing presentation expression; all section callbacks/routes stay unchanged. Still source-only and not compiled or tested.

## Checkpoint 25 — safer everyday Quick Entry selections and editable optional date

- The Quick Entry searchable choice sheet now distinguishes an unavailable/stale selected source ID from an unset choice; it never substitutes the first available account/card/category label. Show explicit "no available choices" when the source list is empty.
- Compose display keys include row ordinal plus the original option ID so repeated legacy source IDs cannot crash the LazyColumn; preserve original IDs and existing selection callbacks without synthesizing finance identities.
- Retain the selected label and add selected accessibility semantics to the full-width, minimum-48dp choice rows. No new financial options or default accounts are introduced.
- Add a clear action only to the optional Quick Entry expected-return date once it has a value. Clear through the already-existing `DateChanged("")`-style callback, leaving the required transaction date, picker/IME handling, validation and save behavior unchanged.
- Unvalidated source-only checkpoint: no compilation, tests, lint, static analysis, screenshots, or device testing executed.

## Checkpoint 26 — Quick Entry source-balance privacy and split removal target

- Quick Entry may show canonical card debt and a person's outstanding receivable as supporting payment context. Apply the existing device-local privacy preference to those source amounts so the editor does not reveal a hidden balance merely by opening a payment/repayment flow. Keep user-entered amount fields editable and visible, and retain all underlying debt/overpayment validation and posting checks.
- Observe changes to the privacy preference through the same shared observer as Home and other read surfaces; no new setting or finance persistence is introduced.
- For split transaction rows with removable parts, preserve the existing guarded RemoveSplitPart action but provide a 48dp tap target and an accessible name identifying the exact part number.
- Remains source-only and unvalidated, with no builds/tests/lint/static analysis/screenshots/device verification.

## Checkpoint 27 — responsive multi-part Quick Entry editor

- On large Android font scales (130%+), separate each split-part label from the guarded Remove action instead of forcing both into one narrow row; ordinary font sizes retain the compact horizontal header.
- Add thin dividers between consecutive split parts so amount, category, subcategory and note fields clearly belong to their own part. Preserve the minimum two-part invariant, original source part IDs, all inputs, validation and callback semantics.
- No new financial mutation or defaults. Source-only, with no builds, tests, lint, static analysis, screenshot comparison or physical device validation.

## Checkpoint 28 — responsive Insights periods and operating report

- The live Insights period selector now wraps the exact existing periods and titles instead of squeezing all choices into one equal-width chip row. Each chip meets the minimum 48dp touch target and retains its historical-report/month selection logic and existing tags.
- Monthly operating-report financial rows switch to readable, vertically arranged label/amount pairs at 130%+ Android font, preserving all source amounts and the existing device-local privacy masking. The expanded account-balance listing uses the same responsive layout.
- Standard-font layout, canonical calculations and navigation callbacks remain unchanged. Source-only; no build, tests, lint, static analysis or physical-device acceptance.
