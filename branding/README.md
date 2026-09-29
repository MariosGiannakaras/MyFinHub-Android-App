# MyFinHub Android branding

The Android client uses the owner-approved `MyFinHub_Brand_Kit_PureVector.zip` identity reviewed on 2026-09-29.

## Source audit

The canonical source SVGs are true self-contained vectors made from paths/shapes, gradients and SVG filters. They contain no embedded raster images, external image references, font dependencies or scripts. The source kit's horizontal light/dark lockups are byte-identical, and the files labelled vertical contain the same horizontal artwork, so Android does not keep duplicate theme/vertical copies.

Exact source vectors needed to regenerate Android branding are retained under `branding/source/`: `myfinhub-symbol.svg` and the single non-duplicate `myfinhub-logo-horizontal.svg`. The complete precomposed light/dark app-tile SVGs are intentionally not used as Android adaptive-icon foregrounds.

## Runtime asset contract

Android does not consume web SVG files directly. Platform resources are derived from the same PureVector masters:

- `myfinhub_launcher_foreground.png`: 1080×1080 transparent adaptive-icon foreground with the standalone symbol sized inside the Android safe zone. It deliberately does not contain a pre-rounded app tile or background.
- `@color/ic_launcher_background`: full-bleed light/dark adaptive-icon background (`#F6F8FB` / `#171B24`).
- `ic_launcher_monochrome.xml`: dedicated single-color MF monogram used by Android 13+ themed icons.
- `myfinhub_symbol.png`: compact 512×512 in-app symbol derivative.
- `myfinhub_lockup.png`: one optimized horizontal in-app lockup; separate light/dark copies would be identical.
- `branding/google-play-icon.png`: 512×512 full-square store-listing artwork. It is intentionally outside `res/` so it does not inflate the APK and does not pre-bake a rounded launcher mask.

The manifest continues to reference adaptive `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`. Since minSdk is 26, legacy pre-adaptive density-specific launcher resources are not required.

No notification small icon is added because the current application has no notification builder/status-bar notification call site. Provider/bank marks are unrelated product assets and remain unchanged.

No signing identity, package identity, finance behavior, authentication flow, card/CVV behavior or release channel is changed by this branding work.
