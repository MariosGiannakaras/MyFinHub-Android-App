# D7 integrated deep audit before the next distributable APK

**Audit start:** 2026-11-22. **Implementation baseline:** Android `develop` merge `c306ab4c9f4bf6ab3131cd0179eb2ef7bf35c4ca` (PR #506, six of six required gates green). **Scope:** native daily companion (Home / Activity / Cards / Quick Entry / device Settings), direct dependencies, offline finance, card secrets, update/install and source-backed user journeys. **Decision: BLOCKED — not a release approval.**

This is a living, evidence-based audit checkpoint, not a claim that inspecting a file or viewing a screenshot proves runtime correctness. Existing verified evidence is reused; source inspection, hosted test execution, actual phone operation and backend verification are separate evidence classes. The pre-APK decision must be reevaluated after this source branch is tested/merged and after any subsequent production source changes. The authoritative change status belongs only in `tracking/android-project-state.json`.

## Status by risk domain

| Domain | Scope and strongest evidence available | Status for distributable APK |
|---|---|---|
| 1. Primary navigation and workflows | Three root tabs Home / Activity / Cards; native secondary Settings and Quick Entry. Source-backed journey fixes #502–#505 each passed all six exact-head gates and merged. Home alert identity now checks the full scheduled source rather than first match. | **Source/hosted CI accepted on merged baseline; physical S24 still needed** |
| 2. UI/UX, responsive and accessibility | Official synthetic Compose screenshot artifact run `44927422834`: 102 PNGs grouped into 11 journeys, including dark/light/large-type reference variants; 15 reviewed contact sheets. Card Detail action correction #503 re-rendered three goldens. Compose semantics and scrolling tests cover selected flows. | **Partially verified; actual owner gestures, TalkBack, font scale and scroll on S24 outstanding** |
| 3. Financial ledger/account/credit math | Shared canonical projections, strict monetary value and date validation, unique source identity and real card-purchase/payment guards inspected. Past green exact-head Android unit/emulator tests exercise fixture flows. Historical owner GET failure and November credit records do not become resolved by this. | **Blocked on real finance GET and reported missing purchase linkage** |
| 4. Offline persistence and reconnect | `EncryptedFinanceLocalStore` uses Android Keystore encryption, preserves v1 keys, keeps server snapshot separate from durable pending intents and validates optimistic replay. *New audit finding:* `distinctBy(intentId)` previously coalesced all persisted intents before duplicate checking, silently discarding conflicting work. This checkpoint adds a legacy/generic merge that rejects conflicting IDs and never downgrades NEEDS_REVIEW. A failed restore now retains IV/ciphertext and places its owner slot under a durable recovery hold, rejecting overwrite and halting the ViewModel sync path. The former v1 single slot is read without deletion; new encrypted writes use SHA-256-fingerprinted per-owner slots so a different login cannot overwrite the previous owner's never-sent work. | **PRs #506 and #508 six exact-head gates SUCCESS and merged; physical encrypted-cache migration/recovery still unverified** |
| 5. Authentication, privacy and secrets | Source uses early API33+ Recents snapshot disable, pre-33 secure flag, restricted revealed-card `FLAG_SECURE` leases and transition grace; offline cache is owner-scoped. Private card copy actions are sensitive. Previous rc18 physical recording exposed full card secrets during navigation; #487 correction exists and rc19 includes it. | **Physical rc20 screenshot/Recents and screen-recording retest mandatory; PRs #509/#510 merged, offline-vault follow-up pending CI** |
| 6. Network failure/sync authorization | FinanceRepository separates read retries from non-retried money writes, guards stale response epochs, validates documents and fences rejected finance reads. Legacy owner GET `/api/data` HTTP400 `INVALID_DATA` was not reproducible in later read-only projections; old server logs have expired. | **Blocked — no verified resolution or authorized server change** |
| 7. Installer, release and integrity | Manifest disables Android backup, exported launcher only, internal update receiver, `REQUEST_INSTALL_PACKAGES` (user-confirmed install); no unattended-update permission in observed manifest. Protected production workflow resolves an exact green source PR, checks package/version/direct APK permissions and protected signer. rc17 was Play Protect-blocked; owner later reported rc18 allowed, but rc19/rc20 physical verification is pending. Public rc20/10019 supersedes rc19 for latest-version preflight, but neither version has an owner-completed physical acceptance record. | **Public rc20 publication metadata verified; no new build, release or installation authorized by this audit** |
| 8. Integrated physical acceptance and release decision | Owner Galaxy S24 Ultra only. Verify exact installed version/signer continuity, existing session/local cache and queue preservation, Recents/screen recording and card secret expiry, live GET and two historical card purchases, transaction create/pay/edit, offline→online exactly-once behavior, accessible scrolling and updates. | **Not run against a new candidate. D7 and D8 remain open** |

**Source review coverage:** 7/8 domains reviewed with available code/previous evidence. **Release-cleared:** 0/8; a source review alone does not clear a device-dependent domain. No percentage of application completion is inferred from these audit counts.

## Confirmed risks and priorities

1. **P0 / physical security acceptance:** rc18 screenshot recording showed PAN/expiry/CVV during a transition. The #487 root secure-window fix was in rc19, but there is no verified rc19 or newer rc20 Galaxy S24 Ultra screen-recording/Recents result. No new APK can be accepted based only on `FLAG_SECURE` source code or emulator snapshots.
2. **P0 / finance contract:** The owner-observed 2026-11-12 GET `/api/data` HTTP400 `INVALID_DATA` and two reported unlinked credit purchases remain unresolved. Do not change server finance, delete/recreate transactions or infer linkage without authoritative live evidence and authorization.
3. **P1 / pending mutation integrity, code finding:** In `EncryptedFinanceLocalStore.load`, `(generic + migratedLegacy).distinctBy(intentId)` made the following duplicate-ID check vacuous. Two different intents sharing an ID could have silently lost one operation during offline recovery. New helper `mergeRestoredPendingMutations` validates duplicates *within* each stored format, compares cross-format migration kind and payload exactly, preserves deterministic replay order, and promotes review state to `NEEDS_REVIEW` whenever either copy requires it. Unit tests cover distinct replay, matching migration, conflicting collision, same-format duplicates, and review-state safety. Test authoring is **not** a passed build.
4. **P1 / invalid encrypted cache recovery, source correction authored:** The previous `load()` called `clear()` on all decryption, replay and structural failures. The new path retains the original encrypted IV/ciphertext, persists either the legacy `finance_recovery_hold` or scoped `finance_recovery_hold_owner_<SHA256-userId>`, throws `FinanceLocalRestoreException` and refuses writes to the affected owner slot. A legacy/global hold also blocks new writes until deliberate destructive recovery. `FinanceProductViewModel.loadFresh` treats this as a non-retryable owner-facing recovery failure rather than proceeding to GET/replay/write, and offline mutation fallback also refuses creation. Android instrumentation tests force decryption failure and verify IV/ciphertext persistence and blocked overwrite across new store instances; six JVM migration tests also run. **PR #506 passed the required six exact-head gates and merged as `c306ab4`.** This quarantines rather than recovers the snapshot: no owner-friendly export/recovery UI or physical corrupted-cache test is complete; do not call it lossless recovery.
5. **P1 / owner-switch durability, source correction authored:** On the old single-slot `finance_offline_v1`, a second user's valid server GET could overwrite the earlier owner's encrypted pending queue. The new code saves each owner into an independent hashed-identity key pair within the existing encrypted DataStore; the old unscoped bytes remain unchanged. On a new install, the matching owner reads its own slot. On an upgrade, if that owner has no scoped data, loading falls back to the prior v1 slot; an owner mismatch safely returns null and the second owner writes only their own scoped slot, not over v1. A scoped owner mismatch or corrupted slot fails closed. Dedicated Android tests restore separate owners' queues, simulate a real v1 on-disk payload by moving the original Keystore ciphertext into legacy keys, switch owner before migration, verify the legacy bytes remain identical, and test per-owner corruption quarantine. The existing `clear()` remains explicit/destructive for the entire DataStore and is not called on login/logout. **PR #506 six exact-head gates passed; owner physical installed-data migration not verified.** Physical migration and installed-data acceptance remain open.
6. **P1 / release-source drift:** the newest publicly visible prerelease is rc20/10019, tag source `0285a8724605c025e39a049884bf552484c0fea7`. It predates merged PR #506 (and later Android `develop` changes), so its code is not the current integrated audit baseline. A green CI on these later branches cannot be used as the signer/Play Protect/physical approval for a new APK.

## Dependency-chain test matrix

| Journey | Checkpoints required | Current evidence/limitation |
|---|---|---|
| Home → Accounts → Activity Detail → Edit/Delete | Cash/payroll-only Home total; full 3 primary account history charts, privacy state; unique account & transaction IDs; no stale detail mutation | Existing Compose/unit CI plus #504; S24 tactile/accessibility result missing |
| Home scheduled alert → Detail → Hide / contextual Activity | Source-wide alert identity, duplicate source beyond top-two cutoff, live revalidation after refresh | #505 exact-head six gates green and merged; physical live data not exercised |
| Cards → secure details → Back/Recents/Quick Entry | Card identity, AAL2, masked/unmasked card secrets, screenshot/recording, no secret under outgoing scene, expiry/clipboard | Source/hosted tests and #487; rc19 physical capture pending |
| Expense → Credit Purchase → Partial Card Payment | Exact card/statement identity, signed amount and account balance, as-of debt, pending/success, no false zero-debt error, mutation acknowledgement | Golden/Compose and credit regression tests; actual owner card history/GET unresolved |
| Offline transaction → device process death → reconnect | Last accepted server state, encrypted owner-only snapshot, durable intent order, safe retries and NEEDS_REVIEW when response ambiguous | PR #506 duplicate migration, scoped-owner/legacy v1 and recovery-hold tests passed six exact-head CI gates; physical installed-data/owner-switch and exactly-once acceptance unresolved |
| Home visibility → Settings → Activity → app background | One local visibility setting and masked amounts; secure recent-task snapshot, re-lock, no unintended state reset | #504 Compose test green; physical Recents/biometric session retest missing |
| Protected private updater → in-place install | Same package/signing key, exact version, user confirmation, manifest check, preserved installed local data, Play Protect result | Release gates source checked; no new release authorized or physically accepted |

## Gate and permitted work

- During this checkpoint, continue **source changes, narrow unit/instrumentation tests, GitHub PR review and standard CI**. Do not run a protected production publisher, generate a new distributable signed APK or promote `main`.
- Audit completion requires no unresolved P0 and a documented disposition for every P1; verify all affected tests on the final exact branch head, both S24 emulator shards, all Compose golden comparisons, review actual rendered changes, and independently obtain physical S24 confirmation. A new release-source SHA needs revalidation, not copy-pasting a previous green result.
- Never use backend modifications, delete user finance records, bypass Play Protect, rotate the enrolled signer or unlock hidden finance administration to satisfy an audit item.
- **Owner action (when requesting physical gate):** test only a verified, explicitly identified release candidate on the supported Galaxy S24 Ultra, report displayed version, Play Protect and install result, confirm current session/local financial state survived, check card-secret screen recording and Recents, check canonical finance GET and card purchase/payment linkage, and report screenshots/codes **without card numbers, CVV, credentials or account secrets**. Device work is not performed by CI.

## Evidence boundaries and follow-up

Merged PRs #502 (card payment wording), #503 (Card Detail goldens), #504 (Activity ID/Settings privacy) and #505 (scheduled alert ID) each had six exact-head green gates and merged into `develop`. Their success is *not* proof of live backend, installed upgrade or physical owner acceptance. PR #506's immutable final source head `5d449b83e8e681f30923fd458ff7a37e243a09ca` passed all six mandatory gates (tracking, Android verify, 102 official Compose screenshots, S24 emulator shards 0 and 1, aggregate). It was squash merged to `develop` as `c306ab4c9f4bf6ab3131cd0179eb2ef7bf35c4ca`. Earlier Maven Central HTTP 429 dependency failures were unrelated to product screenshot differences and were not used as merge evidence. The deep audit remains open after source merge: signed release source, real phone and live finance acceptance are separate gates.

**Latest published candidate reconciliation — 2026-11-22.** Public GitHub Releases records `v1.0.0-rc20` / versionCode `10019` published on **2026-11-19 23:56 UTC** (2026-11-20 Greece time), newer than rc19. The direct APK asset is `MyFinHub-1.0.0-rc20-10019.apk`, **4,012,124 bytes**, with GitHub asset digest SHA-256 `f64c3bc9c2e6972896ab1ca3429ea6ef6c29fdd1e2a58f4ab0cddc70f1b23f6f`. The tag points to source commit `0285a8724605c025e39a049884bf552484c0fea7`. The release page describes an enrolled-signer direct APK and a Play-policy AAB, but this public metadata **does not** prove which APK is installed, its physical signer continuity, Play Protect disposition, real Galaxy S24 Ultra capture protection, preserved user data, GET /api/data or historical card purchase linkage. The rc20 owner test remains **not assessed**. Earlier rc19 observation remains historical evidence, not an alternative current release pointer. No new build or signing requested.

**Read-only phone preflight:** `scripts/s24_physical_preflight.py --expected-version-code 10019 --json` now accepts precisely the known rc20 code/name pair, while older rc19 is reported as older and unknown rc21 remains unrecognized. The output never claims signer verification, physical acceptance or Play Protect clearance. Do not install, bypass protection, inspect card secrets or collect credentials as part of this preflight. The 8-domain release decision remains **BLOCKED**.


## 03e additional failure-mode review: unreadable encrypted DataStore

**Confirmed source gap at the installed-file boundary.** The #506 encrypted-cache recovery
hold began *after* `context.financeOfflineDataStore.data.first()` returned. If reading
the preferences file itself throws an I/O or corruption exception, that error bypassed
`FinanceLocalRestoreException`, which is the ViewModel's fail-closed signal. A
failed `holdForRecovery()` write could likewise leak a raw storage exception instead
of the recovery signal. This is a structural gap, not proof that the owner's phone
currently has a corrupted DataStore.

**Correction integrated through PR #508:** Convert non-cancellation read and hold-write
failures into `FinanceLocalRestoreException`, preserving `CancellationException`
semantics. The existing `FinanceProductViewModel.loadFresh` handler can then refuse
server GET/queued write/replay, clear only volatile finance state and show its
nonretryable owner-guidance message that explicitly distinguishes no app-initiated deletion from guaranteed recoverability. The encrypted preferences file is **not cleared
or replaced**. When the underlying DataStore is unwritable, a durable hold cannot
be claimed to exist; the current attempt still fails closed. Recovery from actual
corruption is not implemented.

**Hermetic Android regressions authored:** inject a DataStore whose read throws
`IOException` while another valid cache retains its queued intent; inject a partial
owner slot whose hold write also throws; verify original IV/ciphertext and queued
work remain unchanged; verify cancellation is propagated, not mislabeled as
corruption. The existing owner-scoped/v1 migration and other #506 checks remain
unchanged. These tests passed all six final-head PR #508 gates and merged in `develop` as `27351d0231c24583a1ed8e33095034ea0681d677`. They do **not** establish physical corrupted-cache recovery.

**Gate:** owner physical S24 encrypted-install upgrade/capture/Recents, real finance
GET and historical card-linkage evidence are still independent blockers. Neither
this source correction nor hosted emulator testing authorizes a new signed APK.


## 03e additional privacy review: owner/bearer transition before Compose attachment

**Concrete source-level issue.** In `MyFinHubRoot`, `authState` can already be
`Ready` for user B or a refreshed bearer, but the finance and card-secret
ViewModels are attached in `LaunchedEffect(authState)` **after** the current
composition. Until that effect runs, the root previously forwarded the
observed old owner's `FinanceProductState.Ready`, potentially revealed
`CardSecretUiState.Revealed` (PAN/CVV), and a pending vault cleanup state
to the new authenticated screen. Owner-scoped IBAN and history were already
guarded during that gap; finance/vault screen state was not. This is
confirmed by reading the dataflow, not by a reported physical incident.

**Targeted fix authored on the same integrated D7 audit:** Shared
`matchesSensitiveUiSession` checks exact owner ID, access bearer and AAL,
not refresh-token text. The root masks finance presentation as `Loading`,
card secrets as `Hidden` and vault cleanup as `Idle` until the new
authentication identity matches the corresponding ViewModel's attached
session. This masking also applies to root-level auth-rejection effects,
finance diagnostics and root secure-vault cleanup decisions, avoiding
misattributing a stale authentication error to a new user. On owner switch,
`FinanceProductViewModel.attachSession` also immediately clears prior
`Ready` state and sync timestamp **before** asynchronous new-owner loading.
`CardSecretViewModel` already redacts the old vault state when attached;
the new root gate closes the earlier pre-attachment frame.

**Preventive tests authored:** JVM checks null/new-owner/bearer/AAL
mismatches and refresh-only preservation; JVM presentation tests verify
that stale finance errors, PAN/CVV and cleanup states cannot reach the new
session; S24 instrumentation now checks actual card-secret ViewModel
attachment and transition authority. **PR #509 passed all six exact-head GitHub checks at
`53b51b9952be2abbbdcbb91eb95c254f4bda3827` and squash merged to
`develop` as `75a42a8ce4ac459b25e49500cb6bbbf8567e312d`. This is
hosted source/test evidence, not physical S24 verification.**

**Release gate unchanged:** This is not proof that real Samsung Recents,
screen recording or a new in-place installed APK is secure. The latest
public rc20 predates these changes. No APK/publication, backend mutation,
or real finance data test is authorized by this checkpoint.

## 03e follow-up: same-owner authority reattachment and server snapshots

**Confirmed source-level gap after PR #509.** `FinanceProductViewModel.attachSession`
cancelled old bearer/AAL work but revoked visible `Ready` or `AuthRejected`
only for a *different owner*. A same-owner bearer rotation or assurance change
advanced `currentSession` before the asynchronous load, so the root's new
session match could temporarily reauthorize an old finance frame. Separately,
`MyFinHubRoot`'s in-memory account metadata/IBAN and history session keys
included owner and access bearer but omitted AAL, allowing an AAL-only
transition to reuse an earlier snapshot before its clearing effect.

**Correction on branch `android/d7-auth-transition-finance-metadata-boundary-20261122`:**
- Synchronously revoke the previous finance presentation and sync timestamp
  for every owner/bearer/AAL authorization transition, without discarding
  per-owner encrypted local snapshots or durable pending mutation intents.
- Bind account metadata/IBAN and finance-history snapshots to owner, bearer
  and AAL, preserving refresh-token-only changes as the same authority.
- Apply the exact session check to late server snapshot publication,
  IBAN write eligibility and subsequent response/reload publication.
- Fence delayed provider metadata, recurring-service assets, device-list
  and revocation callbacks by owner/bearer/AAL and online authority; cancel
  outstanding device-revocation controller work on authorization change.
- Add JVM regressions for owner, bearer, AAL, refresh-only, offline, and
  unauthenticated transitions, revoked finance presentation and delayed callbacks.

**Integration:** PR #510 final head `d2eb38398ca3397e27c899433f65b7c09c680809`
passed all six required exact-head checks (the first S24 shard passed on a
targeted rerun of an unrelated budget UI-test failure). It squash merged
to `develop` as `4ffdc13baf3141c4edf7de4a0a16222c714628de`.
No hosted CI result establishes physical S24 privacy acceptance.
Latest public rc20/10019 predates these corrections. The installed signer,
Play Protect, Recents/card recording, encrypted-cache continuity, finance
GET400 and missing historical credit-purchase linkage remain independent
release blockers. No new signed APK or backend changes are authorized.

## 03e follow-up: offline-ready card vault authority

**Confirmed source-level privacy defect after PR #510:** The root's card-vault
display authorization matched owner, bearer and AAL but did not check the
auth shell's `offline` flag. A successful AAL2 vault read could therefore
remain visible in `Ready(session, offline = true)` after failed server
revalidation with the *same* bearer. `CardSecretViewModel.attachSession`
similarly had no offline authority input and could continue allowing card
reads/writes or publish delayed responses without server revalidation.

**Correction on `android/d7-offline-card-vault-authority-20261122`:**
- Root hides card secrets and cleanup presentation synchronously for an
  offline-ready auth frame, even before its attachment effect runs.
- Card-secret controller accepts validated server-access authority separately
  from the owner/bearer/AAL identity. A same-session online-to-offline change
  revokes visible PAN/expiry/CVV and invalidates delayed reads/cleanup results.
- Offline card requests do not contact the server. Mutations remain blocked,
  caller credential buffers continue to be zeroed and no automatic secret
  re-reveal occurs on reconnect until an explicit card open.
- Owner transitions also clear hidden prior-owner card IDs, rather than only
  the visible PAN/CVV, and pending cleanup cannot cross the owner boundary.
- JVM and Android instrumented tests cover offline Ready masking, immediate
  redaction, blocked reads/writes, stale delayed server callbacks and
  old-owner card-pointer invalidation.

**Integration:** PR #511 final head `ca3fad7fb6069a921d85fd33fc202c44c9cc3591`
passed all six required exact-head checks and squash merged to `develop`
as `2fdac0fb6edd0cba5bc4bde8000c00f481375f16`. These are hosted
source/test results, not real Samsung screen-recording/Recents evidence,
signer/Play Protect or installed-data continuity. Public rc20/10019 predates
the changes; no new signed APK or backend mutation is authorized.

## 03e follow-up: server-confirmed card cleanup in a pre-attachment frame

**Confirmed source-level session race after PR #511.** The root
`LaunchedEffect(authState, financeState, cardSecretCleanupState)` checked
only whether the auth shell was online Ready at AAL2 before calling
`FinanceProductViewModel.serverConfirmedPendingSecretCleanupIds()` and
`CardSecretViewModel.purgeCard()`. Authentication changes can reach Ready
for a new owner/bearer *before* the attachment effect has replaced the
prior finance controller's server marker and the card-vault session.
The previous rendered-state masks do not guard this non-rendering action.
A stale card ID could therefore be selected for destructive vault cleanup.

**Source mitigation on `android/d7-vault-cleanup-session-guard-20261122`:**
- Gate automatic cleanup on **both** finance and card-vault ViewModels
  matching the current authenticated owner, bearer and AAL, with online
  AAL2 required and offline Ready rejected.
- Key the cleanup effect on both attachment authorization booleans to
  re-evaluate it after the controllers attach, without polling or
  suppressing legitimate confirmed cleanup.
- Independently reject explicit CardSecretViewModel purge initiation
  without online AAL2 authority.
- Add JVM transition cases and emulator-facing regressions proving
  downgraded/offline purge is inert and does not delete local vault values.

**Integration:** PR #512 exact head `f5592eb1f02e1f8cdfac365b3988ad1bab17a3cc`
passed all six required checks (Project Tracking, Android CI, screenshots,
both emulator shards and aggregate). Squash merged to `develop`
as `77e65e1944f7d78979d7a52645a4bd6ce6a75477`.
Physical S24 Recents/recording, encrypted-cache preservation, live GET and
historical purchase linkage remain unverified. D7 and D8 are blocked.

## 03e follow-up: network loss, finance cache provenance and revalidation

**Confirmed source gap after #512:** The controller's `networkStatus.drop(1)`
collector returned immediately on OFFLINE/UNKNOWN, leaving an existing
`FinanceProductState.Ready(offline = false)` visible after connectivity was
lost. On reconnection, no GET was scheduled when there were no pending
mutations, and during the next GET the cached `Ready` was re-labelled
`offline = false` before any server response. This could incorrectly
represent old balances as newly server-confirmed.

**Source correction on `android/d7-finance-cache-disclosure-revalidation-20261122`:**
- On disconnection, immediately mark a previously online finance presentation
  offline and preserve its projection and in-flight saving guard. Use the
  existing pending-count projection so pending work is disclosed rather than
  silently masked.
- Set `reloadWhenOnline` even without pending mutations, so reconnect runs
  a fresh read before claiming server authority.
- Keep previously cached/reconnected finance values labelled offline while
  the GET is still in flight; only an accepted response may restore online
  provenance. If connectivity drops during a successful read/write callback,
  leave the UI offline and keep a revalidation request queued.
- JVM truth-table tests distinguish validated online, offline, UNKNOWN and
  prior cached status. No new finance schema, store migration, backend write,
  release publication or secret permission changes.

**Integration:** PR #513 exact head `5dabe3fa994cf57d5c5bb6fcd76693595a4fd3d6`
passed all six required checks (Project Tracking, Android CI, screenshots,
both hosted S24 shards and the aggregate). Squash merged to `develop`
as `861259a2326b702f90463aeac6681430e6da4561`. No owner physical
S24, installed-data, GET400 or card-linkage issue is resolved by CI.

## 03e follow-up: auth-offline authority with network still connected

**Confirmed product privacy/provenance gap after PR #513.**
`AuthShellUiState.Ready(session, offline=true)` can occur after a failed
server-session revalidation while Android still reports a validated network.
If the same owner, bearer and AAL were previously online, the root's
`financeSessionAuthorized` still matched the prior attached token until
`LaunchedEffect(authState)`; and `FinanceProductViewModel.attachSession`
updated `automaticSyncAllowed=false` but kept its existing
`FinanceProductState.Ready(offline=false)`. The offline connectivity
collector added in PR #513 never runs in this case because connectivity
itself did not change. The screen could thus imply recently authenticated
online balances, while the auth shell had withdrawn server authority.

**Source correction on `android/d7-finance-auth-offline-authority-20261122`:**
- Finance presentation authority now includes the attached validated-online
  flag as well as exact owner/bearer/AAL identity. Root checks the expected
  flag from the auth shell before composing, masking the stale pre-attachment
  frame; refresh-token-only changes do not revoke the same authority.
- The attached online/offline flag is exposed via a separate reactive StateFlow,
  allowing Compose to re-evaluate the root gate even when the finance Ready
  state was already offline and would otherwise emit no new state on attach.
- On same-session online-to-offline validation failure, synchronously
  re-label the current projection as offline and preserve in-flight saving
  guard and encrypted pending mutations. Set `reloadWhenOnline` so the
  next validated attachment triggers a server read.
- Reconnected same-owner online attachment retains the offline presentation
  while the accepted GET is pending, as implemented by merged PR #513.
- JVM tests cover identical owner/bearer/AAL, refresh-only, changed bearer,
  owner and AAL, initial null attachment, and online/offline transition
  directions. No server schema, signing, installed data or permission changes.

**Integration:** PR #514 final head `2bdf6606ce9ac2ebbd6db20e84e202e0863acce3`
passed all six required exact-head checks (Project Tracking, Android CI,
screenshot regression, both S24 emulator shards and aggregate) and
squash merged to `develop` as `5621add63402fcd6189b680c03696abe92f79635`.
Physical S24, installed data, GET400 and historic card linkage remain
independent release blockers.

## 03e follow-up: server-scoped device and provider metadata after auth revalidation

**Confirmed source-level stale-state path after PR #514.**
The same-owner, same-bearer, same-AAL online→offline auth revalidation
transition updates `automaticSyncAllowed`, but previously retained
`DeviceSessionsUiSnapshot(loaded=true, revokingSessionId=...)`,
financial-provider catalog and recurring-service assets. The root masks them
while offline, but once validated online authentication resumes with the
unchanged bearer, `attachSession` may skip fresh metadata/device GET because
the old records are still nonempty/loaded. An interrupted other-device revoke
can therefore remain stuck in a permanent revoking state, even though the
server might already have processed the request.

**Source mitigation on `android/d7-session-metadata-revalidation-20261122`:**
- Treat changes between validated online and offline auth, in either
  direction, as a server-metadata authority transition even when owner,
  bearer and AAL are unchanged. Refresh-token-only rotation does not
  invalidate a still-validated session.
- On same-session online→offline revalidation failure, cancel metadata,
  recurring asset, device GET and revoke controller jobs; reset only the
  in-memory server-scoped snapshots. Retain encrypted finance cache and the
  durable pending money mutation queue.
- On verified online reattachment, empty/unloaded snapshots trigger fresh
  metadata/device GET, not an automatic retry of an ambiguous revoke POST.
- Give every auth authority change a monotonically increasing metadata epoch
  and check it (along with coroutine cancellation and exact owner/bearer/AAL)
  before publishing delayed server callbacks. This also fences ABA transitions.
- Add JVM cases for offline↔online, refresh-only, owner, bearer and AAL.
  No backend mutation or new permissions.

**Integration:** PR #515 final head `1f23d832e4ac63803b5e411a1c2e75c665cde5e7`
passed all six exact-head CI checks (tracking, Android CI, screenshot regression,
both S24 emulator shards, and aggregate) with no outstanding reviews or base drift.
Squash merged to `develop` as `f98a2ca058da63953a6891d0991abed803168ce2`.
Physical Galaxy S24, real GET400/credit linkage, installed signer/Play Protect,
Recents/capture and in-place encrypted update remain unverified release blockers.

## 03e follow-up: revoked card-vault display on physical network loss

**Confirmed source privacy gap after PR #515:** `AuthShellViewModel`
reacts to online reconnection for an *offline Ready* session, but does not
turn an existing `Ready(session, offline=false)` into offline on loss of
Android connectivity. The root's card-secret presentation and
`CardSecretViewModel.attachSession(allowServerAccess = !state.offline)`
were keyed only to the auth shell. A previously revealed PAN/expiry/CVV could
therefore remain visible when network becomes OFFLINE/UNKNOWN while auth
stays Ready; the card-secret ViewModel also retained an authorization for
more remote reads. This is a source-level finding, not confirmed S24
recording evidence.

**Source mitigation on `android/d7-card-vault-network-revocation-20261122`:**
- Require **both** non-offline authenticated Ready *and* validated
  `NetworkStatus.ONLINE` before exposing any observed card-secret state or
  vault cleanup to Compose. OFFLINE and UNKNOWN hide them immediately in
  the next recomposition, before attachment effects execute.
- Independently bind the card-secret controller attachment to
  `LaunchedEffect(authState, networkStatus)`, revoking revealed secrets,
  in-flight read/write generations and cleanup authority on lost or
  unvalidated connectivity even when the owner/bearer/AAL is unchanged.
  The finance cache and its offline pending queue stay unaffected.
- On reconnect, retain `Hidden` and do not automatically re-fetch PAN/CVV.
  A current authenticated user must explicitly open the card again.
- JVM tests cover online/offline/unknown versus auth-online/offline/logged-out;
  hosted Android instrumentation covers real `CardSecretViewModel`
  reveal → transport-loss redaction → blocked reveal → reconnect requiring
  an explicit reopen. These are **authored tests, not passed CI yet**.

**Integration:** PR #516 immutable head `01b11db5f2437e1ed505b021b26302618aef59fd`
passed all six required exact-head CI gates (tracking, Android CI, screenshots,
both S24 emulator shards, and aggregate) with no unresolved reviews or
base drift. Squash merged to `develop` as
`89addfc1172ef3bc2da942961d7942ac46f64874`. Physical privacy
and live backend acceptance are still outstanding.

## 03e follow-up: secure-card details screen after network reconnect

**Source-level user journey regression after PR #516.** Card-secret protection
correctly changes an already-revealed card to `CardSecretUiState.Hidden`
when validated connectivity disappears. Reconnection does not auto-reveal,
by design. However, `CanonicalCardSecureDetailsScreen` rendered its
`Hidden` branch as an indefinite **"Φόρτωση στοιχείων κάρτας…"**
spinner; its initial `LaunchedEffect(cardId, cardAvailable)` does not
re-run merely because connectivity or controller authorization recovered.
A user staying on the card's details route had no functional action to
request the required new read.

**Source correction on `android/d7-card-vault-hidden-recovery-20261122`:**
- Pass the root's already fail-closed `vaultSessionAuthorized` through
  `FinanceProductSurface` and `MyFinHubAppContent` to the secure card
  screen, without creating a new authority policy or exposing PAN/CVV.
- A `Hidden` card displays a precise "confirmed online session required"
  message while authority is unavailable. Once authorized, it shows a
  visible **"Προβολή στοιχείων"** action to explicitly request the vault
  read. Only an actual `Loading` state shows a progress spinner.
- Preserve the automatic one-time initial card-open behavior for already
  authorized entry, but do not trigger a read solely on network reconnection.
  The user controls reopening after a disconnected/unknown session.
- Add hosted Compose regression: offline Hidden -> online Hidden without
  automatic read -> explicit action -> offline once more with no retry
  button, and no fake loading spinner. No changes to persisted finance,
  backend, signing, permissions, or card-vault API.

**Integration:** PR #517 immutable head `eae54b8eccf715c7eb75452dacdc1eaceacd3c0f`
passed all six required exact-head CI gates (Project Tracking, Android CI,
screenshot regression, hosted S24 shards 0 and 1 and aggregate), without
outstanding reviews or base drift. Squash merged to `develop` as
`5a0ca1e72de9c2f17702c65d69b8beb47284257f`. Physical Galaxy S24
privacy/capture, in-place encrypted data and signer/Play Protect, real GET400
and two historical credit-purchase links remain unresolved release blockers.

## 03e follow-up: duplicate card-vault GET on secure-details entry

**Confirmed source-level duplicate remote secret read after PR #517.**
The production `MyFinHubAppContent` card-secure route uses
`DisposableEffect(route.cardId, hasUniqueCard)` to invoke
`onCardDetailOpened(route.cardId)`, which maps to
`CardSecretViewModel.openCard` and starts the initial authorized server
PAN/CVV read. Independently, `CanonicalCardSecureDetailsScreen` had its
own `LaunchedEffect(cardId, cardAvailable)` calling `onReveal` for the
same card as soon as it was composed. That second callback maps to
`CardSecretViewModel.reveal` and starts a second remote vault GET for
the same navigation event, invalidating the first request generation.
It also risks a new unsolicited read if a retained details route is
recomposed after session authorization changes.

**Correction on `android/d7-card-vault-single-open-read-20261122`:**
- Keep the production route's existing `onCardDetailOpened` as the
  *single* initial read owner and remove the child screen's automatic
  duplicate `onReveal` launch. Preserve all controller owner/AAL2,
  bearer, network and card identity checks; `openCard` already ignores
  a duplicate open for the same Revealed card.
- Maintain the explicit `Προβολή στοιχείων` button for authorized
  Hidden state after network restoration and the explicit failure retry.
  No re-reveal on connection recovery.
- Host instrumented Compose regression verifies mounting the secure
  details screen cannot trigger a second `onReveal` callback, including
  when secret values already appear; pressing the button does trigger
  exactly one. No new write, permissions, schema or signed APK.

**Integration:** PR #518 final immutable head `ac982db9e495531177840d60251f7acebe7be9cb`
passed all six required exact-head CI gates: Project Tracking, Android CI,
screenshot regression, both S24 emulator shards and instrumentation aggregate.
The initial S24 shard 1 run failed during Gradle resolution because Maven
Central returned HTTP 429, *before tests executed*. A targeted rerun of the
failed shard **at the same PR head** passed; no source code change was needed.
Reviews and base were clean. PR #518 squash merged to `develop` as
`6c6d94c177ebf6d609f3e106a9b81d243b72ff8c`.

**D7 handoff — no new source correction pending.** Native D7 source-reviewed
domains remain 7/8; release-cleared domains remain 0/8; deploy remains 6/8;
Web parity W6.4 stays 23/24. The remaining domain is owner physical acceptance,
not more emulator CI. Real Galaxy S24 Ultra requires signer/Play Protect,
same-install encrypted data continuity, Recents and screen-recording privacy
(PAN/CVV and navigation/dialog transitions). The live finance
`GET /api/data` HTTP 400 `INVALID_DATA` and the two November 12 unlinked
credit-purchase rows need separate read-only forensic evidence and an explicitly
approved production correction. Public signed rc20/10019 predates the current
merged source and does not validate it. No signed candidate, destructive
in-place installation, live financial write or D8 release is authorized.

## 2026-11-22 read-only live finance forensic checkpoint (after merged #519)

**Evidence class: authenticated Supabase project metadata/SQL inspection, not
an owner-authenticated HTTP GET or a physical Android run.** The linked
Supabase project reported ACTIVE_HEALTHY. All queries were SELECT-only and
returned *aggregate metadata*; no transaction payload, identity, PIN,
credentials, financial amounts or secret card values were exported.

The current stored finance state has revision **499**, relational_v1 mode,
and latest app_state update **2026-11-19 19:16:31 UTC**. The deployed database
function `public.rheomiq_read_state` calls owner/AAL access checking and then
`private.rheomiq_effective_data`; that stable composition function rebuilds
cards/statements/events from private normalized tables. Direct read-only SQL
composed one schema-v3 `RheomIQ` JSON document of **275,926 UTF-8 bytes**,
with **509 events, 6 active card records, 0 deleted-card records and
13 credit statements**. The read-only direct SQL composition is **not** a
proof that the owner-AAL2 `/api/data` REST route succeeds.

Focused consistency queries against all **509** relational event headers and
their linked legs found: **0** header-kind / JSON kind mismatches, **0**
event-date / JSON date mismatches, **0** non-positive header amounts,
**0** invalid leg account/zero leg values, **0** unexpected leg cardinalities,
and **0** card-purchase / card-payment sign-sum or card-ID anomalies under
their specific Web semantics. These are *selected structural checks*, not a
full `validateCompleteFinanceData` replay or general ledger certification.

**Credit-specific evidence:** the current private ledger has **10**
`card_purchase` records and **9** `card_payment` records, each kind's
latest recorded event date **2026-11-18**. Exactly **two** rows have event
date **2026-11-12**. Both are `expense` and neither has cardId,
statementId, a `funding` field or a nonzero creditDelta. They cannot be
counted as confirmed credit-card purchases. Do not infer these must be the
owner's two expected transactions, or automatically migrate/link them.

**Boundaries and next evidence:** The historical owner-captured
`GET /api/data` 400 `INVALID_DATA` has not been reproduced or disproven;
the original Vercel runtime request log has expired. `server/storage.ts`
shows the response may be mapped from an upstream invalid-data RPC or
thrown by server-side structural/domain validation after the data read.
Neither failure location can be established from the present SQL aggregates.
A fresh *owner-authorized* real request with sanitized status/request ID and
matched runtime error diagnostics is required. No backend data mutation,
reclassification, destructive recovery, public release, or signed APK is
authorized by this checkpoint. Physical Galaxy S24 Ultra W6.4 and D7/D8
acceptance still block release.

## 2026-11-23 D7 source correction: card-vault presentation on navigation

**Finding (P1, source-backed):** the Cards wallet's `CreditCardStack`
deliberately calls `onCardDetailOpened` when its active card changes and
renders returned PAN/expiry/CVV on the selected card face under a sensitive
window lease. On leaving that wallet face for another primary tab or a
non-vault Money route, the existing `onHideCardSecrets` callback was not
invoked anywhere by `MyFinHubAppContent`; in-memory `Revealed` could
therefore remain attached to the controller while that face was absent.
Secure card details already had an independent open/close disposal boundary.
This finding concerns **stale loaded-secret lifetime**, not a claim of
successful screen capture or of unauthorized data access.

**Proposed mitigation on `android/d7-card-secret-route-boundary-20261123`:**
limit retained card-secret presentation to `TopLevelDestination.MONEY` with
either `AppRoute.Money` (selected card face) or
`AppRoute.CardSecureDetails` (secure-details owner). All other routes and
tabs synchronously mask the secret UI value, while the existing wired
`CardSecretViewModel.hideSecrets()` callback revokes `Revealed` or
`Loading` and increments the request generation to fence delayed vault
GET results. A new `Loading`/`Revealed` state created by a token refresh
while the same non-vault route remains selected is also immediately revoked.
The root sensitive-window transition lease continues to protect outgoing
Nav3 frames. Card-face behavior and secure-detail manual retry stay unchanged.
A JVM policy test covers allowed/forbidden routes; hosted Compose navigation
regression covers tab departure, a late same-route secret and an in-flight
read on a second departure.

**Source integration verified 2026-11-23:** PR #521 final exact head
`bd39ee00e17bf93274dadd5a4d0cfa048adee00b` passed Project Tracking,
Android CI, screenshot regression, hosted S24 emulator shards 0/1 and the
fail-closed S24 instrumentation aggregate (**6/6**); mergeable, base current
and no review threads. Squash merged into `develop` as
`8336deb1891e183ccbdbb1716cb18624934dd7dc`. Two earlier PR-head
test failures were corrected before final-head validation: Compose nav
selector semantics required the unmerged-tree parent; the subsequent test
edit had an invalid `onNode` import. The integrated production change
remains confined to card-secret presentation and in-flight read revocation.

**Not an owner/device clearance:** no new backend access, permissions,
finance write, public APK or signer change. Real Galaxy S24 Ultra
Recents/screen recording, same-signer in-place encrypted-cache continuity,
Play Protect, PIN/biometric and ordinary finance flows remain unverified.
Separately, historical owner HTTP 400 `GET /api/data` and two Nov 12
credit-purchase associations remain unresolved. Latest signed rc20/10019
predates the merged source. D7 stays **6/8**, W6.4 **23/24**, source
audit **7/8** reviewed, **0/8** release-cleared.

## 2026-11-23 grouped PAN editor follow-up (source-only)

Web v1.7 Card Vault strict new-write validation already existed in Android rc20; it is not replaced here. On branch `android/d7-card-pan-grouped-entry-20261123`, new-card PAN entry is visually grouped 4×4 while masked with password accessibility semantics, and protected secure-details PAN edit groups visible digits without mutating the raw backing input. Preserved legacy read lengths up to 32 digits remain visible only inside the protected details editor; writing still requires exactly 16 digits. Values longer than 16 cannot silently be truncated into valid writes. JVM regressions cover obfuscation, historical presentation and selection/cursor offset mapping. No backend/schema, live finance, release signer, permissions, APK or owner physical acceptance changes. **Integration 2026-11-23:** PR #523 final exact head `359b063c23117c7e69af1eaa281926614402e535` passed all six required checks: Project Tracking, Android CI, screenshot regression, S24 hosted shards 0/1 and instrumentation aggregate. First head failed three Card Create screenshot comparisons due to an unrelated explanatory-copy edit; the copy was reverted in the final head and the screenshot gate passed. Reviews were clean, base current. Squash merged to `develop` as `169efdcdc551098a93597b0e898aff69fb66294c`. Post-merge Project Tracking passed; Android CI was still in progress when this checkpoint was recorded. This does not constitute S24 owner physical acceptance, new signed APK, real finance GET success or D8 release clearance.
