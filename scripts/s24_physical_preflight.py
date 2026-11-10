#!/usr/bin/env python3
"""Read-only USB preflight for the owner's physical Galaxy S24 Ultra.

This script does NOT install/upgrade, change device settings, inspect app data,
retrieve APKs, authenticate, test finance writes, or certify physical acceptance.
Its output is intentionally limited to non-secret device/build metadata.
"""
from __future__ import annotations

import argparse
import json
import re
import shutil
import subprocess
import sys

PACKAGE = "app.myfinhub.android"
LAST_KNOWN_SIGNED_VERSION_CODE = 10011  # rc12, released 2026-10-14
MODEL_PATTERN = re.compile(r"SM-S928[A-Z0-9]*\Z")
VERSION_PATTERN = re.compile(r"[A-Za-z0-9._+-]{1,64}\Z")
ANDROID_PATTERN = re.compile(r"[A-Za-z0-9.]{1,32}\Z")
PACKAGE_HEADER = re.compile(r"(?m)^[ \t]*Package \[([^\]\r\n]+)\]")


class PreflightError(Exception):
    """Intentional non-sensitive error; never propagate raw adb output."""


def _adb(*args: str) -> str:
    # Every command is read-only and addresses a single physical USB device.
    # -d fails closed if no USB phone (or multiple physical phones) is connected.
    try:
        result = subprocess.run(
            ["adb", "-d", *args],
            text=True,
            capture_output=True,
            timeout=15,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired) as exc:
        raise PreflightError("ADB unavailable or device query timed out.") from None
    if result.returncode != 0:
        raise PreflightError("ADB query failed. Connect and authorize one physical USB phone.")
    return result.stdout.strip()



def _installed_version(info: str) -> tuple[int, str]:
    """Read exactly one version from the exact package section of dumpsys."""
    headers = list(PACKAGE_HEADER.finditer(info))
    owners = [i for i, header in enumerate(headers) if header.group(1) == PACKAGE]
    if len(owners) != 1:
        raise PreflightError("Installed package identity was not readable.")

    index = owners[0]
    end = headers[index + 1].start() if index + 1 < len(headers) else len(info)
    section = info[headers[index].end():end]
    codes = re.findall(r"(?m)^[ \t]*versionCode=([0-9]+)(?=\s|$)", section)
    names = re.findall(r"(?m)^[ \t]*versionName=([^\r\n]*)$", section)
    if len(codes) != 1 or len(names) != 1 or len(codes[0]) > 18:
        raise PreflightError("Installed package version was not readable.")

    code = int(codes[0])
    name = names[0].strip()
    if code <= 0 or VERSION_PATTERN.fullmatch(name) is None:
        raise PreflightError("Installed package version was not valid.")
    return code, name


def inspect(expected_version_code: int | None = None) -> dict[str, object]:
    if expected_version_code is not None and (
        isinstance(expected_version_code, bool)
        or not isinstance(expected_version_code, int)
        or expected_version_code <= LAST_KNOWN_SIGNED_VERSION_CODE
        or len(str(expected_version_code)) > 18
    ):
        raise PreflightError("Expected candidate version code must be newer than rc12.")

    if shutil.which("adb") is None:
        raise PreflightError("Android platform-tools (adb) are not installed.")

    if _adb("get-state") != "device":
        raise PreflightError("One authorized physical USB device is required.")

    model = _adb("shell", "getprop", "ro.product.model")
    manufacturer = _adb("shell", "getprop", "ro.product.manufacturer").lower()
    if manufacturer != "samsung" or MODEL_PATTERN.fullmatch(model) is None:
        raise PreflightError("Connected device is not the supported Samsung SM-S928 family.")

    release = _adb("shell", "getprop", "ro.build.version.release")
    if ANDROID_PATTERN.fullmatch(release) is None:
        raise PreflightError("Android release could not be validated.")

    scale_text = _adb("shell", "settings", "get", "system", "font_scale")
    try:
        font_scale = float(scale_text)
    except ValueError:
        font_scale = None
    if font_scale is not None and not (0.5 <= font_scale <= 3.0):
        font_scale = None

    installed_path = _adb("shell", "pm", "path", PACKAGE)
    if not installed_path:
        result: dict[str, object] = {
            "supported_device": "Samsung Galaxy S24 Ultra (SM-S928 family)",
            "android_release": release,
            "font_scale": font_scale,
            "package": PACKAGE,
            "installation": "not_found",
            "candidate_check": "not_installed",
            "physical_acceptance": "not_assessed",
        }
        if expected_version_code is not None:
            result["expected_version_code"] = expected_version_code
            result["candidate_version_matches_expected"] = False
        return result

    if not all(re.fullmatch(r"package:/[^\s]+\.apk", line) for line in installed_path.splitlines()):
        raise PreflightError("Installed package path was not readable.")

    info = _adb("shell", "dumpsys", "package", PACKAGE)
    version_code, version_name = _installed_version(info)

    # A newer code alone does NOT establish trusted signer, source provenance,
    # safe in-place upgrade, or product/owner acceptance.
    freshness = (
        "older_or_equal_to_rc12"
        if version_code <= LAST_KNOWN_SIGNED_VERSION_CODE
        else "newer_build_requires_source_and_signer_verification"
    )
    result: dict[str, object] = {
        "supported_device": "Samsung Galaxy S24 Ultra (SM-S928 family)",
        "android_release": release,
        "font_scale": font_scale,
        "package": PACKAGE,
        "installation": "installed",
        "version_name": version_name,
        "version_code": version_code,
        "candidate_check": freshness,
        "physical_acceptance": "not_assessed",
    }
    if expected_version_code is not None:
        result["expected_version_code"] = expected_version_code
        result["candidate_version_matches_expected"] = (
            version_code == expected_version_code
        )
    return result


def _expected_code_arg(raw: str) -> int:
    if re.fullmatch(r"[0-9]{1,18}", raw) is None:
        raise argparse.ArgumentTypeError("Expected version code must be numeric.")
    code = int(raw)
    if code <= LAST_KNOWN_SIGNED_VERSION_CODE:
        raise argparse.ArgumentTypeError(
            "D7 candidate must be newer than the already published rc12."
        )
    return code


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Print only privacy-safe JSON")
    parser.add_argument(
        "--expected-version-code",
        type=_expected_code_arg,
        metavar="CODE",
        help="Fail closed unless the installed package matches a newer signed candidate code.",
    )
    args = parser.parse_args(argv)

    try:
        result = inspect(expected_version_code=args.expected_version_code)
    except PreflightError as exc:
        print(f"D7 preflight blocked: {exc}", file=sys.stderr)
        return 2

    if args.expected_version_code is not None and not result.get(
        "candidate_version_matches_expected", False
    ):
        print(
            "D7 preflight blocked: installed package does not match the expected candidate.",
            file=sys.stderr,
        )
        return 2

    if args.json:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    else:
        print("D7 physical S24 read-only preflight (NOT owner acceptance)")
        for key, value in result.items():
            print(f"{key}: {value}")
        print("The installed signer, release source, in-place data continuity,")
        print("and all real-device user journeys require separate owner verification.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
