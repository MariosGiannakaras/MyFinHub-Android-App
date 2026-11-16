#!/usr/bin/env python3
"""Read-only published APK integrity, ZIP, manifest and signing comparison."""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path


class AuditError(ValueError):
    pass


def digest(path: Path) -> str:
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1048576), b""):
            result.update(chunk)
    return result.hexdigest()


def checksum(path: Path, filename: str) -> str:
    hashes = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        m = re.fullmatch(r"([a-fA-F0-9]{64})\s+\*?([^/\\]+)", line)
        if not m or m.group(2) in hashes:
            raise AuditError("Invalid or duplicate checksum entry")
        hashes[m.group(2)] = m.group(1).lower()
    if filename not in hashes:
        raise AuditError("Published SHA256SUMS is missing the requested APK")
    return hashes[filename]


def inspect_zip(path: Path) -> dict:
    with path.open("rb") as stream:
        if stream.read(4) != b"PK\x03\x04":
            raise AuditError("Nonstandard ZIP header")
    with zipfile.ZipFile(path) as archive:
        info = archive.infolist()
        names = [x.filename for x in info]
        if len(names) != len(set(names)) or "AndroidManifest.xml" not in names:
            raise AuditError("Duplicate ZIP entries or missing manifest")
        if any(n.startswith("/") or "\\" in n or ".." in n.split("/") for n in names):
            raise AuditError("Unsafe ZIP entries")
        if any(x.flag_bits & 1 for x in info) or archive.testzip() is not None:
            raise AuditError("Encrypted or corrupt APK entries")
        return {
            "zip_entries": len(names),
            "dex_entries": sorted(n for n in names if re.fullmatch(r"classes\d*\.dex", n)),
            "native_libraries": sorted(n for n in names if n.startswith("lib/") and n.endswith(".so")),
        }


def tool(*args: str) -> str:
    p = subprocess.run(args, text=True, capture_output=True, check=False)
    if p.returncode:
        raise AuditError(f"Tool {Path(args[0]).name} rejected APK: {p.stderr[-250:]}")
    return p.stdout


def identity(raw: str) -> tuple[str, int, str]:
    m = re.search(r"^package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", raw, re.MULTILINE)
    if m is None:
        raise AuditError("APK package/version not parseable")
    return m.group(1), int(m.group(2)), m.group(3)


def certificate(raw: str) -> str:
    fingerprints = set(re.findall(r"certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", raw, re.IGNORECASE))
    if len(fingerprints) != 1:
        raise AuditError("Expected one APK signing certificate")
    return fingerprints.pop().lower()


def analyze(directory: Path, tag: str, aapt: str, apksigner: str) -> dict:
    files = list(directory.glob("*.apk"))
    if len(files) != 1:
        raise AuditError(f"{tag}: expected exactly one APK")
    apk = files[0]
    if not re.fullmatch(r"MyFinHub-1\.0\.0-rc\d+-\d+\.apk", apk.name):
        raise AuditError(f"{tag}: unexpected APK name")
    metadata = json.loads((directory / "release-metadata.json").read_text(encoding="utf-8"))
    if not isinstance(metadata, dict):
        raise AuditError(f"{tag}: invalid metadata")
    sha = digest(apk)
    if sha != checksum(directory / "SHA256SUMS.txt", apk.name) or sha != str(metadata.get("direct_apk_sha256", "")).lower():
        raise AuditError(f"{tag}: APK digest mismatch with published sidecars")
    if apk.stat().st_size != metadata.get("direct_apk_size_bytes"):
        raise AuditError(f"{tag}: size mismatch")
    zipinfo = inspect_zip(apk)
    pkg, code, name = identity(tool(aapt, "dump", "badging", str(apk)))
    if pkg != "app.myfinhub.android" or name != tag.removeprefix("v") or code != metadata.get("version_code") or name != metadata.get("version_name"):
        raise AuditError(f"{tag}: package/version mismatch")
    signer = certificate(tool(apksigner, "verify", "--verbose", "--print-certs", str(apk)))
    if signer != str(metadata.get("signer_certificate_sha256", "")).lower():
        raise AuditError(f"{tag}: APK signing certificate mismatch")
    permissions = sorted(set(re.findall(r"^uses-permission(?:-sdk-23)?: name='([^']+)'", tool(aapt, "dump", "permissions", str(apk)), re.MULTILINE)))
    return {"tag":tag,"name":name,"package":pkg,"version_code":code,"size":apk.stat().st_size,
            "sha256":sha,"signer_sha256":signer,"permissions":permissions,**zipinfo}


def compare(left: dict, right: dict) -> dict:
    if left["signer_sha256"] != right["signer_sha256"]:
        raise AuditError("Signer changed between published APKs")
    if right["version_code"] <= left["version_code"]:
        raise AuditError("Version code must increase")
    return {
        "rc16":left,"rc17":right,
        "permissions_added":sorted(set(right["permissions"])-set(left["permissions"])),
        "permissions_removed":sorted(set(left["permissions"])-set(right["permissions"])),
        "same_signer":True,
        "warning":"This is NOT a malware scan or Google Play Protect classification."
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    for field in ("baseline","candidate","aapt","apksigner"):
        parser.add_argument("--"+field, required=True)
    args = parser.parse_args()
    try:
        result = compare(analyze(Path(args.baseline),"v1.0.0-rc16",args.aapt,args.apksigner),
                         analyze(Path(args.candidate),"v1.0.0-rc17",args.aapt,args.apksigner))
    except (AuditError, OSError, ValueError, zipfile.BadZipFile) as exc:
        print(f"Read-only APK audit failed: {exc}",file=sys.stderr)
        return 2
    print(json.dumps(result,indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
