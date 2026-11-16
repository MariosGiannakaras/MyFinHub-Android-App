#!/usr/bin/env python3
"""Read-only static audit of signed published MyFinHub Android APKs. No installs."""
import argparse
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

RELEASES = {
    "rc16": ("1.0.0-rc16", 10015, "199a263c6dadc67ec2c40b14671a6543b6a13e15d53425b14992d2e4fc2c2556"),
    "rc17": ("1.0.0-rc17", 10016, "b17d0963ad50aa761c52d130dd7aa61a89b86a940b7dfa223f2d5433ce51f117"),
}
DISALLOWED = {
    "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS", "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS", "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
}


class AuditError(Exception):
    pass


def sha256(path):
    h = hashlib.sha256()
    with path.open("rb") as f:
        for b in iter(lambda: f.read(1024 * 1024), b""):
            h.update(b)
    return h.hexdigest()


def validate_zip(path):
    with path.open("rb") as f:
        if f.read(4) != b"PK\x03\x04":
            raise AuditError("APK has a nonstandard ZIP prefix")
    try:
        with zipfile.ZipFile(path) as z:
            members = z.infolist()
            names = [m.filename for m in members]
            if not names or len(names) != len(set(names)) or len(names) > 30000:
                raise AuditError("APK ZIP has duplicate/excessive/empty entries")
            for m in members:
                parts = Path(m.filename.replace("\\", "/")).parts
                if m.filename.startswith("/") or ".." in parts or m.flag_bits & 1 or m.file_size > 400 * 1024 * 1024:
                    raise AuditError("APK ZIP contains an unsafe entry")
            if "AndroidManifest.xml" not in names or not any(re.fullmatch(r"classes[0-9]*\.dex", n) for n in names):
                raise AuditError("APK lacks manifest or classes.dex")
            bad = z.testzip()
            if bad is not None:
                raise AuditError("APK ZIP CRC failed")
            return len(names), sum(bool(re.fullmatch(r"classes[0-9]*\.dex", n)) for n in names)
    except (zipfile.BadZipFile, OSError, RuntimeError) as exc:
        raise AuditError("APK is not an intact ZIP archive") from exc


def metadata_check(path, meta, release):
    name, code, pinned = release
    data = json.loads(meta.read_text(encoding="utf-8"))
    digest = sha256(path)
    if path.stat().st_size != data.get("direct_apk_size_bytes"):
        raise AuditError("APK size mismatches release metadata")
    if digest != pinned or digest != data.get("direct_apk_sha256"):
        raise AuditError("APK SHA-256 mismatches pinned digest or release metadata")
    if data.get("version_name") != name or data.get("version_code") != code:
        raise AuditError("Release metadata version mismatch")
    signer = data.get("signer_certificate_sha256")
    if not isinstance(signer, str) or not re.fullmatch("[0-9a-fA-F]{64}", signer):
        raise AuditError("No valid signed release fingerprint in metadata")
    return digest, signer.lower()


def run(*args):
    try:
        result = subprocess.run(args, check=True, capture_output=True, text=True, timeout=90)
        return result.stdout + "\n" + result.stderr
    except (OSError, subprocess.CalledProcessError, subprocess.TimeoutExpired) as exc:
        raise AuditError("Android tool failed: " + args[0]) from exc


def signer_digests(output):
    """Handle both Android V3.0 Signer and older Signer #1 apksigner labels."""
    return [value.lower() for value in re.findall(
        r"certificate SHA-256 digest:\s*([a-fA-F0-9]{64})", output, re.IGNORECASE)]


def apk_check(path, release, expected_signer, aapt, apksigner):
    name, code, _ = release
    badging = run(aapt, "dump", "badging", str(path))
    match = re.search(r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'", badging)
    if not match or match.groups() != ("app.myfinhub.android", str(code), name):
        raise AuditError("APK package/version does not match pinned release")
    dump = run(aapt, "dump", "permissions", str(path))
    perms = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", dump))
    if not perms:
        raise AuditError("Unable to inspect APK permissions")
    unexpected = perms & DISALLOWED
    if unexpected:
        raise AuditError("Unexpected dangerous APK permissions: " + ", ".join(sorted(unexpected)))
    output = run(apksigner, "verify", "--verbose", "--print-certs", str(path))
    # apksigner may print the same certificate for multiple signing schemes;
    # duplicate identical digests are not multiple different signing identities.
    signers = signer_digests(output)
    if not signers or set(signers) != {expected_signer}:
        labels = [re.sub(r"([a-fA-F0-9]{64})", "<redacted-digest>", line.strip())
                  for line in output.splitlines() if "SHA-256" in line]
        raise AuditError(
            "APK signer parse/certificate mismatch: parsed="
            + str(len(signers)) + ", unique=" + str(len(set(signers)))
            + ", metadata_match=" + str(expected_signer in signers)
            + ", verifier_labels=" + str(labels[:5])
        )
    return expected_signer, sorted(perms)


def inspect(kind, apk, meta, aapt, apksigner):
    try:
        release = RELEASES[kind]
        digest, expected_signer = metadata_check(apk, meta, release)
        entries, dex = validate_zip(apk)
        signer, perms = apk_check(apk, release, expected_signer, aapt, apksigner)
        return {"digest": digest, "signer": signer, "perms": perms, "size": apk.stat().st_size,
                "entries": entries, "dex": dex, "version": release[0]}
    except AuditError as exc:
        raise AuditError(kind + ": " + str(exc)) from exc


def report(a, b):
    if a["signer"] != b["signer"]:
        raise AuditError("rc16 and rc17 are signed by different certificates")
    added = sorted(set(b["perms"]) - set(a["perms"]))
    removed = sorted(set(a["perms"]) - set(b["perms"]))
    lines = [
        "# Published MyFinHub APK audit — rc16 versus rc17", "",
        "Read-only static checks passed for public GitHub Release APK bytes.", "",
        "| Check | rc16 | rc17 |", "|---|---|---|",
    ]
    for label, key in [("Version", "version"), ("Size (bytes)", "size"), ("ZIP entries", "entries"), ("DEX files", "dex")]:
        lines.append(f"| {label} | {a[key]} | {b[key]} |")
    lines += ["", "- Identical enrolled signer: " + a["signer"],
              "- rc16 SHA-256: " + a["digest"], "- rc17 SHA-256: " + b["digest"],
              "- Added permissions: " + (", ".join(added) if added else "none"),
              "- Removed permissions: " + (", ".join(removed) if removed else "none"),
              "- rc17 APK permissions: " + ", ".join(b["perms"]), "",
              "These checks cannot determine malware absence, SDK runtime behavior, Play Protect classification "
              "or physical Samsung S24 safety. The inspected APKs predate the later permissions hardening. "
              "Do not bypass the warning; Google review and owner acceptance remain independent.", ""]
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser()
    for key in ("rc16", "rc17"):
        p.add_argument("--" + key + "-apk", type=Path, required=True)
        p.add_argument("--" + key + "-metadata", type=Path, required=True)
    p.add_argument("--aapt", required=True)
    p.add_argument("--apksigner", required=True)
    p.add_argument("--report", type=Path, required=True)
    args = p.parse_args()
    try:
        older = inspect("rc16", args.rc16_apk, args.rc16_metadata, args.aapt, args.apksigner)
        newer = inspect("rc17", args.rc17_apk, args.rc17_metadata, args.aapt, args.apksigner)
        output = report(older, newer)
        args.report.write_text(output, encoding="utf-8")
        print(output)
        return 0
    except (AuditError, ValueError, json.JSONDecodeError, OSError) as exc:
        print("Published APK audit FAILED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
