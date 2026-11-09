# Android screenshots — validated Compose visual references

This directory gives stable, human-readable paths for **synthetic, rendered
production Compose UI fixtures**. It is **not** a capture of the owner's
physical Samsung Galaxy S24 Ultra or a claim of real-device acceptance.

The authoritative reference PNGs are in
`app/src/screenshotTestDebug/reference/`. The 29 mirrored files
listed in `scripts/verify_visual_reference_mirrors.py` are **byte-identical
copies** of those official screenshots, rather than independent renderings.
These cover Activity, authentication, Home, cards/money, Plan, Quick Entry,
Settings and Reports in selected light, dark and large-type states.

For Android UI changes:

1. Review the actual rendered Compose candidate and its accessibility/privacy
   semantics; only update official reference PNGs after visually validating
   the changed screen.
2. Update the `MIRRORS` source path if the official test name/hash changed;
   then run `python3 scripts/verify_visual_reference_mirrors.py --sync`.
3. Run `python3 scripts/verify_visual_reference_mirrors.py` to catch stale
   public copies. Project Tracking tests enforce the same byte-level check.
4. Preserve the stable public file names. Do **not** replace them with mockups,
   generated design art, real account secrets, or unreviewed device screenshots.

**Historical exception:** `utilities/change-history.png` is an older,
privacy-safe server-history illustration without an equivalent committed
official Compose golden. It is **not** in the current validated mirror set
and must not be cited as proof that the current release matches that image.

Hosted tests validate the committed code/rendering, but **D7 physical S24
owner acceptance and D8 protected release remain independent gates**.
