#!/usr/bin/env python3
"""Build a user-journey review pack from real synthetic Compose PNG fixtures.

Does not render screenshots or establish functional/device acceptance.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
from pathlib import Path
from urllib.parse import quote

PNG = b"\x89PNG\r\n\x1a\n"
# (id, label, entry route, question). Canonical roots from ADR-0002.
JOURNEYS = (
    ("home", "Αρχική", "Tab Αρχική", "Balances, three account cards, privacy, next useful action."),
    ("activity", "Κινήσεις", "Tab Κινήσεις → filters/detail/edit → Back", "Selection, search, undo, preservation of context."),
    ("quick-entry", "Νέα κίνηση", "Αρχική/Κινήσεις/Κάρτες → Νέα κίνηση", "Expense/income/transfer/card entry, valid save/cancel/return."),
    ("cards", "Κάρτες", "Tab Κάρτες → selected card", "Does card selection and purchase/payment entry work without duplicate actions?"),
    ("card-detail", "Λεπτομέρειες κάρτας", "Κάρτες → επιλεγμένη κάρτα → Back", "Is Αλλαγή κάρτας redundant here? Is return to original card preserved?"),
    ("card-secure", "Ασφαλή στοιχεία κάρτας", "Λεπτομέρειες κάρτας → Στοιχεία κάρτας", "Masking and security. PNGs DO NOT prove FLAG_SECURE."),
    ("card-create", "Δημιουργία κάρτας", "Κάρτες → νέα κάρτα", "Avoid unnecessary setup and ensure safe cancel."),
    ("account-detail", "Στοιχεία λογαριασμού", "Αρχική → λογαριασμός", "Relevant account actions and consistent source identity."),
    ("settings", "Ρυθμίσεις Android", "Αρχική → Ρυθμίσεις", "Visibility, local security, updates, sign-out, no Web admin."),
    ("auth", "Είσοδος / PIN", "Launch or restore locked session", "Recovery and privacy before successful unlock."),
    ("secondary", "Δευτερεύουσες διαδρομές", "Contextual route", "Is this task necessary on Android; does Back preserve context?"),
    ("legacy", "Παλαιότερες test-only οθόνες", "Internal preview only", "Never imply Plan/Insights/admin are normal Android tabs."),
)


def surface(path: Path) -> str:
    p, n = path.as_posix().lower(), path.name.lower()
    if any(x in p for x in ("/feature/plan/", "/feature/insights/")) or any(
        x in n for x in ("s7plan", "s9utilities", "canonicalmoney", "canonical_money")
    ):
        return "legacy"
    if "/feature/auth/" in p:
        return "auth"
    for text, tag in (
        ("cardsecure", "card-secure"), ("card_secure", "card-secure"),
        ("carddetail", "card-detail"), ("card_detail", "card-detail"),
        ("cardcreate", "card-create"), ("card_create", "card-create"),
        ("accountdetail", "account-detail"), ("account_detail", "account-detail"),
    ):
        if text in n:
            return tag
    for path_fragment, tag in (
        ("/feature/quickentry/", "quick-entry"), ("/feature/activity/", "activity"),
        ("/feature/home/", "home"), ("/feature/utilities/", "settings"),
    ):
        if path_fragment in p:
            return tag
    if "/feature/money/" in p and any(x in n for x in (
        "cardstack", "card_stack", "walletcards", "wallet_cards",
        "creditworkspace", "credit_workspace", "carddeletedialog",
    )):
        return "cards"
    return "secondary"


def variant(name: str) -> str:
    s = name.lower()
    if any(x in s for x in ("largefont", "large_font", "largetype", "large_type", "150_percent", "font_150", "_large_", "s7planlarge", "s7debtslarge")):
        return "large-font"
    if "dark" in s:
        return "dark"
    if "light" in s:
        return "light"
    return "state-specific"


def render(source: Path, target: Path, *, source_sha: str = "unknown", run_id: str = "local", validation: str = "unknown") -> dict:
    if validation not in {"success", "failure", "unknown"}:
        raise ValueError("Invalid validation status")
    source, target = source.resolve(), target.resolve()
    if not source.is_dir() or target.is_relative_to(source):
        raise ValueError("Missing reference screenshots or unsafe output location")
    found = []
    for file in sorted(source.rglob("*.png")):
        if file.is_symlink() or not file.is_file() or not file.resolve().is_relative_to(source):
            continue
        data = file.read_bytes()
        if not data.startswith(PNG):
            raise ValueError(f"Invalid official PNG: {file}")
        rel = file.relative_to(source)
        url = "/".join(quote(x, safe="") for x in os.path.relpath(file, target.parent).replace(os.sep, "/").split("/"))
        found.append({
            "file": rel.as_posix(), "url": url, "sha256": hashlib.sha256(data).hexdigest(),
            "surface": surface(rel), "variant": variant(rel.name), "visual_fixture_only": True,
        })
    if not found:
        raise ValueError("No official PNG screenshots")
    sections, cards, checklist, inventory = [], [], [
        "# MyFinHub Android — evidence-based UX review", "",
        f"Source SHA: {source_sha} | run: {run_id} | screenshot test: {validation}", "",
        "Visual fixtures do not prove clicks, data correctness, security, or owner physical device acceptance.", "",
        "For every visible UI element inspect intent → entry → action → state → Back.",
        "Choose keep / move / merge / remove based on actual code and interaction tests.", "",
    ], []
    for ident, title, entry, question in JOURNEYS:
        images = [x for x in found if x["surface"] == ident]
        expected = {"light", "dark", "large-font"} if ident not in {"legacy", "secondary", "auth"} else set()
        missing = sorted(expected - {x["variant"] for x in images})
        inventory.append({
            "id": ident, "title": title, "entry": entry, "review_question": question,
            "count": len(images), "missing_baseline_variants": missing,
            "requires_behavioral_tests": True, "images": images,
        })
        cards.append(f'<a href="#{ident}">{html.escape(title)} ({len(images)})</a>')
        figs = []
        for item in images:
            u = html.escape(item["url"], quote=True)
            figs.append(f'<figure><a href="{u}" target="_blank" rel="noopener">'
                        f'<img loading="lazy" src="{u}" alt="{html.escape(item["file"], quote=True)}"></a>'
                        f'<figcaption><b>{html.escape(item["variant"])}</b><small>{html.escape(item["file"])}</small></figcaption></figure>')
        sections.append(f'<section id="{ident}"><h2>{html.escape(title)}</h2>'
                        f'<p><b>Entry:</b> {html.escape(entry)}</p>'
                        f'<p><b>Review:</b> {html.escape(question)}</p>'
                        f'<p><b>Missing baseline variants:</b> {html.escape(", ".join(missing) or "none")}</p>'
                        '<div class="grid">' + "".join(figs) + '</div></section>')
        checklist.extend([
            f"## {title}", f"- Entry: {entry}", f"- Review: {question}",
            f"- Screenshots: {len(images)}; missing baseline: {', '.join(missing) or 'none'}.",
            "- [ ] Check label, placement, necessity and duplication of EVERY control.",
            "- [ ] Execute the entire route, Save/Cancel, result and Back in tests.",
            "- [ ] Check empty/error/offline/privacy/large font and account/card identity.",
            "- [ ] Record evidence-based decision and regression coverage.", "",
        ])
    result = {
        "schema_version": 1, "source_sha": source_sha, "run_id": run_id,
        "validation": validation, "screenshot_count": len(found),
        "origin": "official synthetic Compose screenshot references",
        "device_acceptance": "not_established", "journeys": inventory,
    }
    target.parent.mkdir(parents=True, exist_ok=True)
    (target.parent / "review-index.json").write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (target.parent / "review-checklist.md").write_text("\n".join(checklist), encoding="utf-8")
    page = ('<!doctype html><html lang="el"><head><meta charset="utf-8">'
            '<meta name="viewport" content="width=device-width,initial-scale=1"><title>MyFinHub UX Review</title>'
            '<style>:root{color-scheme:light dark;font-family:system-ui,sans-serif}'
            'body{max-width:100rem;margin:auto;padding:1rem;line-height:1.5}'
            '.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(min(100%,17rem),1fr));gap:1rem}'
            'figure{margin:0;border:1px solid #8886;border-radius:9px;overflow:hidden}'
            'img{width:100%;display:block}figcaption{padding:.6rem;overflow-wrap:anywhere}'
            'small{display:block;opacity:.7}section{margin-top:2rem;padding-top:1rem;border-top:1px solid #8886}'
            'nav a{display:inline-block;margin:.4rem}</style></head><body>'
            f'<h1>MyFinHub Android — UX review ({len(found)} PNGs)</h1>'
            f'<p>Source {html.escape(source_sha)} · run {html.escape(run_id)} · validation {html.escape(validation)}</p>'
            '<p>Synthetic Compose fixtures only. Screenshots cannot establish useful clicks, real'
            ' navigation, backend correctness, FLAG_SECURE or S24 acceptance. Legacy routes separate.</p>'
            '<p><a href="review-index.json">Manifest / checksums</a> · '
            '<a href="review-checklist.md">Review checklist</a></p><nav>'
            + " ".join(cards) + '</nav>' + "".join(sections) + "</body></html>")
    target.write_text(page, encoding="utf-8")
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, default=Path("app/src/screenshotTestDebug/reference"))
    parser.add_argument("--target", type=Path, default=Path("app/build/reports/screenshotTest/gallery/review.html"))
    args = parser.parse_args()
    manifest = render(args.source, args.target,
        source_sha=os.environ.get("MYFINHUB_REVIEW_SOURCE_SHA", "unknown"),
        run_id=os.environ.get("GITHUB_RUN_ID", "local"),
        validation=os.environ.get("MYFINHUB_REVIEW_VALIDATION", "unknown"))
    print(f"UX review indexed {manifest['screenshot_count']} official PNGs")