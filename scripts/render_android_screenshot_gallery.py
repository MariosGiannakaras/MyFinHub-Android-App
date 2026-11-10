#!/usr/bin/env python3
"""Build an offline, human-readable gallery from official synthetic Compose PNGs.

This helper NEVER generates or substitutes screenshots: it indexes files already
rendered by the Android Compose Preview Screenshot Test task. Only CI test
fixtures may be placed in the input directory; never pass personal device data.
"""
from __future__ import annotations

import argparse
import html
import os
from pathlib import Path
from urllib.parse import quote


SCREEN_LABELS = {
    "app": "Εφαρμογή",
    "auth": "Σύνδεση και κλείδωμα",
    "home": "Αρχική",
    "activity": "Κινήσεις",
    "money": "Κάρτες και λογαριασμοί",
    "quickentry": "Νέα κίνηση",
    "quick_entry": "Νέα κίνηση",
    "utilities": "Ρυθμίσεις Android",
    "plan": "Πλάνο (παλαιότερα previews)",
    "insights": "Αναφορές (παλαιότερα previews)",
}
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def screen_group(relative_path: Path) -> str:
    components = [s.lower() for s in relative_path.parts]
    for component in components:
        for token, label in SCREEN_LABELS.items():
            if token in component:
                return label
    return "Λοιπά previews"


def safe_url(value: str) -> str:
    # Keep directory separators, escape spaces and special URL characters.
    return "/".join(quote(part, safe="") for part in value.replace(os.sep, "/").split("/"))


def render_gallery(source: Path, destination: Path) -> int:
    source = source.resolve()
    destination = destination.resolve()
    if not source.is_dir():
        raise ValueError(f"Screenshot source directory is missing: {source}")
    if destination.is_relative_to(source):
        raise ValueError("Gallery output must not be inside the screenshot references")

    screenshots = []
    for candidate in sorted(source.rglob("*.png")):
        if candidate.is_symlink() or not candidate.is_file():
            continue
        if not candidate.resolve().is_relative_to(source):
            continue
        with candidate.open("rb") as handle:
            if handle.read(8) != PNG_SIGNATURE:
                raise ValueError(f"Not a PNG screenshot: {candidate}")
        screenshots.append(candidate)

    if not screenshots:
        raise ValueError(f"No official PNG screenshots found in {source}")

    destination.parent.mkdir(parents=True, exist_ok=True)
    by_group: dict[str, list[tuple[str, str, str]]] = {}
    for screenshot in screenshots:
        relative = screenshot.relative_to(source)
        target_path = os.path.relpath(screenshot, destination.parent)
        target_url = safe_url(target_path)
        title = relative.stem.replace("_", " ").replace("-", " ")
        group = screen_group(relative)
        by_group.setdefault(group, []).append((title, target_url, str(relative)))

    sections = []
    for group, images in sorted(by_group.items()):
        cards = []
        for title, url, path in images:
            cards.append(
                '<figure class="capture">'
                f'<a href="{html.escape(url, quote=True)}" target="_blank" rel="noopener">'
                f'<img loading="lazy" src="{html.escape(url, quote=True)}" '
                f'alt="{html.escape(title, quote=True)}"></a>'
                f'<figcaption><strong>{html.escape(title)}</strong>'
                f'<small>{html.escape(path)}</small></figcaption></figure>'
            )
        sections.append(
            f'<section><h2>{html.escape(group)} · {len(images)}</h2>'
            '<div class="screens">' + "\n".join(cards) + '</div></section>'
        )
    page = f"""<!doctype html>
<html lang="el">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MyFinHub Android — Compose Screenshots</title>
<style>
:root {{ color-scheme: light dark; font-family: system-ui, sans-serif; }}
body {{ margin:0 auto; padding:1.25rem; max-width:110rem; line-height:1.45; }}
h1 {{ margin-bottom:.25rem; }} p {{ max-width:75ch; }}
section {{ margin-block:2rem; }}
.screens {{ display:grid; grid-template-columns:repeat(auto-fill,minmax(min(100%,17rem),1fr)); gap:1.2rem; align-items:start; }}
.capture {{ min-width:0; margin:0; overflow:hidden; border:1px solid #85858555; border-radius:12px; }}
.capture img {{ width:100%; height:auto; display:block; background:#f4f5f8; }}
.capture a:focus-visible {{ outline:3px solid #3565de; outline-offset:-3px; }}
figcaption {{ padding:.7rem; overflow-wrap:anywhere; }}
figcaption strong, figcaption small {{ display:block; }}
figcaption small {{ opacity:.7; font-size:.8rem; }}
</style>
</head>
<body>
<header><h1>MyFinHub Android — Screenshots</h1>
<p><strong>{len(screenshots)} πραγματικά PNG από Compose screenshot tests.</strong>
Συνθετικά δεδομένα, όχι πραγματικά οικονομικά στοιχεία ή φωτογραφίες από το S24 Ultra.
Οι εικόνες είναι της συγκεκριμένης εκτέλεσης CI. Άνοιξε κάθε εικόνα για πλήρες μέγεθος.
Παλαιότερα previews παραμένουν χωριστά και δεν αποτελούν ενεργή πλοήγηση Android.</p></header>
{''.join(sections)}
</body>
</html>
"""
    destination.write_text(page, encoding="utf-8")
    return len(screenshots)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source", type=Path,
        default=Path("app/src/screenshotTestDebug/reference"),
    )
    parser.add_argument(
        "--output", type=Path,
        default=Path("app/build/reports/screenshotTest/gallery/index.html"),
    )
    args = parser.parse_args()
    total = render_gallery(args.source, args.output)
    print(f"Gallery indexed {total} real Compose screenshots: {args.output}")


if __name__ == "__main__":
    main()
