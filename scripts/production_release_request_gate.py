#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import re
import subprocess
import sys
from pathlib import Path

REQUEST_PATH = Path(".github/release-requests/production.json")
EXPECTED_REF = "refs/heads/develop"
EXPECTED_ACTOR = "MariosGiannakaras"
VERSION_NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")


class RequestGateError(RuntimeError):
    pass


def _positive_pr(value: object) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value <= 0:
        raise RequestGateError("source_pr must be a positive integer.")
    return value


def _version_name(value: object) -> str:
    if not isinstance(value, str) or VERSION_NAME_RE.fullmatch(value) is None:
        raise RequestGateError("version_name is invalid.")
    return value


def parse_request(path: Path = REQUEST_PATH) -> tuple[int, str]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RequestGateError("Production release request file is missing or invalid JSON.") from exc
    if not isinstance(payload, dict) or set(payload) != {"source_pr", "version_name", "request_id"}:
        raise RequestGateError("Production request must contain exactly source_pr, version_name and request_id.")
    request_id = payload.get("request_id")
    if not isinstance(request_id, str) or re.fullmatch(r"[A-Za-z0-9._-]{8,80}", request_id) is None:
        raise RequestGateError("request_id is invalid.")
    return _positive_pr(payload.get("source_pr")), _version_name(payload.get("version_name"))


def validate_push(before: str, sha: str, ref: str, actor: str) -> None:
    if ref != EXPECTED_REF:
        raise RequestGateError("Production release request push must target develop.")
    if actor != EXPECTED_ACTOR:
        raise RequestGateError("Production release request must be initiated by the repository owner.")
    if re.fullmatch(r"[0-9a-f]{40}", before or "") is None or re.fullmatch(r"[0-9a-f]{40}", sha or "") is None:
        raise RequestGateError("Push commit range is invalid.")
    result = subprocess.run(
        ["git", "diff", "--name-only", before, sha],
        check=True,
        capture_output=True,
        text=True,
    )
    changed = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    if changed != [str(REQUEST_PATH)]:
        raise RequestGateError("Production release request push must change only the guarded request file.")


def resolve_request() -> tuple[int, str]:
    event = os.environ.get("GITHUB_EVENT_NAME", "")
    if event == "workflow_dispatch":
        raw_pr = os.environ.get("SOURCE_PR", "")
        if re.fullmatch(r"[1-9][0-9]*", raw_pr) is None:
            raise RequestGateError("source_pr must be a positive PR number.")
        return int(raw_pr), _version_name(os.environ.get("VERSION_NAME", ""))
    if event != "push":
        raise RequestGateError("Unsupported production release trigger event.")
    validate_push(
        os.environ.get("GITHUB_EVENT_BEFORE", ""),
        os.environ.get("GITHUB_SHA", ""),
        os.environ.get("GITHUB_REF", ""),
        os.environ.get("GITHUB_ACTOR", ""),
    )
    return parse_request()


def main() -> int:
    output_path = os.environ.get("GITHUB_OUTPUT", "")
    if not output_path:
        print("production release request gate failed: GITHUB_OUTPUT is unavailable.", file=sys.stderr)
        return 2
    try:
        source_pr, version_name = resolve_request()
        with open(output_path, "a", encoding="utf-8") as out:
            out.write(f"source_pr={source_pr}\n")
            out.write(f"version_name={version_name}\n")
    except (RequestGateError, subprocess.CalledProcessError) as exc:
        print(f"production release request gate failed: {exc}", file=sys.stderr)
        return 2
    print(f"Validated guarded production release request for PR #{source_pr}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
