# Phase 6 — general offline-first mutation queue

Tracker: #56

## Goal
All currently supported canonical Android finance mutations must remain usable without connectivity and reconcile later:

- create transaction;
- edit transaction;
- delete transaction;
- overall budget update;
- canonical card deactivation.

## Invariants
- Last server-accepted canonical document stays separate from local intents.
- Pending intents are encrypted with the existing `finance_offline_v1` DataStore + Android Keystore identity.
- Existing append-only cache is migrated in place.
- Local projection updates immediately and survives process death.
- Reconnect always loads fresh server state first.
- Reconciliation removes only a satisfied ordered prefix so later dependent work cannot be dropped independently.
- Only a leading sequence of `NEVER_SENT` intents may replay automatically.
- Intents become `NEEDS_REVIEW` before crossing the write boundary.
- Ambiguous attempts are never automatically replayed.
- Explicit retry reloads/reconciles server state before replay.
- Card secret cleanup occurs only after canonical card deactivation is confirmed committed by server state.

## Network-bound exclusions
First login/TOTP, server PAN/expiry access, and app update check/download remain network-bound. Device-local CVV save/delete remains local.

## Validation
Run normal functional CI during implementation. Batch UI/screenshot/accessibility validation only once at final UI acceptance if visible UI changes are retained. Final authority remains the owner's Galaxy S24 Ultra with mixed offline mutations, kill/relaunch, reconnect and web/server parity checks.
