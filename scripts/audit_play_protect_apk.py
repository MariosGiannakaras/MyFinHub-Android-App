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
    "rc18": ("1.0.0-rc18", 10017, "40360b5678225300c7cd8f8fe1dd1f727cbb14dee9bd5dde2f33ace329fbdc0d"),
    "rc20": ("1.0.0-rc20", 10019, "f64c3bc9c2e6972896ab1ca3429ea6ef6c29fdd1e2a58f4ab0cddc70f1b23f6f"),
    "rc21": ("1.0.0-rc21", 10020, "68859f5076dc33235ab6d34b01e2410c07f1d2245d3c6f56dfcb2f0f6c924bc4"),
    "rc22": ("1.0.0-rc22", 10021, "3e79ed72455e4b23b46ecc0c516cff3c9d731787d6c577c71272bd7e9ea5b41d"),
    "rc23": ("1.0.0-rc23", 10022, "3d4562188737d0281f0f2184eb4126e1b06c1183c2625243f940330b76b77b86"),
    "rc24": ("1.0.0-rc24", 10023, "6be67bbaf249c245b151c2abf417b4801806faa47e2e2ea35d303bd7c601b8a7"),
    "rc25": ("1.0.0-rc25", 10024, "273f23283cb758a43a771155e4397236859a75ccb0b4f044b8d546601363c038"),
    "rc26": ("1.0.0-rc26", 10025, "e8149dcd74f42892be66a0661613de831224fae8f89dad22ef21df30852bf7c5"),
    "rc27": ("1.0.0-rc27", 10026, "90d84b09c428c37134ffa58f16fa64fe778742ac877fea4b4fb912fc740435cb"),
}
HARDENED_RELEASES = ("rc18", "rc20", "rc21", "rc22", "rc23", "rc24", "rc25", "rc26", "rc27")
UNATTENDED_INSTALL_PERMISSION = "android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION"
DISALLOWED = {
    "android.permission.READ_SMS", "android.permission.RECEIVE_SMS",
    "android.permission.SEND_SMS", "android.permission.READ_CALL_LOG",
    "android.permission.READ_CONTACTS", "android.permission.QUERY_ALL_PACKAGES",
    "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.BIND_ACCESSIBILITY_SERVICE",
    "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
}

PRIVATE_SMS_PERMISSIONS = {
    "android.permission.ACCESS_NETWORK_STATE", "android.permission.CAMERA",
    "android.permission.INTERNET", "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_SMS", "android.permission.REQUEST_INSTALL_PACKAGES",
    "android.permission.USE_BIOMETRIC", "android.permission.USE_FINGERPRINT",
    "app.myfinhub.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
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


def receipt_provider_check(output):
    """Require the actual APK receipt FileProvider to be private and grant-scoped."""
    blocks = []
    current = None
    for line in output.splitlines():
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped.startswith("E: "):
            if current is not None and indent <= current[0]:
                blocks.append(current[1])
                current = None
            if stripped.startswith("E: provider "):
                current = (indent, [])
        elif current is not None and indent == current[0] + 2 and stripped.startswith("A: "):
            current[1].append(stripped)
    if current is not None:
        blocks.append(current[1])

    def attribute(block, name):
        return next((line for line in block if line.startswith("A: android:" + name + "(")), "")

    matches = [block for block in blocks if re.search(
        r'="app\.myfinhub\.android\.receipt"(?:\s|$)', attribute(block, "authorities"))]
    if len(matches) != 1:
        raise AuditError("Missing or ambiguous receipt FileProvider authority")
    block = matches[0]
    if not re.search(r'="androidx\.core\.content\.FileProvider"(?:\s|$)', attribute(block, "name")):
        raise AuditError("Unexpected receipt provider class")
    if not re.search(r'=\(type 0x12\)0x0+(?:\s|$)', attribute(block, "exported")):
        raise AuditError("Receipt FileProvider is not explicitly nonexported")
    if not re.search(r'=\(type 0x12\)0x(?:ffffffff|1)(?:\s|$)', attribute(block, "grantUriPermissions")):
        raise AuditError("Receipt FileProvider lacks explicit URI grants")


def bank_sms_receiver_check(output):
    """Require the SMS broadcast action, permission and exported flag on the same receiver."""
    receiver_blocks = []
    current = None
    for line in output.splitlines():
        stripped = line.lstrip()
        indent = len(line) - len(stripped)
        if stripped.startswith("E: "):
            if current is not None and indent <= current[0]:
                receiver_blocks.append(current)
                current = None
            if stripped.startswith("E: receiver "):
                current = (indent, [])
        if current is not None:
            current[1].append((indent, stripped))
    if current is not None:
        receiver_blocks.append(current)

    def attrs(block, depth, name):
        return [value for indent, value in block[1] if indent == block[0] + depth
                and value.startswith("A: android:" + name + "(")]

    def string_match(lines, value):
        return len(lines) == 1 and re.search(r'="' + re.escape(value) + r'"(?:\s|$)', lines[0])

    matches = [block for block in receiver_blocks if string_match(
        attrs(block, 2, "name"), "app.myfinhub.android.feature.bankalert.BankSmsReceiver")]
    if len(matches) != 1:
        raise AuditError("Missing or ambiguous bank SMS receiver")
    block = matches[0]
    if not string_match(attrs(block, 2, "permission"), "android.permission.BROADCAST_SMS"):
        raise AuditError("Bank SMS receiver is not protected by BROADCAST_SMS")
    exported = attrs(block, 2, "exported")
    if len(exported) != 1 or not re.search(r'=\(type 0x12\)0x(?:ffffffff|1)(?:\s|$)', exported[0]):
        raise AuditError("Bank SMS receiver is not explicitly exported for system delivery")
    actions = attrs(block, 6, "name")
    if not string_match(actions, "android.provider.Telephony.SMS_RECEIVED"):
        raise AuditError("Bank SMS receiver must handle only SMS_RECEIVED")
    if sum("android.provider.Telephony.SMS_RECEIVED" in value for candidate in receiver_blocks
           for _, value in candidate[1]) != 1:
        raise AuditError("Ambiguous or duplicate SMS_RECEIVED receiver action")


def apk_check(path, release, expected_signer, aapt, apksigner):
    name, code, _ = release
    badging = run(aapt, "dump", "badging", str(path))
    match = re.search(r"package:\s+name='([^']+)'\s+versionCode='(\d+)'\s+versionName='([^']+)'", badging)
    if not match or match.groups() != ("app.myfinhub.android", str(code), name):
        raise AuditError("APK package/version does not match pinned release")
    if name in {"1.0.0-rc26", "1.0.0-rc27"} and (not re.search(r"^sdkVersion:\x2726\x27$", badging, re.M) or not re.search(r"^targetSdkVersion:\x2737\x27$", badging, re.M)):
        raise AuditError(name + " APK SDK declaration mismatch")
    dump = run(aapt, "dump", "permissions", str(path))
    perms = set(re.findall(r"uses-permission(?:-sdk-\d+)?: name='([^']+)'", dump))
    if not perms:
        raise AuditError("Unable to inspect APK permissions")
    unexpected = perms & DISALLOWED
    if name == "1.0.0-rc27":
        unexpected.discard("android.permission.RECEIVE_SMS")  # Exact approved rc27 only.
    # rc16/rc17 are historical evidence and predate the removal. Every
    # inspected release after that security correction must lack this permission.
    if code >= RELEASES["rc18"][1] and UNATTENDED_INSTALL_PERMISSION in perms:
        unexpected.add(UNATTENDED_INSTALL_PERMISSION)
    if unexpected:
        raise AuditError("Unexpected dangerous APK permissions: " + ", ".join(sorted(unexpected)))
    if name == "1.0.0-rc27" and perms != PRIVATE_SMS_PERMISSIONS:
        raise AuditError("rc27 must have exactly the approved nine private APK permissions")
    if code >= RELEASES["rc23"][1]:
        manifest = run(aapt, "dump", "xmltree", str(path), "AndroidManifest.xml")
        receipt_provider_check(manifest)
        if name == "1.0.0-rc27":
            bank_sms_receiver_check(manifest)
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


def zip_diff(older, newer):
    def entries(path):
        with zipfile.ZipFile(path) as z:
            return {info.filename: (info.CRC, info.file_size) for info in z.infolist()}
    before, after = entries(older), entries(newer)
    names_old, names_new = set(before), set(after)
    return {
        "added": sorted(names_new - names_old),
        "removed": sorted(names_old - names_new),
        "changed": sorted(n for n in names_old & names_new if before[n] != after[n]),
    }


def report(a, b, diff=None):
    older_label = a["version"].rsplit("-", 1)[-1]
    newer_label = b["version"].rsplit("-", 1)[-1]
    if a["signer"] != b["signer"]:
        raise AuditError(f"{older_label} and {newer_label} are signed by different certificates")
    added = sorted(set(b["perms"]) - set(a["perms"]))
    removed = sorted(set(a["perms"]) - set(b["perms"]))
    if (older_label, newer_label) in {("rc22", "rc23"), ("rc23", "rc24"), ("rc25", "rc26")} and (added or removed):
        raise AuditError(f"{newer_label} permission set differs from {older_label}; investigate before acceptance")
    if (older_label, newer_label) == ("rc24", "rc25") and (added != ["android.permission.CAMERA"] or removed):
        raise AuditError("rc25 permission delta must be exactly CAMERA added and none removed")
    if (older_label, newer_label) == ("rc26", "rc27") and (added != ["android.permission.POST_NOTIFICATIONS", "android.permission.RECEIVE_SMS"] or removed):
        raise AuditError("rc27 permission delta must be exactly RECEIVE_SMS and POST_NOTIFICATIONS added and none removed")
    if (older_label, newer_label) in {("rc23", "rc24"), ("rc24", "rc25"), ("rc25", "rc26"), ("rc26", "rc27")}:
        if diff is None or not any(re.fullmatch(r"classes[0-9]*\.dex", name)
                                   for key in ("added", "removed", "changed") for name in diff[key]):
            raise AuditError(f"{newer_label} executable DEX delta not verified against {older_label}")
    lines = [
        f"# Published MyFinHub APK audit — {older_label} versus {newer_label}", "",
        "Read-only static checks passed for public GitHub Release APK bytes.", "",
        f"| Check | {older_label} | {newer_label} |", "|---|---|---|",
    ]
    for label, key in [("Version", "version"), ("Size (bytes)", "size"), ("ZIP entries", "entries"), ("DEX files", "dex")]:
        lines.append(f"| {label} | {a[key]} | {b[key]} |")
    if diff is not None:
        lines.extend([
            "",
            "- Added APK ZIP entries: " + (", ".join(diff["added"][:30]) if diff["added"] else "none"),
            "- Removed APK ZIP entries: " + (", ".join(diff["removed"][:30]) if diff["removed"] else "none"),
            "- Changed-entry count (CRC/size): " + str(len(diff["changed"])),
            "- Changed APK ZIP entries (up to 30): " +
            (", ".join(diff["changed"][:30]) if diff["changed"] else "none"),
        ])
    lines += ["", "- Identical enrolled signer: " + a["signer"],
              f"- {older_label} SHA-256: " + a["digest"], f"- {newer_label} SHA-256: " + b["digest"],
              "- Added permissions: " + (", ".join(added) if added else "none"),
              "- Removed permissions: " + (", ".join(removed) if removed else "none"),
              f"- {newer_label} APK permissions: " + ", ".join(b["perms"]), "",
              "These checks cannot determine malware absence, SDK runtime behavior, Play Protect classification "
              "or physical Samsung S24 safety. Historical rc16/rc17 predate the permissions hardening; "
              "Do not bypass the warning; Google review and owner acceptance remain independent.", ""]
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser()
    for key in RELEASES:
        required = key not in HARDENED_RELEASES
        p.add_argument("--" + key + "-apk", type=Path, required=required)
        p.add_argument("--" + key + "-metadata", type=Path, required=required)
    p.add_argument("--aapt", required=True)
    p.add_argument("--apksigner", required=True)
    p.add_argument("--report", type=Path, required=True)
    args = p.parse_args()
    try:
        optional_paths = [
            getattr(args, f"{key}_{suffix}")
            for key in HARDENED_RELEASES
            for suffix in ("apk", "metadata")
        ]
        if any(optional_paths) and not all(optional_paths):
            raise AuditError("Provide all rc18, rc20, rc21, rc22, rc23, rc24, rc25, rc26 and rc27 APK/metadata pairs together")
        keys = list(RELEASES) if all(optional_paths) else ["rc16", "rc17"]
        apks = {key: getattr(args, key + "_apk") for key in keys}
        results = {
            key: inspect(key, apks[key], getattr(args, key + "_metadata"), args.aapt, args.apksigner)
            for key in keys
        }
        pairs = [("rc16", "rc17")]
        if all(optional_paths):
            pairs += [("rc17", "rc18"), ("rc18", "rc20"), ("rc20", "rc21"), ("rc18", "rc21"), ("rc21", "rc22"), ("rc18", "rc22"), ("rc22", "rc23"), ("rc18", "rc23"), ("rc23", "rc24"), ("rc18", "rc24"), ("rc24", "rc25"), ("rc18", "rc25"), ("rc25", "rc26"), ("rc18", "rc26"), ("rc26", "rc27"), ("rc18", "rc27")]
        output = "\n".join(report(results[older], results[newer], zip_diff(apks[older], apks[newer]))
                           for older, newer in pairs)
        args.report.write_text(output, encoding="utf-8")
        print(output)
        return 0
    except (AuditError, ValueError, json.JSONDecodeError, OSError) as exc:
        print("Published APK audit FAILED: " + str(exc), file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
