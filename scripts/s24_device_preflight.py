#!/usr/bin/env python3
"""Read-only, privacy-minimized package metadata preflight for the owner's S24 Ultra.

This does NOT install, modify, sign or approve an Android build. Never print
ADB device serials, private files, raw dumpsys output or account information.
"""
import argparse
import json
import re
import subprocess
import sys

PACKAGE = "app.myfinhub.android"


class PreflightError(RuntimeError):
    pass


def adb_output(args, serial=None, runner=subprocess.run):
    command = ["adb"] + (["-s", serial] if serial else []) + list(args)
    try:
        result = runner(command, capture_output=True, text=True, timeout=25, check=False)
    except (FileNotFoundError, subprocess.TimeoutExpired, OSError) as exc:
        raise PreflightError("ADB is unavailable or timed out; verify Android SDK and USB authorization.") from exc
    if result.returncode != 0:
        # ADB stderr often contains device serials or system details: never expose it.
        raise PreflightError("ADB read failed; verify USB debugging, device connection and authorization.")
    return result.stdout


def choose_device(devices_output, serial):
    connected = []
    for line in devices_output.splitlines()[1:]:
        fields = line.split()
        if len(fields) >= 2 and fields[1] == "device":
            connected.append(fields[0])
    if serial is not None:
        if serial not in connected:
            raise PreflightError("The selected device is not connected and authorized.")
        return serial
    if len(connected) != 1:
        raise PreflightError("Connect exactly one authorized device or pass --serial explicitly.")
    return connected[0]


def parse_installed_version(dump):
    # Scope matching to the installed package block; unrelated package records
    # and historical package change entries must never satisfy this check.
    section = re.search(
        r"(?m)^\s*Package \[app\.myfinhub\.android\] \([^\n]*\):\s*$", dump
    )
    if section is None:
        raise PreflightError("MyFinHub package metadata is not available.")
    tail = dump[section.end():]
    next_package = re.search(r"(?m)^\s*Package \[[^\n]+\] \([^\n]*\):\s*$", tail)
    block = tail[:next_package.start()] if next_package else tail
    code = re.search(r"(?m)^\s*versionCode=(\d+)(?:\s|$)", block)
    name = re.search(r"(?m)^\s*versionName=([^\s]+)\s*$", block)
    if code is None or name is None:
        raise PreflightError("Installed MyFinHub version fields are incomplete.")
    return int(code.group(1)), name.group(1)


def collect(expected_name, expected_code, previous_code, serial=None, runner=subprocess.run):
    if expected_code <= previous_code or previous_code < 0:
        raise PreflightError("The expected versionCode must exceed the previously installed build.")
    device = choose_device(adb_output(["devices"], runner=runner), serial)
    model = adb_output(["shell", "getprop", "ro.product.model"], device, runner).strip()
    manufacturer = adb_output(["shell", "getprop", "ro.product.manufacturer"], device, runner).strip()
    android_release = adb_output(["shell", "getprop", "ro.build.version.release"], device, runner).strip()
    dump = adb_output(["shell", "dumpsys", "package", PACKAGE], device, runner)
    code, name = parse_installed_version(dump)
    checks = {
        "physical_s24_ultra_model": manufacturer.casefold() == "samsung" and model.upper().startswith("SM-S928"),
        "exact_expected_version": code == expected_code and name == expected_name,
        "newer_than_previous_installed_code": code > previous_code,
    }
    # Never label D7 accepted: signature continuity, in-place installation,
    # encrypted owner state, rendering and real app flows require owner evidence.
    return {
        "status": "metadata_matches" if all(checks.values()) else "blocked",
        "package": PACKAGE,
        "device_model": model,
        "android_release": android_release,
        "installed_version_name": name,
        "installed_version_code": code,
        "checks": checks,
        "signature_continuity": "not_verified",
        "in_place_upgrade": "not_verified",
        "account_and_vault_continuity": "not_verified",
        "physical_functional_acceptance": "not_verified",
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--expected-version-name", required=True)
    parser.add_argument("--expected-version-code", required=True, type=int)
    parser.add_argument("--previous-version-code", required=True, type=int)
    parser.add_argument("--serial", help="ADB selection only; never included in the report")
    args = parser.parse_args(argv)
    try:
        report = collect(
            expected_name=args.expected_version_name,
            expected_code=args.expected_version_code,
            previous_code=args.previous_version_code,
            serial=args.serial,
        )
    except PreflightError as error:
        print(json.dumps({"status": "blocked", "reason": str(error)}, ensure_ascii=False))
        return 1
    print(json.dumps(report, ensure_ascii=False, indent=2))
    return 0 if report["status"] == "metadata_matches" else 1


if __name__ == "__main__":
    sys.exit(main())
