# MyFinHub credit-card stack reference

Issue: #24

Canonical source artifact supplied directly by the product owner:

`myfinhub_credit_card_stack_production_final.html`

SHA-256:

`4d281887a4083d36c6b454fa2438b792e3c545248991a22c6bdfdefa736f9b87`

The HTML artifact is the sole visual and interaction source of truth for the Android card-stack implementation. Do not substitute the existing Android or web card UI as a design reference.

Required preserved contract includes the 1.586:1 card geometry, four-layer 3D stack, stable ID ordering, vertical swipe/restack, non-touch pointer tilt, reveal/hide, copy, slide-to-delete at >=90%, flash/collapse plus multi-slice shred deletion, final-card deletion/empty-stack support, pagination dots, scoped keyboard navigation, accessibility announcements/focus treatment and reduced-motion behavior.

The source artifact was supplied directly in the implementation session for issue #24. This file records its exact identity so future validation can detect a changed reference rather than silently comparing against a different artifact.
