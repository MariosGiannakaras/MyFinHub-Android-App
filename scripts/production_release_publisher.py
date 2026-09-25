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
CHANNEL = "production"
TABLE = "rheomiq_android_releases"
MAX_APK_BYTES = 300 * 1024 * 1024
PRODUCTION_VERSION_BASE = 10000
NOTES_PREFIX = "Protected automated production publisher"
VERSION_NAME_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")


class PublisherError(RuntimeError):
    pass


class NotFound(PublisherError):
    pass


class RemoteWriteError(PublisherError):
    pass


def validate_release_run_id(value: str) -> str:
    if re.fullmatch(r"[1-9][0-9]{0,19}", value or "") is None:
        raise PublisherError("A valid GitHub release run ID is required.")
    return value


def validate_version_name(value: str) -> str:
    if VERSION_NAME_RE.fullmatch(value or "") is None:
        raise PublisherError("Production versionName is invalid.")
    return value


def release_notes(release_run_id: str) -> str:
    return f"{NOTES_PREFIX}; github_run_id={validate_release_run_id(release_run_id)}."


def expected_storage_path(version_code: int) -> str:
    if version_code < PRODUCTION_VERSION_BASE:
        raise PublisherError("Production versionCode is below the reserved production range.")
    return f"production/{version_code}/MyFinHub-{version_code}.apk"


@dataclass(frozen=True)
class ReleaseSpec:
    version_code: int
    version_name: str
    storage_path: str
    sha256: str
    size_bytes: int
    release_run_id: str
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
            "notes": release_notes(self.release_run_id),
        }


class ReleaseClient(Protocol):
    def get_release(self, version_code: int) -> dict[str, Any] | None: ...
    def get_release_by_run_id(self, release_run_id: str) -> dict[str, Any] | None: ...
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


def api_key_headers(secret_key: str) -> dict[str, str]:
    if secret_key.startswith("sb_publishable_"):
        raise PublisherError("A publishable Supabase key cannot be used by the production publisher.")
    headers = {"apikey": secret_key}
    if not secret_key.startswith("sb_secret_"):
        headers["Authorization"] = f"Bearer {secret_key}"
    return headers


def build_spec(
    apk_path: Path,
    version_code: int,
    version_name: str,
    release_run_id: str,
) -> ReleaseSpec:
    if not apk_path.is_file():
        raise PublisherError("APK file is missing.")
    if version_code < PRODUCTION_VERSION_BASE:
        raise PublisherError("Production versionCode is below the reserved production range.")
    version_name = validate_version_name(version_name)
    release_run_id = validate_release_run_id(release_run_id)
    data = apk_path.read_bytes()
    size = len(data)
    if size < 1 or size > MAX_APK_BYTES:
        raise PublisherError("APK size is outside the allowed release bounds.")
    return ReleaseSpec(
        version_code=version_code,
        version_name=version_name,
        storage_path=expected_storage_path(version_code),
        sha256=hashlib.sha256(data).hexdigest(),
        size_bytes=size,
        release_run_id=release_run_id,
    )


def release_matches(row: dict[str, Any], spec: ReleaseSpec) -> bool:
    expected = spec.metadata()
    keys = (
        "channel",
        "version_code",
        "version_name",
        "storage_path",
        "sha256",
        "size_bytes",
        "mandatory",
        "enabled",
        "notes",
    )
    return all(row.get(key) == expected[key] for key in keys)


def verify_remote_object(client: ReleaseClient, spec: ReleaseSpec) -> bool:
    try:
        data = client.download_object(spec.storage_path)
    except NotFound:
        return False
    if len(data) != spec.size_bytes:
        raise PublisherError("Remote production APK size does not match the verified local artifact.")
    if hashlib.sha256(data).hexdigest() != spec.sha256:
        raise PublisherError("Remote production APK SHA-256 does not match the verified local artifact.")
    return True


def publish_release(client: ReleaseClient, spec: ReleaseSpec, apk_bytes: bytes) -> str:
    existing = client.get_release(spec.version_code)
    if existing is not None:
        if not release_matches(existing, spec):
            raise PublisherError("Production release metadata already exists with different verified values.")
        if not verify_remote_object(client, spec):
            raise PublisherError("Matching production metadata exists but the private APK object is missing.")
        return "already-published"

    try:
        client.upload_object(spec.storage_path, apk_bytes)
    except RemoteWriteError:
        if not verify_remote_object(client, spec):
            raise PublisherError("Production upload outcome could not be confirmed; refusing blind retry.")
    else:
        if not verify_remote_object(client, spec):
            raise PublisherError("Uploaded production APK could not be re-read and verified.")

    try:
        client.insert_release(spec.metadata())
    except RemoteWriteError:
        reconciled = client.get_release(spec.version_code)
        if reconciled is None or not release_matches(reconciled, spec):
            raise PublisherError("Production metadata outcome could not be confirmed; refusing blind retry.")
        return "published-after-reconciliation"

    final_row = client.get_release(spec.version_code)
    if final_row is None or not release_matches(final_row, spec):
        raise PublisherError("Published production metadata failed final reconciliation.")
    return "published"


def plan_next_release(client: ReleaseClient, release_run_id: str, version_name: str) -> dict[str, Any]:
    release_run_id = validate_release_run_id(release_run_id)
    version_name = validate_version_name(version_name)
    correlated = client.get_release_by_run_id(release_run_id)
    if correlated is not None:
        try:
            version_code = int(correlated["version_code"])
        except (KeyError, TypeError, ValueError) as exc:
            raise PublisherError("Correlated production metadata has an invalid versionCode.") from exc
        if correlated.get("channel") != CHANNEL:
            raise PublisherError("Correlated release belongs to an unexpected channel.")
        if version_code < PRODUCTION_VERSION_BASE:
            raise PublisherError("Correlated release is below the reserved production version range.")
        if correlated.get("version_name") != version_name:
            raise PublisherError("Correlated production release has a different versionName.")
        if correlated.get("storage_path") != expected_storage_path(version_code):
            raise PublisherError("Correlated production storage path is noncanonical.")
        if correlated.get("notes") != release_notes(release_run_id):
            raise PublisherError("Correlated production run marker does not match.")
        return {
            "version_code": version_code,
            "version_name": version_name,
            "storage_path": expected_storage_path(version_code),
            "resumed": True,
        }

    latest = client.get_latest_version_code()
    if latest is None:
        version_code = PRODUCTION_VERSION_BASE
    else:
        if latest < PRODUCTION_VERSION_BASE:
            raise PublisherError("Existing production metadata is below the reserved version range.")
        version_code = latest + 1
    return {
        "version_code": version_code,
        "version_name": version_name,
        "storage_path": expected_storage_path(version_code),
        "resumed": False,
    }


class SupabaseReleaseClient:
    RELEASE_SELECT = "channel,version_code,version_name,storage_path,sha256,size_bytes,mandatory,enabled,notes"

    def __init__(self, project_url: str, secret_key: str, timeout_seconds: float = 30.0) -> None:
        self.project_url = validate_project_url(project_url)
        if not secret_key or len(secret_key) < 20:
            raise PublisherError("Protected production Supabase publish credential is unavailable.")
        self.secret_key = secret_key
        self.timeout_seconds = timeout_seconds
        api_key_headers(secret_key)

    def _request(
        self,
        method: str,
        path: str,
        *,
        data: bytes | None = None,
        headers: dict[str, str] | None = None,
        allow_not_found: bool = False,
        write: bool = False,
    ) -> bytes:
        request_headers = api_key_headers(self.secret_key)
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

    def _single_release(self, query: str, description: str) -> dict[str, Any] | None:
        raw = self._request("GET", f"/rest/v1/{TABLE}?{query}")
        rows = json.loads(raw.decode("utf-8"))
        if not isinstance(rows, list):
            raise PublisherError(f"Unexpected {description} response.")
        if len(rows) > 1:
            raise PublisherError(f"Multiple rows returned for {description}.")
        return rows[0] if rows else None

    def get_release(self, version_code: int) -> dict[str, Any] | None:
        query = urllib.parse.urlencode({
            "channel": f"eq.{CHANNEL}",
            "version_code": f"eq.{version_code}",
            "select": self.RELEASE_SELECT,
        })
        return self._single_release(query, "production release metadata")

    def get_release_by_run_id(self, release_run_id: str) -> dict[str, Any] | None:
        query = urllib.parse.urlencode({
            "channel": f"eq.{CHANNEL}",
            "notes": f"eq.{release_notes(release_run_id)}",
            "select": self.RELEASE_SELECT,
        })
        return self._single_release(query, "production release correlation")

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
            raise PublisherError("Unexpected latest production metadata response.")
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
    plan = plan_next_release(client, args.release_run_id, args.version_name)
    print(json.dumps(plan, separators=(",", ":")))
    return 0


def command_publish(args: argparse.Namespace) -> int:
    apk_path = Path(args.apk)
    spec = build_spec(apk_path, args.version_code, args.version_name, args.release_run_id)
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
    result = argparse.ArgumentParser(description="Protected MyFinHub production release publisher.")
    result.add_argument("--project-url", default=os.environ.get("SUPABASE_URL", f"https://{EXPECTED_PROJECT_HOST}"))
    result.add_argument("--secret-key", default=os.environ.get("SUPABASE_RELEASE_PUBLISH_KEY", ""), help=argparse.SUPPRESS)
    result.add_argument("--release-run-id", default=os.environ.get("GITHUB_RUN_ID", ""), help=argparse.SUPPRESS)
    sub = result.add_subparsers(dest="command", required=True)
    plan = sub.add_parser("plan")
    plan.add_argument("--version-name", required=True)
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
        print(f"production release publisher failed: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
