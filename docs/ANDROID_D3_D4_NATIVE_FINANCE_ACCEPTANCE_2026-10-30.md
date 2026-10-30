# Android mobile finance journeys: D3 and D4 integration checkpoint (2026-10-30)

This checkpoint validates actual Compose top-level navigation instead of introducing another Web-parity-only tweak. Existing main implementation of Cards, Quick Entry, Activity and offline finance mutation pipeline is kept unchanged.

## Acceptance evidence
- D3 native Wallet cards → selected payable canonical credit statement → actual CARD_PAYMENT Quick Entry with exact card ID, statement ID and remaining debt; tests also require existing credit option and global source-identity verification. Card source-active/archived/kind validation merged #409; card statement identity and filter protections merged #405/#406. Existing hosted Quick Entry selector tests cover invalid and duplicate statement options, and Kotlin canonical mutation/replay tests preserve finance writes.
- D4 native Activity ledger → unique transaction detail → separate editor/back → delete confirmation, cancel/no side effect, explicit confirmed source-ID delete callback; all via real App navigation. Existing Activity visual, historic-edit, ledger-filter and offline mutation tests remain in place; #408 handled malformed money and #410 guarded source validity, permission changes and confirmation lifecycle.

## Milestones
On successful **immutable-head four-gate CI and PR integration**, D3 Cards/Credit/Quick Entry and D4 Activity complete their **mobile software integration** acceptance. This does not claim that the device owner tested the new build: D7 remains physically unapproved. D5 native Plan/savings/loans/Insights and D6 settings/auth/offline/backup become the next work; D8 production signed deployment still requires owner release approval. Historical W1–W6 Web accounting remains separate, not a mandate to copy desktop layouts.

Only test-state reducers are used in this checkpoint; no live financial writes, backend or schema changes, private secrets, SMS #146 or global shared PIN #345. One complete final-head CI suite, not multiple intermediate builds.
