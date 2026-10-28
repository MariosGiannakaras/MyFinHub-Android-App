# Web v1.7 Card Vault native strict-write compatibility — 28 October 2026

Source of truth: MyFinHub Web develop \`server/cardVaultCrypto.ts\` and issue #637. Web v1.7 is NOT production (main is v1.6). The native implementation aligns **new PUT writes only** to server acceptance:

- PAN: exactly 16 ASCII digits after removing formatting; no bank-network-specific extra rules or silent truncation.
- CVV: exactly 3 ASCII digits when supplied; CVV-only updates are allowed; 4-digit legacy CVV is READABLE but never rewritten.
- Expiry: valid month from current **UTC month** through year 2099, accepts MM/YY and MM/YYYY (or server-allowed hyphen separator), canonical outbound MM/YY. Current month is valid.
- Never accept an all-empty update and never leak full PAN/CVV or user PIN in logs/diagnostics.
- Guard both Compose create/editor and CardSecretViewModel API write boundary, including direct/internal callers. Existing owner+AAL2, revision/auth, card-identity and encrypted vault session controls remain untouched.
- Legacy encrypted read/decrypt remains permissive. Before any one-time migration PUT, ensure **every missing source field** is safe to write. If one is incompatible (expired expiry, 4-digit CVV, non-16 PAN), skip the whole attempt, preserve every local encrypted secret, display source values under existing owner+AAL2 protections and offer a generic non-secret notice. Incompatible migration != data loss; do not claim it synchronized.
- Unit vectors cover UTC month boundaries, four-year expiry forms, invalid format, strict PAN/CVV and mixed partial write. Hosted Android ViewModel tests cover zeroized caller buffers, denied bad writes, legacy preservation and valid writes. Compose create UI fails closed instead of creating unsynchronizable profile.
- Excludes Web release, Supabase migrations, production writes, global shared PIN implementation issue #345 and physical S24 acceptance #149.

CI evidence and exact immutable head belong in PR #393 and canonical tracking. Green Android gates are **client source evidence**, not full Web v1.7 production readiness, because shared PIN bearer ticket and disaster recovery are separate gates.
