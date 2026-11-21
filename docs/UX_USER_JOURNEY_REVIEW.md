# Android user-journey and element-level UX audit

Permanent review rules for the staged audit (00–08); progress belongs in the
canonical project tracker, not in this document or conversation history.

## Canonical product scope

Read AGENTS.md and docs/ADR-0002-mobile-top-level-navigation.md first:
Android daily-use navigation has **three** roots (Αρχική, Κινήσεις, Κάρτες).
Device/app Settings are secondary. Plan/Insights and Web finance
administration remain internal/test-only, not extra mobile tabs.
The three full Home primary account cards retain their charts and account
actions. Do not weaken data/security/auth/offline/signing invariants.

## Review sequence

00: capture evidence from existing official Compose screenshots, CI provenance
and per-journey checklist. 01: Cards, chosen card detail, purchase/payment and
secure details. 02: Quick Entry kinds, validation and Save/Cancel.
03: Activity search/filter/details/edit and return. 04: Home, account cards,
balance/privacy/attention and shortcuts. 05: Account details, savings, loans,
lending and contextual secondary paths. 06: Settings, updates, PIN/biometrics,
auth/logout. 07: legacy routes and Web-only administration exposure. 08:
integrated cross-route tests, visual/accessibility review, separate real S24
owner acceptance and later protected release.

## For EVERY visible and interactive element

Inspect: (1) why the user entered this page, (2) what they are trying to
achieve, (3) meaning of the control/label, (4) whether the task is already
available in a more natural place, (5) source identity and consequences,
(6) screen after action/confirmation, (7) Back/cancel/return, and
(8) empty/loading/offline/error/long-font/privacy states. Classify
**keep, relocate, consolidate or remove** with explicit code AND interaction
evidence; never remove behavior solely from a screenshot.

First grounded case in Chat 01: CanonicalCardDetailScreen.kt exposes
**Αλλαγή κάρτας**, while card selection exists in parent Cards; MyFinHubApp.kt
currently pushes another CardDetail when changing card. Analyze the
back stack and preserve the originally chosen card on return. Inspect and
correct this only after cross-screen navigation tests, not as an isolated
cosmetic change.

## Evidence and completion

The Android UI Quality job validates official screenshot goldens and now
uploads review.html + review-index.json + review-checklist.md with the PNGs.
The JSON includes exact source head, CI run ID, image SHA-256 and baseline
variant gaps. Run the narrowest real user-journey tests first, then all
required exact-head CI gates and merge reviewable workflow batches.

These images are **synthetic**. They prove visual fixtures, not that a
click works, payments persist, private values are hidden in screen recording,
or that the physical owner's Galaxy S24 Ultra passed D7. Protected D8 is
a separate release decision.

## Cards evidence audit — routing and wording

The mobile Cards tab owns card switching. Card Detail must never host a second
picker. For credit cards with debt the native Cards primary payment action is
**Πληρωμή κάρτας**, not **Εξόφληση πιστωτικής**: canonical Quick Entry explicitly
supports partial payments up to the outstanding balance. When debt is zero,
do not show a disabled payment button; show a valid purchase entry instead.

The `CardCreate` source route and its Compose previews are retained but have
**no user-accessible navigation entry from the current companion Cards tab**;
long-form card creation is Web-oriented under ADR-0002. The old inline credit
workspace and non-companion card-delete dialog are likewise test/legacy
fixtures, not current mobile Cards root. Do not count their PNGs as coverage
of the live screen. Legacy previews still matter for regression and installed
state compatibility; they are not production navigation evidence.

Outstanding visual cleanup requires fresh renderer-verified goldens: in Card
Detail, **Στοιχεία κάρτας** is currently repeated as a heading and button.
Secure card details display protected values behind the owner+AAL2/FLAG_SECURE
boundary; screenshot fixtures do NOT prove physical Recents/screen capture
privacy. Real S24 owner acceptance remains D7-pending.

## Cards identity & vault safety — phase 01c

The animated Cards stack rejected ambiguous IDs, but a routed CardDetail
previously resolved `firstOrNull { id == route.cardId }`. After a finance
refresh, that could display a different physical card under the same ID.
Use one unique-active route resolver across normal and secure detail, the
three card-originated credit Quick Entry actions, and all card-secret
open/reveal/save handlers. The resolver compares the active and archived
catalog and rejects blank, noncanonical and duplicate IDs.

An already-open detail becomes unavailable if its card ID turns ambiguous;
the user can return safely to Cards. Secure details do not inherit an old
revealed card's PAN/expiry/CVV, and the previous reveal is released on source
identity change; a new unsafe CharArray write is wiped instead of dispatched.
The implementation is supported by pure identity tests and Compose
navigation/privacy tests. Valid-card visual fixtures remain unchanged.

Still requiring separately generated and visually inspected goldens: the
repeated «Στοιχεία κάρτας» heading, obsolete CardCreate vault copy, and
context-free «Νέα κίνηση» shortcut. None is justified by deleting canonical
finance functionality or bypassing synthetic screenshot validation.

## Card secure-value copy accessibility (phase 01d)

The visual three-button `Αντιγραφή` pattern in secure card details
previously provided no field-specific action name to screen-reader users.
The compact on-screen labels remain unchanged, but merged Compose button
semantics identify `Αντιγραφή αριθμού`, `Αντιγραφή λήξης`, and
`Αντιγραφή CVV`. The spoken action does NOT include PAN, expiry or CVV.
Retain existing `clearAndSetSemantics` on displayed secret values, clipboard
ownership/expiry guarantees and secure screen capture flags.

Instrumentation executes the real copy port and verifies correct value per
distinct accessible button, while preserving clipboard clearance on disposal
and loss of AAL2 authority. The change is semantics-only, not a visual
reference replacement. The actual Galaxy S24 Ultra remains the only physical
D7 authority.

## Quick Entry: one Back/discard journey (phase 02a)

The parent QuickEntryBackGuard already owned Android system/predictive Back,
but the child ProductionQuickEntryScreen independently owned toolbar Back and
rendered a second discard AlertDialog with a different dismissal vocabulary.
One screen should have **one** authoritative discard decision.

The route-level guard now supplies a shared `requestExit` to the editor.
Toolbar Back and system/predictive Back both use the same dialog:
`Απόρριψη νέας κίνησης;`, `Παραμονή στην επεξεργασία`, and
`Απόρριψη αλλαγών`. Discard resets a dirty draft before navigating away;
keep-editing retains state. Once the server acknowledges a save or the
encrypted offline queue accepts the entry, auto-exit is not blocked by a
discard prompt. This is verified by a real Activity → Quick Entry →
system Back → keep editing → toolbar Back → discard → Activity test, on top
of the existing Card → purchase → discard → Card Detail test.

The normal editor/preview layout is unchanged; screenshot goldens should
remain pixel-identical. Visible text corrections for partial credit payment
(`Εξόφληση πιστωτικής` in subtitle, `Ποσό εξόφλησης` in amount label)
remain a distinct renderer-verified visual checkpoint, not part of this fix.

## Quick Entry selected source identity — phase 02b

The selector UI refused some duplicate target IDs, but reducer Save previously
accepted any matching credit card/account, and the final Android canonical
mutation used associateBy, which silently selected a row on duplicate IDs.
Selected source identity is therefore checked again at the Save button
and authoritative local mutation boundary. Card/account IDs must be
trim-stable, nonblank and unique across the original source rows, including
active/archived card collisions.

A finance refresh may remove or invalidate a source card or account while
a transaction is being entered. The canonical Quick Entry projection now
retains that invalid identity for a dirty draft, hides ambiguous picker rows,
and disables Save instead of silently switching to another card/account.
Credit debt, statements and repayment bank matches never resolve
ambiguous card IDs. Unit, mutation and Compose tests enforce these cases.

No normal visual text/layout or screenshot baseline is changed here.
The partial credit-payment title/amount wording and remaining visual
Cards issues are deferred to renderer-validated golden regeneration.

## Account Detail canonical route identity — phase 03a

After verified, merged Quick Entry identity PR #500 (`develop`
`7c52a4e0e8e6ae052cd9506be7c84ad2f983fac4`), the mobile Account
Detail route still used `firstOrNull { id == route.accountId }`. The
projection includes all non-credit canonical account rows, while the canonical
source may contain duplicate IDs (even a collision with a hidden credit-kind
account). This made account balance/history and the server IBAN edit/delete
actions potentially bind to an arbitrary first row on finance refresh.

The canonical Home and money projections now carry original source-wide
account ID counts. On Home, ambiguous source IDs disable primary actions and
secondary account entry with the existing identity warning rather than leaving
a clickable row that silently does nothing. Every account opening from Home,
All Accounts and Wallet, plus an
already-open Account Detail, resolves one nonblank, trim-stable source identity
only. On collision or stale ID, detail shows the existing account-unavailable
state, not the first matching finance row; IBAN and activity history from an
invalidated route are not exposed. IBAN save/remove and account-prefilled
Quick Entry callbacks independently recheck the routed account at action time.

Pure unit regressions cover unique, duplicate, missing, whitespace and
cross-kind IDs, including a refresh that invalidates a previously valid
account. Compose navigation tests cover rejection before opening and
revocation while the detail is already open. PR #501 passed all six
exact-head checks at `0317d600` and was squash-merged to `develop` as
`59ed42f`. Valid account layouts and text are unchanged, so no official
screenshot baseline is replaced. Owner physical S24 D7 approval and
separately safeguarded D8 release remain pending.

## Partial credit payment copy — visual checkpoint 02c

The canonical `card_payment` operation permits both partial payments and
complete payoff, so the Quick Entry subtitle and amount field must not
promise an automatic full settlement. Use **Μερική ή πλήρης πληρωμή** as its
subtitle and **Ποσό πληρωμής** as its amount label. Unavailable same-bank
source and pending-save messages must likewise say **πληρωμή**, not
**εξόφληση**. Preserve the same canonical mutation kind, card/account
selection, amount validation, statement allocation and finance behavior.

The official `ProductionQuickEntryCardPaymentScreenshot` was regenerated
by the Android UI Quality Compose renderer in run `44923462573`. The PNG
was visually inspected against the previous golden: only subtitle rows
122–154 and amount-label rows 454–486 changed, with no other layout drift.
Its exact renderer bytes (SHA-256 `7472ff879a9520bf88ce14687e4c40a2b129319158e78b8fa719ec71c66ef179`)
are committed in the source branch. The first CI run had one expected screenshot
mismatch and one old-wording instrumentation assertion; the latter was
corrected. All six required exact-final-head checks must pass before merge.
Never approve altered images solely to make CI pass.

The visually duplicated Card Detail **Στοιχεία κάρτας** heading remains a
separate visual-golden task; do not bundle unreviewed screenshot changes
or replace the owner's physical S24 D7 acceptance with synthetic renders.


## Bulk cross-screen Compose review — 102 screenshots (phase 03b)

Source: Android UI Quality run `44927422834`, exact screenshot
source commit `6ccee4ddb6201c2553b4d794177eb339a79a55cf` for
PR #502, validation `success`. The official gallery/review-index contains
102 **synthetic** Compose reference PNGs in 11 user-journey groups. The images
were examined together as compact grouped contact sheets, with the earlier
single-screen partial-card-payment before/after comparison retained as
separate direct visual evidence. This is a **bulk visual triage**, not a
completed per-element interaction, TalkBack, scroll, touch-target or physical
Samsung S24 Ultra audit.

| Journey group | PNGs | Bulk visual assessment |
| --- | ---: | --- |
| Home | 7 | Three detailed primary account cards/history charts preserved; light, dark and large-type references visible |
| Activity | 15 | Search, filters, transaction edit/detail, pending and empty states represented; actual return/persistence requires behavioral testing |
| Quick Entry | 8 | Credit payment correctly says partial/full payment and amount of payment; split and reconciliation layouts represented |
| Cards root | 5 | Card stack and compact card selection remain visible; generic New transaction is an already-retained product decision |
| Card Detail | 3 | **Confirmed:** heading and secure-details button both read `Στοιχεία κάρτας`; no second card switcher |
| Secure Card Detail | 3 | Field labels and copy controls visible; fixtures are fake values and do not prove FLAG_SECURE or on-device privacy |
| Account Detail | 3 | Single-account balance, transaction list and contextual new-entry action visible |
| Android Settings and diagnostics | 17 | Light, dark, large-font, pending updates and diagnostics represented; no Web admin root inferred |
| Authentication | 3 | Login, lock/PIN fallback and PIN enrollment rendered |
| Secondary journeys | 15 | Grouped notifications, money/wallet and contextual screens; screenshots alone do not prove active navigation |
| Legacy/test-only | 23 | Plan/Insights, card-create and retained administrative previews must **not** be treated as primary Android routes |
| **Total** | **102** | No missing light/dark/large-type baseline variants reported by the official review index for its defined groups |

**Actionable source-backed delta:** in the *companion* Card Detail only,
keep the section title `Στοιχεία κάρτας`, but label its actual secure-details
button `Προβολή στοιχείων`. The button still invokes
`onOpenSecureDetails(card.id)` using the already-validated card identity.
Non-companion/legacy copy remains unchanged. The real Compose test
requires one section heading and invokes the button to assert it opens the
selected card. In Android UI Quality run `44930334611`, the original PR
head `931728e` passed tracking, Android CI and both emulator shards plus
aggregate, while screenshot-regression detected exactly three expected
differences. The official Compose renderer regenerated and uploaded all
three Card Detail goldens. Direct before/after inspection confirmed the
changed pixels lie **only within the button label**, with unchanged control
bounds and no layout drift in light, dark or 150% font. The exact renderer
PNG SHA-256 fingerprints are:
- light: `9d9e7f56e8c5293643e545cb9fca7d4973996741bca172f13768c3bc7390db31`
- dark: `d567244dbabb2822b0b2f9559741f37265eb0fca690fa527f310d9e42bb891de`
- large font: `1ed04e6e721778700edbfdb2fad8739cbfd19099af0158b585a2cc96e39787bf`

All three approved renderer outputs are committed as actual official goldens.
PR #503 passed all six exact-head gates at `a4ad7b2` and squash merged into
`develop` as `74d4b88`. The corrected action is integrated; screenshots
remain synthetic and do not establish owner physical S24 approval.

No other geometry defect was sufficiently confirmed by the *grouped,
first-viewport* visual evidence to justify speculative production changes.
Large-font screenshots may depict scrollable content extending offscreen;
this is not evidence of clipping without scrolling/semantics tests. The
historical `CardCreate` fixture is not a live companion route; its
device-only vault message still requires separate finance-contract scrutiny,
not just a cosmetic replacement. Known unresolved owner acceptance remains
physical S24 D7 plus independent protected D8 release.


## Activity → Home → Settings behavioral review — checkpoint 03c

The full-ledger source ID resolver already protected against blank and
duplicate IDs across the unfiltered source, but accepted padded IDs such as
`" event "`: their exact nonblank text was unique, so a row could navigate to
a financial detail or enter edit/delete callbacks even though the source ID
was not canonical. The historical payer picker had the same gap for unique
account IDs. Source IDs now require `id == id.trim()`, nonblank content
and uniqueness; rows with bad IDs **stay visible in the Activity ledger and
financial totals**, but explicitly report `Μη έγκυρο αναγνωριστικό · η κίνηση
δεν ανοίγει` and cannot be mutated. No ID is silently normalized or
retargeted, and current valid route behavior is unchanged.

Source-backed user-journey coverage:
- Activity ledger: the malformed persisted row stays readable and disabled,
  whereas a valid unique row can still open
- Already-open Activity Detail: when a finance refresh replaces the original
  with a padded source ID, detail becomes unavailable and no edit/delete
  action survives; callbacks use the same live resolver
- Legacy payer editing: a unique but padded account ID never appears in a
  writable selection
- Home → Settings → Home → Activity: switching visibility on Home, then hiding
  amounts in Settings, preserves the same device-local preference and
  re-masks Activity figures on return

This checkpoint changes no valid production layout/text; existing official
goldens should remain identical. The regression tests execute on hosted
Android/S24 emulator shards before merge. An emulator result cannot certify
Samsung-specific Recents/Play Protect behavior or complete the owner's
physical D7 acceptance. Do not start protected D8/APK release from this
checkpoint; the required integrated deep pre-build audit remains separate.

**First hosted correction evidence:** PR #504's original head `e1b62cb`
found two incorrect test imports and one literal-tab fixture; those were
fixed in the same PR. On head `cc08675`, tracking, Android build/unit,
102 screenshots and S24 shard 1 passed. The S24 shard 0 instrumentation
run completed 203 tests with a single failure in an older, independently
existing `ActivityQuickEntryTest.activityDatePicker_opensAndCancelsWithoutChangingQuickEntryKind`.
That test failed **before opening the calendar**, because
`Espresso.closeSoftKeyboard()` waited for window focus during an IME
transition, as the actual stack trace confirms. The correction removes
only that Espresso-dependent preparatory step; it still uses the actual
Quick Entry date action, waits for the calendar, cancels, and verifies the
expense form. Require all six checks on a new immutable head; neither
an emulator race nor a previously green unrelated test may be ignored.

**Second hosted correction evidence:** head `0d2289c` again passed
tracking, Android verify, screenshot regression and S24 shard 1. All
203 tests on S24 shard 0 ran; the same old calendar test alone failed
because the `quick_entry_date_picker` control was not displayed while
the amount field's automatically opened IME still covered it. Removing
`Espresso.closeSoftKeyboard()` had exposed the real visibility issue
rather than fixed the test precondition. The latest test-only correction
clears the Android activity window's input focus and asks
`InputMethodManager` to hide the IME; then waits until the real date
control is displayed, physically clicks it and retains the calendar
open/cancel and unchanged entry-kind checks. It does not disable the
auto-focus in production, suppress visibility assertions, bypass clicks
or change a screenshot reference. All new final-head CI gates must still
succeed before merge.


## Integrated behavior audit: scheduled Home attention source identity — checkpoint 03d

PR #504's final head `277cfc1` passed all six exact-head required
tracking/Android/screenshot/S24 shard-and-aggregate checks and squash merged
to `develop` as `cb3ac83`. Its source-ID and local privacy-preference
journey fixes are integrated; neither CI nor 102 synthetic screenshots
constitute owner physical S24 acceptance.

**Confirmed current native issue.** Canonical pending scheduled obligations
are projected to Home as at most three attention items, of which only two
are displayed. `HomeAttentionDetail` previously used
`firstOrNull { it.id == route.attentionId }`, while
`HomeAction.DismissAttention` removed *every* visible row with that ID.
If canonical scheduled source IDs collide, the row could open an arbitrary
first obligation or dismiss more than the selected obligation. Duplicates
outside the visible two/three-row cutoff were not checked.

**Correction.** Carry the ID counts across the *whole* canonical scheduled
source in `HomeUiState.attentionSourceIdCounts`, not just visible alerts.
`uniqueAttention` resolves only an exactly one-of-one, nonblank, trim-stable
source ID. Home retains and visibly warns about an ambiguous/invalid alert,
but disables its click. Real Home navigation rechecks before opening,
already-open detail becomes unavailable on a source collision, and both
"Απόκρυψη για τώρα" and the contextual activity/plan action recheck
identity at invocation. `DismissAttention` also refuses an ambiguous ID
at its reducer boundary. No source ID is silently trimmed, no finance
mutation is issued, and normal Home content/layout remains unchanged.

**Verification authored, not yet passed:** pure unit checks cover valid,
padded, visible-duplicate and hidden-source-duplicate alert IDs plus
fail-closed dismiss. Compose tests cover the disabled, warned Home row and
the full Home → alert detail → finance-source collision → unavailable
journey. The existing Home screenshots use valid synthetic fixture IDs,
so no official golden should change. Require fresh six-of-six hosted
checks on the final PR head before merge. Physical Galaxy S24 Ultra D7,
finance GET and historical credit purchases, integrated deep pre-APK audit
and protected D8 remain independent open gates.
