#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Protocol

EXPECTED_PROJECT_HOST = "ahsukppxwaiagampsuzb.supabase.co"
BUCKET = "android-releases"
CHANNEL = "phase6-test"
TABLE = "rheomiq_android_releases"
MAX_APK_BYTES = 300 * 1024 * 1024
PHASE6_VERSION_BASE = 6000


class PublisherError(RuntimeError):
    pass


class NotFound(PublisherError):
    pass


class RemoteWriteError(PublisherError):
    pass


@dataclass(frozen=True)
class ReleaseSpec:
    version_code: int
    version_name: str
    storage_path: str
    sha256: str
    size_bytes: int
    mandatory: bool = False
    enabled: bool = True

    def metadata(self) -> dict[str, Any]:
        return {
            "channel": CHANNEL,
            "version_code": self.version_code,
            "version_name": self.version_name,
            "storage_path": self.storage_path,
            "sha256": self.sha256,
            "size_bytes": self.size_bytes,
            "mandatory": self.mandatory,
            "enabled": self.enabled,
            "notes": "Protected automated phase6-test publisher.",
        }


class ReleaseClient(Protocol):
    def get_release(self, version_code: int) -> dict[str, Any] | None: ...
    def get_latest_version_code(self) -> int | None: ...
    def download_object(self, storage_path: str) -> bytes: ...
    def upload_object(self, storage_path: str, data: bytes) -> None: ...
    def insert_release(self, metadata: dict[str, Any]) -> None: ...


def validate_project_url(project_url: str) -> str:
    parsed = urllib.parse.urlparse(project_url)
    if parsed.scheme != "https" or parsed.hostname != EXPECTED_PROJECT_HOST:
        raise PublisherError("Refusing unexpected Supabase project URL.")
    if parsed.path not in ("", "/") or parsed.params or parsed.query or parsed.fragment:
        raise PublisherError("Supabase project URL must not contain a path/query/fragment.")
    return f"https://{EXPECTED_PROJECT_HOST}"


def expected_version_name(version_code: int) -> str:
    if version_code <= PHASE6_VERSION_BASE:
        raise PublisherError("phase6-test versionCode must be above the Phase 6 base.")
    return f"0.1.0-phase6.{version_code - PHASE6_VERSION_BASE}"


def build_spec(apk_path: Path, version_code: int, version_name: str) -> ReleaseSpec:
    if not apk_path.is_file():
        raise PublisherError("APK file is missing.")
    data = apk_path.read_bytes()
    size = len(data)
    if size < 1 or size > MAX_APK_BYTES:
        raise PublisherError("APK size is outside the allowed release bounds.")
    expected_name = expected_version_name(version_code)
    if version_name != expected_name:
        raise PublisherError(f"Unexpected phase6-test versionName; expected {expected_name}.")
    if not re.fullmatch(r"0\.1\.0-phase6\.[1-9][0-9]*", version_name):
        raise PublisherError("Invalid phase6-test versionName format.")
    sha = hashlib.sha256(data).hexdigest()
    path = f"{CHANNEL}/{version_code}/MyFinHub-Phase6-{version_code}.apk"
    return ReleaseSpec(version_code, version_name, path, sha, size)


def release_matches(row: dict[str, Any], spec: ReleaseSpec) -> bool:
    expected = spec.metadata()
    for key in ("channel", "version_code", "version_name", "storage_path", "sha256", "size_bytes", "mandatory", "enabled"):
        if row.get(key) != expected[key]:
            return False
    return True


def verify_remote_object(client: ReleaseClient, spec: ReleaseSpec) -> bool:
    try:
        data = client.download_object(spec.storage_path)
    except NotFound:
        return False
    if len(data) != spec.size_bytes:
        raise PublisherError("Remote APK size does not match the verified local artifact.")
    if hashlib.sha256(data).hexdigest() != spec.sha256:
        raise PublisherError("Remote APK SHA-256 does not match the verified local artifact.")
    return True


def publish_release(client: ReleaseClient, spec: ReleaseSpec, apk_bytes: bytes) -> str:
    existing = client.get_release(spec.version_code)
    if existing is not None:
        if not release_matches(existing, spec):
            raise PublisherError("Release metadata already exists with different verified values.")
        if not verify_remote_object(client, spec):
            raise PublisherError("Matching metadata exists but the private APK object is missing.")
        return "already-published"

    object_verified = verify_remote_object(client, spec)
    if not object_verified:
        try:
            client.upload_object(spec.storage_path, apk_bytes)
        except RemoteWriteError:
            if not verify_remote_object(client, spec):
                raise PublisherError("Upload outcome could not be confirmed; refusing blind retry.")
        if not verify_remote_object(client, spec):
            raise PublisherError("Uploaded APK could not be re-read and verified.")

    try:
        client.insert_release(spec.metadata())
    except RemoteWriteError:
        reconciled = client.get_release(spec.version_code)
        if reconciled is None or not release_matches(reconciled, spec):
            raise PublisherError("Metadata publish outcome could not be confirmed; refusing blind retry.")
        return "published-after-reconciliation"

    final_row = client.get_release(spec.version_code)
    if final_row is None or not release_matches(final_row, spec):
        raise PublisherError("Published metadata failed final reconciliation.")
    return "published"


class SupabaseReleaseClient:
    def __init__(self, project_url: str, secret_key: str, timeout_seconds: float = 30.0) -> None:
        self.project_url = validate_project_url(project_url)
        if not secret_key or len(secret_key) < 20:
            raise PublisherError("Protected Supabase publish credential is unavailable.")
        self.secret_key = secret_key
        self.timeout_seconds = timeout_seconds

    def _request(self, method: str, path: str, *, data: bytes | None = None, headers: dict[str, str] | None = None,
                 allow_not_found: bool = False, write: bool = False) -> bytes:
        request_headers = {
            "Authorization": f"Bearer {self.secret_key}",
            "apikey": self.secret_key,
        }
        if headers:
            request_headers.update(headers)
        req = urllib.request.Request(f"{self.project_url}{path}", data=data, headers=request_headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=self.timeout_seconds) as response:
                return response.read()
        except urllib.error.HTTPError as exc:
            if allow_not_found and exc.code == 404:
                raise NotFound("Remote object not found.") from None
            if write:
                raise RemoteWriteError(f"Remote write returned HTTP {exc.code}.") from None
            raise PublisherError(f"Supabase read returned HTTP {exc.code}.") from None
        except (urllib.error.URLError, TimeoutError, OSError):
            if write:
                raise RemoteWriteError("Remote write transport outcome is ambiguous.") from None
            raise PublisherError("Supabase read transport failed.") from None

    def get_release(self, version_code: int) -> dict[str, Any] | None:
        query = urllib.parse.urlencode({
            "channel": f"eq.{CHANNEL}",
            "version_code": f"eq.{version_code}",
            "select": "channel,version_code,version_name,storage_path,sha256,size_bytes,mandatory,enabled",
        })
        raw = self._request("GET", f"/rest/v1/{TABLE}?{query}")
        rows = json.loads(raw.decode("utf-8"))
        if not isinstance(rows, list):
            raise PublisherError("Unexpected release metadata response.")
        if len(rows) > 1:
            raise PublisherError("Multiple release rows exist for one phase6-test versionCode.")
        return rows[0] if rows else None

    def get_latest_version_code(self) -> int | None:
        query = urllib.parse.urlencode({
            "channel": f"eq.{CHANNEL}",
            "select": "version_code",
            "order": "version_code.desc",
            "limit": "1",
        })
        raw = self._request("GET", f"/rest/v1/{TABLE}?{query}")
        rows = json.loads(raw.decode("utf-8"))
        if not isinstance(rows, list) or len(rows) > 1:
            raise PublisherError("Unexpected latest-release metadata response.")
        return int(rows[0]["version_code"]) if rows else None

    def download_object(self, storage_path: str) -> bytes:
        quoted = "/".join(urllib.parse.quote(part, safe="") for part in storage_path.split("/"))
        return self._request("GET", f"/storage/v1/object/authenticated/{BUCKET}/{quoted}", allow_not_found=True)

    def upload_object(self, storage_path: str, data: bytes) -> None:
        quoted = "/".join(urllib.parse.quote(part, safe="") for part in storage_path.split("/"))
        self._request(
            "POST",
            f"/storage/v1/object/{BUCKET}/{quoted}",
            data=data,
            headers={
                "Content-Type": "application/vnd.android.package-archive",
                "x-upsert": "false",
                "cache-control": "3600",
            },
            write=True,
        )

    def insert_release(self, metadata: dict[str, Any]) -> None:
        body = json.dumps(metadata, separators=(",", ":")).encode("utf-8")
        self._request(
            "POST",
            f"/rest/v1/{TABLE}",
            data=body,
            headers={"Content-Type": "application/json", "Prefer": "return=minimal"},
            write=True,
        )


def command_plan(args: argparse.Namespace) -> int:
    client = SupabaseReleaseClient(args.project_url, args.secret_key)
    latest = client.get_latest_version_code()
    if latest is None:
        raise PublisherError("No existing phase6-test baseline exists; refusing automatic version planning.")
    version_code = latest + 1
    version_name = expected_version_name(version_code)
    storage_path = f"{CHANNEL}/{version_code}/MyFinHub-Phase6-{version_code}.apk"
    print(json.dumps({"version_code": version_code, "version_name": version_name, "storage_path": storage_path}, separators=(",", ":")))
    return 0


def command_publish(args: argparse.Namespace) -> int:
    apk_path = Path(args.apk)
    spec = build_spec(apk_path, args.version_code, args.version_name)
    client = SupabaseReleaseClient(args.project_url, args.secret_key)
    result = publish_release(client, spec, apk_path.read_bytes())
    print(json.dumps({
        "result": result,
        "channel": CHANNEL,
        "version_code": spec.version_code,
        "version_name": spec.version_name,
        "storage_path": spec.storage_path,
        "sha256": spec.sha256,
        "size_bytes": spec.size_bytes,
    }, separators=(",", ":")))
    return 0


def parser() -> argparse.ArgumentParser:
    result = argparse.ArgumentParser(description="Protected MyFinHub phase6-test release publisher.")
    result.add_argument("--project-url", default=os.environ.get("SUPABASE_URL", f"https://{EXPECTED_PROJECT_HOST}"))
    result.add_argument("--secret-key", default=os.environ.get("SUPABASE_RELEASE_PUBLISH_KEY", ""), help=argparse.SUPPRESS)
    sub = result.add_subparsers(dest="command", required=True)
    sub.add_parser("plan")
    publish = sub.add_parser("publish")
    publish.add_argument("--apk", required=True)
    publish.add_argument("--version-code", required=True, type=int)
    publish.add_argument("--version-name", required=True)
    return result


def main() -> int:
    args = parser().parse_args()
    try:
        if args.command == "plan":
            return command_plan(args)
        if args.command == "publish":
            return command_publish(args)
        raise PublisherError("Unknown command.")
    except PublisherError as exc:
        print(f"release publisher failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
