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

The existing official `ProductionQuickEntryCardPaymentScreenshot` depicts
the old text. Before integrating this visual change, regenerate that PNG
using the official Compose screenshot renderer, inspect the actual output,
commit the reviewed golden and require all six exact-final-head gates. Never
approve altered images solely to make CI pass.

The visually duplicated Card Detail **Στοιχεία κάρτας** heading remains a
separate visual-golden task; do not bundle unreviewed screenshot changes
or replace the owner's physical S24 D7 acceptance with synthetic renders.
