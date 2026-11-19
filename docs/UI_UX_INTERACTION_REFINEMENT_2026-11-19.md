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

## Checkpoint 29 — account ledger search and empty list

- The canonical Account Detail ledger now uses the same native search field as Activity, including an accessible in-field clear action that removes only the query; the kind filter and account scope are unaffected. Keep the original `account_history_search` test tag, the six-record threshold, Web search indexing and existing explicit clear-all-filters button.
- When no transactions match, show the existing message as a compact text state instead of a nested card. Preserve the original distinction between an empty ledger and an empty filtered result.
- No account metadata, IBAN validation, financial totals, unique source IDs or navigation callback was changed. Source only, not validated by build/tests/lint/device.

## Checkpoint 30 — Insights month navigation and secondary details at large font

- When calendar-month Reports navigation is visible, stack Previous/Next actions at 130%+ Android font instead of forcing two labels into one narrow row. Preserve previous and next historical period identities, disabled boundaries and original action tags. Standard fonts retain the existing horizontal layout.
- In the secondary income/net/trajectory analysis card, place the disclosure action below the heading at enlarged fonts while keeping the disclosure state, amount privacy and six-month reporting source unchanged.
- Both controls retain minimum 48dp hit targets; no new finance actions, analytics calculations or persistence changes. No build/tests/lint/static-analysis/device checks executed.

## Checkpoint 31 — responsive forecast drilldown monetary rows

- Preserve all canonical Plan 30-day forecast data, daily portfolio amounts, per-account balances, movement impacts and warning/identity tags. At Android 130%+ font, place long account names or date labels above their own masked financial amount rather than forcing two columns to overlap.
- Reuse one private UI-only amount row for per-day portfolio totals, per-day account balances and projected movement account legs. At normal font sizes the familiar horizontal row stays intact.
- No forecast calculation, inclusion rules, dates, source identities, display privacy preference or write callbacks were altered. Source-only; no build, tests, lint, static analysis or device validation.

## Checkpoint 32 — compact nonurgent Plan ledger

- The real Plan root keeps overdue and next-seven-day obligation items as prominent individual cards. Only the lower-priority "Υπόλοιπο πλάνου" items become flat, divider-separated rows, reducing repeated card chrome in long schedules.
- Keep original date grouping, source item order, unique lazy keys, `s7_plan_item_*` tags, icons, amount privacy and large-font layout. Both urgent and remaining rows share exactly the same row contents, without changing financial model or callbacks.
- This is a source-only visual change; no build, test, lint, analysis, screenshot or physical device validation was performed.

## Checkpoint 33 — large-font read-only movement amounts

- Transaction details: in the canonical split-history section, keep the original part label, optional category path, exact part amount and divider. At 130%+ Android font scale, render each part amount beneath its description so long labels cannot crowd the amount. Retain the original compact row for standard fonts, all source part indices and existing amount-privacy masking.
- Plan Forecast: show each known movement's source/date and its signed net impact on separate lines at enlarged font scale, keeping the normal horizontal compact presentation, canonical movement order, zero-amount presentation and masking unchanged.
- This is presentation-only. No changes to split accounting, future movement inclusion, transaction mutations, source identities or forecasts. No build, tests, lint, static analysis, UI screenshots or physical-device validation.

## Checkpoint 34 — truthful counterparty actions in transaction detail

- Transaction Detail's merchant/beneficiary identity picker already blocks its menu and mutation while a transaction is pending sync, but its "Αλλαγή ταυτότητας" button still appeared enabled. Disable that button under the same existing pending-sync gate, without changing canonical write authorization.
- Expose the currently selected unique counterparty or "Καμία ταυτότητα" in the dropdown both visually and through accessibility selected semantics. Do not mark an imported ambiguous/stale ID as any matching candidate: use the existing safely resolved `matches.singleOrNull()` instead.
- Preserve original `onChooseCounterparty` IDs, clearing action, duplicate-ID guard and test tags. Source only; no builds, tests, lint, analysis or device verification.

## Checkpoint 35 — loan form account availability and selected choices

- In the canonical loan metadata editor, do not open an empty payer menu when the existing eligible unique canonical payer list is empty; the same validation still prevents saving. Make the missing-payer condition explicit in the label rather than suggesting that an option can be selected.
- Mark the actual selected loan kind, payer account and expense category visually and with accessibility selected semantics. Keep existing source option IDs, eligibility checks, original test tags, total/installment recalc, finance mutations and invalid-state save gates unchanged.
- Kind, account and category actions use the existing minimum 48dp touch target. This checkpoint is source-only and unvalidated: no builds, tests, lint, static analysis, screenshots or device validation.

## Checkpoint 36 — searchable certified card provider picker

- In the real Card Create bottom-sheet picker, show the shared native search field only when the certified-issuer/provider option catalog exceeds eight entries. Filter by provider label or canonical provider ID with case-insensitive matching; no source IDs are normalized, discarded or reordered outside the displayed filter.
- An empty filtered search shows "Δεν βρέθηκε εκδότης" and keeps the original selected provider unchanged. The existing field clears its query without dismissing the sheet. Standard small option pickers (type/network/form factor) remain unchanged and all original selection tags and callbacks are retained.
- This is only a UI lookup over the already supplied session catalog, not a change to card/vault creation, bank identity or security contracts. No build, tests, lint, static analysis or device acceptance was performed.

## Checkpoint 37 — lending person search and readable receivable identities

- Lending person selector now reuses the shared native search field, giving an in-field one-tap clear action. The search still uses the same exact person filter and resets the initial displayed-person limit to 24 on query edits; existing test tags and selected-person/history filters are unchanged.
- For 130%+ system fonts, show long person names and their status above the read-only outstanding amount. Normal fonts retain the original compact identity-plus-amount row. A person without a unique active receivable still has no implied balance, and amount visibility continues to obey the existing device preference.
- All original person/source identities, repayment/lending callbacks, duplicate-identity fences and projected history remain intact. No builds, tests, lint, static analysis, screenshots or physical-device acceptance were performed.

## Checkpoint 38 — self-loan savings destination cannot equal payer

- In the existing self-loan payment dialog, a savings account used as the selected payment source is visibly disabled as a savings destination, with an explanatory suffix. Other eligible savings destinations are unchanged and remain selectable.
- Extend the reusable radio-choice row with an optional enabled flag, preserving the same 48dp touch geometry and TalkBack radio semantics; default enabled behavior keeps ordinary loan payer selection unchanged.
- The canonical payment preview still independently rejects same-source transfers and all other invalid routes. No default payer/target is chosen, no finance identity is rewritten, and no payment is created until the existing confirm callback.
- Source-only, without builds, tests, lint, static analysis, UI snapshots or device acceptance.

## Checkpoint 39 — mask card PAN/CVV during initial creation

- Card Create's plain visible input for the full primary account number and CVV now uses Compose's standard password visual transformation to mask entered digits on screen, consistent with device-local secret-data handling. This is a presentation-only safeguard against inadvertent shoulder-surfing during typing.
- Preserve the existing original digit-only input filters, length limits, numeric keyboards, validation error strings, input test tags and secure local vault write of the unchanged characters. The expiration field and non-secret card profile fields remain unchanged. No credentials are persisted to new storage, and no secret is added to UI logs or tracking.
- Source-only and unvalidated; no build, tests, lint, static analysis, screenshots or physical device verification.

## Checkpoint 40 — actionable empty payment-account explanations

- When the canonical loan payment dialog has zero unique eligible payer accounts, its existing required-account message now distinguishes missing account availability from an unselected existing account. The loan payment preview continues to reject both situations unchanged.
- A self-loan return also explains when no eligible savings destination distinct from the selected source exists; if one exists but none is chosen, the original explicit-choice instruction remains. The same warning tags, canonical route checks and final confirmation gate stay intact.
- No new account suggestions, default identities, finance writes, storage changes or altered validation logic. Source-only; no build, tests, lint, static analysis or device verification.

## Checkpoint 41 — safe source identities across Wallet and Quick Entry

- Wallet accounts preserve every imported display row, including duplicated IDs. Compose row keys now use source ordinal + group rather than assuming unique finance IDs. Opening account details requires one nonblank, trimmed, globally unique account ID across all account groups. Ambiguous rows remain visible with an inline warning and an explicitly disabled action; their IBAN metadata is not resolved through a potentially shared ID.
- Archived cards likewise preserve every visual row but use collision-safe ordinal list keys. A restore action is available only when that card ID is globally unique across both active and archived cards. Blank/duplicated IDs are disabled with an explanation rather than invoking an ambiguous restore callback.
- Quick Entry's shared option picker preserves all visible account/category/statement options but refuses a duplicate option ID, marks that row unavailable, and no longer announces a duplicated selected ID as a valid choice. Unique original IDs and existing callbacks remain untouched; optional blank single-value choices retain their existing behavior.
- Financial totals, ledger inclusion, source records, account/card IDs, vault state, security gates and save logic are unchanged. Source-only, no builds, tests, lint, static analysis, screenshots, or physical acceptance.

## Checkpoint 42 — Activity selection integrity and responsive money navigation

- Long Activity histories now separate selected-month context from the "Όλο το ιστορικό" action, and sort/count text from the sort control, when Android fonts are 130%+; normal-font compact rows and existing month/sort callback semantics remain unchanged. Month summary and sort actions meet minimum 48dp touch targets.
- Active Activity filter chips/rows retain exact imported type/category/account IDs, but their removal control stacks beneath long scope labels on large fonts while preserving the existing one-filter removal callbacks and accessible labels.
- Shared Activity selector menus now explicitly mark duplicated source IDs unavailable instead of showing the same duplicated identity as selected and allowing a first-match filter/legacy-edit choice. Stale selections remain visible, and normal unique choices keep the original semantics/actions.
- In the credit card workspace, each statement's payable amount, due date, purchases and payments are separate short read-only lines at enlarged fonts, retaining all amounts, masking, source ID guard, status, due dates and payment callbacks. Normal-font presentation is unchanged.
- Source-only Android UI changes; no finance calculations, transaction posting, backends, builds, tests, lint, static analysis, screenshots or physical-device acceptance.

## Checkpoint 43 — Home identity-gated actions and responsive recent activity

- The owner-required Web-style primary Cash, Payroll and Savings cards, charts, monthly deltas and actual contextual content remain unchanged. Account-scoped Quick Entry and Transactions actions require a globally unique nonblank, trimmed account ID from the full Home account roster, not merely the displayed primary subset. Ambiguous account identities leave their complete card visible but disable those shortcuts, suppress potentially misassigned IBAN lookup, and show a specific warning. The global Home New Transaction action remains accessible.
- Secondary account rows apply the same identity check before opening a detail route, retaining their original order and source balances. Accounts with ambiguous identities remain visible with an inline warning.
- Recent transactions in Home now put amounts below long transaction labels at 130%+ Android font without altering amounts, privacy preference, existing unique-event navigation fences or row test tags. At normal fonts the compact row remains. Recent and attention row touch surfaces meet the 48dp minimum.
- No account financial projections, balance calculations, history chart data, source IDs or callbacks were modified. Source-only; no builds, tests, lint, static analysis, screenshots or Galaxy S24 owner acceptance.

## Checkpoint 44 — keep colliding Home source rows visible

- When selecting one displayed primary account for each Cash/Payroll/Savings role, exclude only that exact selected source ordinal from the secondary list. Never exclude all accounts merely because their ID equals a chosen primary ID.
- Imported accounts with colliding IDs or additional rows in the same primary role therefore remain visible in the secondary account roster, where the checkpoint-43 unique-ID guard disables unsafe account opening. The three original primary cards, balances and real history charts remain unchanged; no source account is merged, deleted or silently preferred for a write.
- Source-only follow-up to the same Home identity-safety batch; no build, tests, lint, static analysis, screenshot or physical owner acceptance.

## Checkpoint 45 — adaptive loan sorting and lending-history readability

- The four existing Loans sort choices now use one wrapping Material FlowRow instead of two hard-coded rows, retaining `sortKey`, ascending/descending behavior, both list-pagination resets and all labels. Every sort chip and the direction action has at least a 48dp touch target.
- Lending history action filters likewise wrap as needed instead of occupying two fixed rows; the same four exact filter values, historical records and 30-entry pagination behavior remain unchanged.
- Lending history entries place the transaction amount beneath long person names/date/status at 130%+ Android font. Normal font stays compact, and historical running outstanding, account context, source ordinal keys and amount privacy are preserved.
- Source-only Android UI. No validation commands, finance calculations, network changes, PR/merge or device acceptance.

## Checkpoint 46 — budget navigation and category selection ergonomics

- Budget month picker and previous/next month actions now have explicit minimum-48dp targets while keeping their existing dirty-draft, save-in-flight and mutation-blocked navigation gates. At 130%+ font, the monthly budget's balance/overrun label and masked amount stack without competing for one horizontal row; normal-size layout stays unchanged.
- The category-limit editor preserves exact category strings, but offers one visual menu row per name when the source list contains duplicates, and announces the selected category both visually and via accessibility semantics. The original selected name, validation, save/delete callbacks, existing test tags and budget calculations are unchanged.
- Source-only and not device/CI validated. No build, tests, lint, static analysis, screenshots, merges or release.

## Checkpoint 47 — consistent Quick Entry search and calendar touch targets

- Searchable account/category/counterparty option bottom sheets in Quick Entry now use the same shared native search field as Activity, Account Detail, Lending and Card Create. Its in-field clear action resets only the local search query, never the selected option, and the old label/subtitle search semantics, list order, identity fences and source callbacks remain intact.
- Both Quick Entry calendar variants (ordinary DateEntryField and the keyboard-aware ProductionDateChoice) now provide explicit 48dp minimum touch targets on Confirm and Cancel. Date/time parsing, selected values, keyboard-dismiss choreography, optional-date clearing and finance drafts are unchanged.
- This is a native presentation/accessibility-only change without finance write or security logic modifications. Source committed without build, tests, lint, static analysis, screenshot/device validation.

## Checkpoint 48 — consistent detailed credit statement readability

- The detailed credit statement history now presents payable amount, due date, purchase total and payment total on separate short rows at Android 130%+ fonts, matching the existing inline credit workspace behavior. The original compact combined rows remain at normal font sizes.
- Keep original statement order/status, exact amounts and privacy masking, duplicate-ID/integrity fences, linked-activity navigation, bounded pagination, payment entry callbacks and existing test tags unchanged.
- Give the linked-statement activity pagination action the same full-width 48dp target already used by overall statement pagination, without changing its 15-item batch size.
- Source-only UI; no builds, tests, lint, static analysis, screenshot comparison, release or device acceptance.

## Checkpoint 49 — card detail mobile geometry and safe card switching

- Card Detail now stacks the credit-limit and available-credit metrics at Android font scales of 130% or larger, preserving the same privacy-aware canonical values and ordinary compact layout.
- The five-entry card-activity preview retains the existing unique-ID navigation gate and signed amounts, but puts long titles and date labels above their amounts at enlarged fonts. The existing show-all/show-less control receives a full-width minimum-48dp touch target.
- The card-switcher sheet is bounded and scrollable for long real card lists, marks the active card, and displays imported duplicate/blank/noncanonical IDs without allowing ambiguous selection. It never silently chooses a first matching card; normal unique-ID selection still passes exactly the same ID to the existing callback.
- No vault secret reveal, card identity mutation, financial data, billing calculation, network route or secure clipboard behavior changed. Source-only, no validation commands or device acceptance.

## Checkpoint 50 — fail-closed Wallet presentation for ambiguous active card identities

- The animated credit-card stack previously keyed and associated all cards by canonical ID, which collapses imported duplicate IDs into one selected card. When the active/archived roster contains a repeated, blank or padded ID, it now switches to a non-secret, complete read-only card roster instead of rendering an ambiguous card face. Safe unique cards remain explicitly openable; ambiguous cards are displayed with a warning, without selecting, revealing, copying or archiving a potentially different source row.
- Wallet derives one global ID-collision set across active and archived cards. Companion credit actions are disabled on an ambiguous selected identity; the normal inline credit workspace is replaced by an identity warning for that ambiguous card. This avoids first-match purchase/payment/statement routing. Existing ordinary unique-card stack animations, focus/keyboard, privacy protection, clipboard lifecycle, activity history and callbacks are unchanged.
- Archived-card restoration reuses the same ID-collision set. No finance source rows are deleted or merged, and no data/vault/network contract was modified. This is source-only and unvalidated.

## Remaining gates and explicit owner action — after source checkpoint 50

This document records source code changes only, not validated Android behavior. The existing 2026 UI/UX follow-up has no additional *confirmed* source-only requirements from the scoped screens reviewed here. Runtime/Compose/CI evidence may expose defects requiring another source change; no completion or release-readiness certification is implied.

**Branch validation prerequisite:** Source branch `android/ui-ux-interaction-redesign-20261119` is NOT merged into `develop`, has no PR and has not produced an APK. The owner explicitly prohibited all builds, tests, lint, static analysis, screenshots and device verification in this workstream. Before any PR/merge or new installable candidate, the owner must explicitly permit the repository's required exact-final-head validation or delegate it to an authorized local agent. Do not bypass protected checks to merge or publish.

**Existing D7 owner action, separately actionable now:** The previously signed and published `v1.0.0-rc19` / build `10018` APK is available on GitHub Releases and can be tested on the owner's *physical* Samsung Galaxy S24 Ultra, installed in place without clearing existing data. This older candidate does NOT contain this branch's UI/UX changes. Its independent owner acceptance must cover installation/continuity, real authenticated finance GET and credit purchase/payment history, current outstanding-debt behavior, and FLAG_SECURE protection against screenshots/Recents exposure of PAN/CVV during transitions. Never put actual secrets in shared recordings. This does not authorize release D8 or substitute for final validation of UI changes.

**D7/D8 are separate:** D7 requires actual owner acceptance; D8 requires independently authorized protected release/publishing. Historical GET /api/data HTTP400 INVALID_DATA, November missing-purchase reports and physical acceptance cannot be declared resolved on source edits or emulator green checks alone. Do not edit generated `STATUS.md`, `TODO.md` or `docs/CURRENT_HANDOFF.md` manually.

Handoff order after authorization: validate this exact feature branch with targeted Android compile/unit/Compose checks, then applicable screenshot/hosted-S24 CI; repair failures on the same branch, review and merge only with protected gates, produce a new signed candidate through the guarded release workflow, and obtain separate real-S24 owner approval before D8. Until permission changes: remain `source_committed_unvalidated`.
