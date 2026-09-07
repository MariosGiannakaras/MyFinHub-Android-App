# RC6 owner-feedback correction

**Date:** 2026-09-07  
**Tracker:** #73  
**Implementation PR:** #88  
**Overall progress:** 4/6

`1.0.0-rc6` / `10005` remains a technically valid same-signer publication, but it was physically rejected on the Samsung Galaxy S24 Ultra and is not an accepted release candidate.

## Corrections implemented in this pass

- Account identity no longer exposes canonical shorthand such as `Π-Μ` / `Π-Α` as the user-facing role. The Android projection separates the account role/name from the institution and uses the approved local provider marks where a provider is known.
- Card creation is a visible canonical action in `Περιουσία`, with provider, kind, network, form factor, last-four and credit-limit metadata written through the existing canonical finance mutation path.
- Card detail can save PAN/expiry through the existing owner+AAL2 native `PUT /api/card-secrets` boundary. CVV remains device-local and never enters a server request.
- Credit-card detail exposes canonical `card_purchase` / `card_payment` history linked by stable `cardId`. Purchase and payment actions open the existing production Quick Entry flow, so accounting, durable offline queueing, optimistic concurrency and ambiguous-write reconciliation stay centralized rather than being reimplemented in the card UI.
- Card-stack indicators retain stable card identity, visibly track the active card and can select a card directly.
- Card deactivation waits for the approved shred/collapse transition before invoking the existing canonical deactivation mutation. Reduced-motion behavior skips decorative delay while preserving the same mutation semantics.
- `Εικόνα` has been restructured around month-over-month narrative, a common-scale four-month income/expense chart and category concentration instead of another set of repeated KPI cards.
- Compile compatibility is explicit across retained card-detail surfaces: the secure `Saving` state is handled exhaustively rather than falling through older reveal-only state machines.

## Security and data-boundary constraints

- No service-role credential or card-vault encryption key is introduced into the Android client.
- PAN/expiry use only `/api/card-secrets` with the current owner+AAL2 bearer session.
- CVV remains in the Android encrypted local vault only.
- Finance purchases/payments continue through the canonical transaction-entry mutation and the existing offline/reconciliation infrastructure.
- Card deactivation remains canonical state deactivation; secret cleanup occurs only after canonical commit.

## Validation still required

Before PR #88 can merge, the exact head must pass Project Tracking, Android CI/R8, screenshot regression and S24-target instrumentation. Every changed production surface must receive fresh real Compose light/dark/150%-font renders and personal inspection before screenshot baselines are accepted. A higher protected same-signer candidate may be published only after that hosted validation, and overall progress remains 4/6 until physical owner acceptance on the Samsung Galaxy S24 Ultra.
