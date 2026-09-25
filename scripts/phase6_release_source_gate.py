#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

EXPECTED_BASE = "develop"
EXPECTED_BRANCH_PREFIX = "android/"
REQUIRED_CHECKS = (
    "verify",
    "screenshot-regression",
    "s24-ultra-target-instrumented",
)


class SourceGateError(RuntimeError):
    pass


def validate_source_pr(repo: str, requested_pr: int, payload: dict[str, Any]) -> str:
    if payload.get("number") != requested_pr:
        raise SourceGateError("GitHub returned an unexpected PR number.")
    if payload.get("state") != "open":
        raise SourceGateError("Release source PR must still be open.")
    if payload.get("draft") is True:
        raise SourceGateError("Release source PR must be ready for review, not draft.")

    base = payload.get("base") or {}
    head = payload.get("head") or {}
    head_repo = head.get("repo") or {}
    if base.get("ref") != EXPECTED_BASE:
        raise SourceGateError("Release source PR must target Android develop.")
    if head_repo.get("full_name") != repo:
        raise SourceGateError("Release source PR must come from the same Android repository.")

    head_ref = head.get("ref")
    if not isinstance(head_ref, str) or not head_ref.startswith(EXPECTED_BRANCH_PREFIX):
        raise SourceGateError("Release source branch must be Android-owned.")

    sha = head.get("sha")
    if not isinstance(sha, str) or re.fullmatch(r"[0-9a-f]{40}", sha) is None:
        raise SourceGateError("Release source PR does not expose a valid immutable head SHA.")
    return sha


def validate_required_checks(payload: dict[str, Any]) -> None:
    runs = payload.get("check_runs")
    if not isinstance(runs, list):
        raise SourceGateError("Unexpected GitHub check-runs response.")

    latest_by_name: dict[str, dict[str, Any]] = {}
    for run in runs:
        if not isinstance(run, dict):
            continue
        name = run.get("name")
        run_id = run.get("id")
        if not isinstance(name, str) or not isinstance(run_id, int):
            continue
        current = latest_by_name.get(name)
        if current is None or run_id > int(current.get("id", -1)):
            latest_by_name[name] = run

    failures: list[str] = []
    for name in REQUIRED_CHECKS:
        run = latest_by_name.get(name)
        if run is None or run.get("status") != "completed" or run.get("conclusion") != "success":
            failures.append(name)
    if failures:
        raise SourceGateError("Latest exact-head checks are not successful: " + ", ".join(failures))


def _fetch_json(url: str, token: str) -> dict[str, Any]:
    req = urllib.request.Request(
        url,
        headers={
            "Authorization": f"Bearer {token}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=20) as response:
            payload = json.load(response)
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, OSError, json.JSONDecodeError) as exc:
        raise SourceGateError("GitHub source-gate read failed.") from exc
    if not isinstance(payload, dict):
        raise SourceGateError("Unexpected GitHub source-gate response.")
    return payload


def main() -> int:
    repo = os.environ.get("GITHUB_REPOSITORY", "")
    token = os.environ.get("GH_TOKEN", "")
    raw_pr = os.environ.get("SOURCE_PR", "")
    output_path = os.environ.get("GITHUB_OUTPUT", "")
    if not repo or not token or not output_path:
        print("release source gate failed: required GitHub runtime context is unavailable.", file=sys.stderr)
        return 2
    if re.fullmatch(r"[1-9][0-9]*", raw_pr) is None:
        print("release source gate failed: source_pr must be a positive PR number.", file=sys.stderr)
        return 2

    requested_pr = int(raw_pr)
    quoted_repo = urllib.parse.quote(repo, safe="/")
    try:
        pr_payload = _fetch_json(f"https://api.github.com/repos/{quoted_repo}/pulls/{requested_pr}", token)
        sha = validate_source_pr(repo, requested_pr, pr_payload)
        checks_payload = _fetch_json(
            f"https://api.github.com/repos/{quoted_repo}/commits/{sha}/check-runs?per_page=100&filter=latest",
            token,
        )
        validate_required_checks(checks_payload)
        with open(output_path, "a", encoding="utf-8") as output:
            output.write(f"source_sha={sha}\n")
    except SourceGateError as exc:
        print(f"release source gate failed: {exc}", file=sys.stderr)
        return 2

    print("Validated same-repository Android PR source and latest exact-head checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
