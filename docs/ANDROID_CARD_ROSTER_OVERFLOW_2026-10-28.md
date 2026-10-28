# Mixed card stack: many-card indicator and gesture safety (28 October 2026)

Owner contract: one combined debit+credit+virtual stack, vertical swipe on the **card** (no arrows), inline credit history below. When there are many cards, the 48dp accessible pagination targets no longer fit a narrow S24 screen.

- For a roster that fits, preserve existing tiny centered 7dp/22dp indicator visuals and 48dp hitboxes.
- For overflow, allow **horizontal scrolling of pagination indicators only**, never change the vertical card swipe behavior. Automatically bring the currently selected indicator to the center of the available viewport (clamping at edges), including after card swipe or direct dot selection.
- Lock the card motion during programmatic dot transitions to prevent rapid taps and drag interactions from reordering a stale stack. Clear stale copy-status announcement when a different card becomes active, while the established sensitive clipboard lifecycle continues to revoke secrets.
- Tests cover 12 mixed cards, far-side dot selection, pure pixel centering/clamp/overflow math, and preservation of existing three-card UI. Do not alter canonical financial data, Web/backend/schema or release/signing.
