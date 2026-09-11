#!/usr/bin/env python3
"""Render and validate the canonical Android project tracking files.

The only hand-edited state is tracking/android-project-state.json. STATUS.md,
TODO.md and docs/CURRENT_HANDOFF.md are generated from it. CI also uses this
script to require product/release PRs to update the canonical state.
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STATE_PATH = ROOT / "tracking/android-project-state.json"
GENERATED = {
    ROOT / "STATUS.md": "status",
    ROOT / "TODO.md": "todo",
    ROOT / "docs/CURRENT_HANDOFF.md": "handoff",
}

TRACKED_PREFIXES = (
    "app/",
    "benchmark/",
    "gradle/",
    "screenshots/",
    ".github/workflows/",
    ".github/release-requests/",
    "scripts/",
)
TRACKED_FILES = {
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
}
STATE_REL = "tracking/android-project-state.json"


def load_state() -> dict:
    data = json.loads(STATE_PATH.read_text(encoding="utf-8"))
    required = {
        "schema_version",
        "updated_at",
        "overall_progress",
        "supported_device",
        "authoritative_branch",
        "release_branch",
        "active_workstream",
        "latest_production_candidate",
        "completed",
        "constraints",
        "repo_tracking",
    }
    missing = sorted(required - data.keys())
    if missing:
        raise SystemExit(f"Tracking state is missing required keys: {', '.join(missing)}")
    progress = data["overall_progress"]
    if not isinstance(progress.get("completed"), int) or not isinstance(progress.get("total"), int):
        raise SystemExit("overall_progress.completed/total must be integers")
    if progress["completed"] < 0 or progress["completed"] > progress["total"]:
        raise SystemExit("overall_progress is invalid")
    validate_redesign(data)
    return data


def validate_redesign(s: dict) -> None:
    """Validate checkpoint integrity; completion counts are derived, never entered."""
    tasks = s.get("redesign_tasks")
    if tasks is None:
        return  # Historical schema-v1 state before the new redesign.
    if not isinstance(tasks, list) or len(tasks) != 10:
        raise SystemExit("redesign_tasks must contain the ten agreed slices")
    rows = list(s["current_redesign_pass"]["preparation"])
    if len(rows) != 4:
        raise SystemExit("Preparation must retain four checkpoints")
    for i, task in enumerate(tasks, 1):
        if task["id"] != f"S{i}" or len(task["subtasks"]) != 4:
            raise SystemExit("Redesign slice IDs/counts must remain stable")
        for j, subtask in enumerate(task["subtasks"], 1):
            if subtask["id"] != f"S{i}.{j}":
                raise SystemExit("Redesign subtask IDs must remain stable")
        rows.extend(task["subtasks"])
    for i, row in enumerate(rows[:4], 1):
        if row["id"] != f"P{i}":
            raise SystemExit("Preparation IDs must remain stable")
    for row in rows:
        if row["status"] not in {"pending", "in_progress", "blocked", "completed"}:
            raise SystemExit(f"Invalid checkpoint status: {row['id']}")
        evidence = row.get("evidence", [])
        if not isinstance(evidence, list) or any(not isinstance(e, str) or not e.strip() for e in evidence):
            raise SystemExit(f"Invalid evidence: {row['id']}")
        if row["status"] == "completed" and not evidence:
            raise SystemExit(f"Completed checkpoint needs evidence: {row['id']}")
        if row["status"] == "blocked" and not row.get("blocker", "").strip():
            raise SystemExit(f"Blocked checkpoint needs a reason: {row['id']}")


def redesign_lines(s: dict) -> list[str]:
    tasks = s.get("redesign_tasks", [])
    if not tasks:
        return []
    complete = lambda row: row["status"] == "completed"
    subtasks = [sub for task in tasks for sub in task["subtasks"]]
    completed_tasks = sum(all(complete(sub) for sub in task["subtasks"]) for task in tasks)
    r = s["current_redesign_pass"]
    prep = r["preparation"]
    lines = [
        "", "## Android redesign progress", "",
        f"**Tasks: {completed_tasks}/{len(tasks)} · Subtasks: {sum(map(complete, subtasks))}/{len(subtasks)} · Preparation: {sum(map(complete, prep))}/{len(prep)}**",
        "", "These counts are separate from historical overall project progress. Documents do not count as implemented Android screens.",
        "", f"Working branch: `{r['branch']}`. PR: {r.get('pr') or 'not yet opened'}.",
        f"Checkpoint: `{r['checkpoint']}`.",
        f"Specification: `{r['plan_doc']}`.",
        f"Next action: {r['next_action']}",
        "", "| Slice | Completed subtasks | Remaining |", "|---|---|---|",
    ]
    for task in tasks:
        done = sum(map(complete, task["subtasks"]))
        remaining = "; ".join(f"{sub['id']} {sub['title']} ({sub['status']})" for sub in task["subtasks"] if not complete(sub)) or "Complete"
        lines.append(f"| {task['id']} {task['title']} | {done}/{len(task['subtasks'])} | {remaining} |")
    lines += ["", "### Preparation", ""]
    lines.extend(f"- [{'x' if complete(row) else ' '}] {row['id']} {row['title']} — {row['status']}" for row in prep)
    lines += ["", "### Blockers", ""]
    blockers = list(r.get("blockers", [])) + [f"{row['id']}: {row['blocker']}" for row in prep + subtasks if row["status"] == "blocked"]
    lines.extend(f"- {b}" for b in blockers or ["No recorded blocker. Physical S24 acceptance remains a future required gate, not an automated completion claim."])
    return lines + [""]


def header() -> str:
    return "<!-- GENERATED by scripts/render_project_tracking.py from tracking/android-project-state.json. DO NOT EDIT BY HAND. -->\n"


def render_status(s: dict) -> str:
    p = s["overall_progress"]
    w = s["active_workstream"]
    r = s["latest_production_candidate"]
    lines = [
        header().rstrip(),
        "# MyFinHub Android — Current Status",
        "",
        f"**Updated:** {s['updated_at']}  ",
        f"**Overall:** {p['completed']}/{p['total']}  ",
        f"**Authoritative branch:** `{s['authoritative_branch']}`  ",
        f"**Supported device:** {s['supported_device']}",
        "",
        "## Active workstream",
        "",
        f"Issue #{w['issue']} — **{w['title']}**  ",
        f"State: `{w['status']}`",
        "",
        w["summary"],
        "",
        "## Current production candidate",
        "",
        f"`{r['version_name']}` / versionCode `{r['version_code']}` — **{r['status']}**.",
        "",
        r["notes"],
        "",
        "## Next",
        "",
    ]
    lines.extend(f"{i}. {item}" for i, item in enumerate(w["next"], 1))
    lines += ["", "## Non-negotiable constraints", ""]
    lines.extend(f"- {item}" for item in s["constraints"])
    lines += [
        "",
        "## Tracking contract",
        "",
        f"Canonical source: `{s['repo_tracking']['source_of_truth']}`.",
        "",
        s["repo_tracking"]["policy"],
        "",
    ]
    return "\n".join(lines)


def render_todo(s: dict) -> str:
    w = s["active_workstream"]
    lines = [
        header().rstrip(),
        "# MyFinHub Android — Current TODO",
        "",
        f"Active tracker: issue #{w['issue']} — **{w['title']}**",
        "",
        "## Open",
        "",
    ]
    lines.extend(f"- [ ] {item}" for item in w["next"])
    lines += ["", "## Completed foundations", ""]
    lines.extend(f"- [x] {item}" for item in s["completed"])
    lines += [
        "",
        "## Rule",
        "",
        "Do not append historical TODO sections here. Git history and closed issues/PRs are the history. Update `tracking/android-project-state.json` and regenerate this file instead.",
        "",
    ]
    return "\n".join(lines)


def render_handoff(s: dict) -> str:
    p = s["overall_progress"]
    w = s["active_workstream"]
    r = s["latest_production_candidate"]
    lines = [
        header().rstrip(),
        "# MyFinHub Android — Current Handoff",
        "",
        "This file exists so a new chat/agent can continue correctly without relying on conversation memory.",
        "",
        "## Mandatory startup sequence",
        "",
        "1. Read root `AGENTS.md`.",
        "2. Read `tracking/android-project-state.json` and this file.",
        "3. Read permanent issue #27.",
        "4. Inspect live `develop`, open PRs/issues, recent merged PRs and relevant workflow results.",
        "5. Treat live GitHub state as authoritative if it is newer than generated files; fix tracking drift before product work.",
        "6. Continue the active workstream without repeating completed discovery.",
        "",
        "## Current facts",
        "",
        f"- Overall progress: **{p['completed']}/{p['total']}**.",
        f"- Supported device: **{s['supported_device']} only**.",
        f"- Active workstream: issue #{w['issue']} — {w['title']}.",
        f"- Workstream state: `{w['status']}`.",
        f"- Latest private production candidate: `{r['version_name']}` / `{r['version_code']}` — `{r['status']}`.",
        f"- `develop` is the authoritative implementation branch; `{s['release_branch']}` is release-only.",
        "",
        "## Why implementation is open",
        "",
        w["summary"],
        "",
        "## Immediate work",
        "",
    ]
    lines.extend(f"- {item}" for item in w["next"])
    lines += ["", "## Constraints", ""]
    lines.extend(f"- {item}" for item in s["constraints"])
    lines += [
        "",
        "## Tracking discipline",
        "",
        "`tracking/android-project-state.json` is the only hand-edited current-state file. Run `python3 scripts/render_project_tracking.py` after changing it. CI checks that generated files match and, on product/release PRs, that the canonical state changed in the same PR.",
        "",
    ]
    return "\n".join(lines)


def rendered(s: dict) -> dict[Path, str]:
    outputs = {
        ROOT / "STATUS.md": render_status(s),
        ROOT / "TODO.md": render_todo(s),
        ROOT / "docs/CURRENT_HANDOFF.md": render_handoff(s),
    }
    progress = "\n".join(redesign_lines(s))
    return {path: content + progress for path, content in outputs.items()}


def changed_files(base_ref: str) -> list[str]:
    subprocess.run(["git", "fetch", "origin", base_ref, "--depth=1"], cwd=ROOT, check=True)
    out = subprocess.check_output(
        ["git", "diff", "--name-only", f"origin/{base_ref}...HEAD"],
        cwd=ROOT,
        text=True,
    )
    return [line.strip() for line in out.splitlines() if line.strip()]


def is_product_or_release(path: str) -> bool:
    if path == STATE_REL or path in {"STATUS.md", "TODO.md", "docs/CURRENT_HANDOFF.md", "AGENTS.md"}:
        return False
    return path in TRACKED_FILES or path.startswith(TRACKED_PREFIXES)


def enforce_pr_tracking_change() -> None:
    base_ref = os.environ.get("GITHUB_BASE_REF", "").strip()
    if not base_ref:
        return
    files = changed_files(base_ref)
    if any(is_product_or_release(path) for path in files) and STATE_REL not in files:
        product_files = [path for path in files if is_product_or_release(path)]
        preview = "\n  - ".join(product_files[:20])
        raise SystemExit(
            "Product/release changes require tracking/android-project-state.json in the same PR.\n"
            f"Changed tracked paths:\n  - {preview}"
        )


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="Fail if generated files are stale")
    parser.add_argument(
        "--enforce-pr-tracking-change",
        action="store_true",
        help="On PRs, require canonical state to change when product/release paths change",
    )
    args = parser.parse_args()

    state = load_state()
    outputs = rendered(state)
    stale: list[str] = []
    for path, content in outputs.items():
        expected = content.rstrip() + "\n"
        if args.check:
            actual = path.read_text(encoding="utf-8") if path.exists() else ""
            if actual != expected:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(expected, encoding="utf-8")

    if stale:
        raise SystemExit(
            "Generated tracking files are stale: " + ", ".join(stale) +
            ". Run: python3 scripts/render_project_tracking.py"
        )
    if args.enforce_pr_tracking_change:
        enforce_pr_tracking_change()
    return 0


if __name__ == "__main__":
    sys.exit(main())
