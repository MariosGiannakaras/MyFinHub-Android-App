"""Synthetic fixtures for the read-only S24 package metadata preflight."""
import subprocess
import unittest

from s24_device_preflight import PreflightError, choose_device, collect, parse_installed_version

DUMP = """Packages:
  Package [app.myfinhub.android] (synthetic):
    userId=12345
    versionCode=10012 minSdk=26 targetSdk=36
    versionName=1.0.0-rc13
    signatures=not-inspected
"""


def runner_for(*, device_lines="List of devices attached\nsynthetic-id\tdevice\n",
               model="SM-S928B", manufacturer="samsung", version_dump=DUMP):
    responses = {
        ("adb", "devices"): device_lines,
        ("adb", "-s", "synthetic-id", "shell", "getprop", "ro.product.model"): model + "\n",
        ("adb", "-s", "synthetic-id", "shell", "getprop", "ro.product.manufacturer"): manufacturer + "\n",
        ("adb", "-s", "synthetic-id", "shell", "getprop", "ro.build.version.release"): "17\n",
        ("adb", "-s", "synthetic-id", "shell", "dumpsys", "package", "app.myfinhub.android"): version_dump,
    }

    def run(command, **kwargs):
        if tuple(command) not in responses:
            raise AssertionError("Unexpected ADB access: " + repr(command))
        return subprocess.CompletedProcess(command, 0, responses[tuple(command)], "")

    return run


class S24ReadOnlyPreflightTests(unittest.TestCase):
    def test_verified_metadata_is_not_physical_acceptance(self):
        report = collect("1.0.0-rc13", 10012, 10011, runner=runner_for())
        self.assertEqual(report["status"], "metadata_matches")
        self.assertEqual(report["physical_functional_acceptance"], "not_verified")
        self.assertEqual(report["signature_continuity"], "not_verified")
        self.assertEqual(report["in_place_upgrade"], "not_verified")
        self.assertNotIn("synthetic-id", repr(report))

    def test_rc12_cannot_qualify_as_new_develop_candidate(self):
        old = DUMP.replace("versionCode=10012", "versionCode=10011").replace("1.0.0-rc13", "1.0.0-rc12")
        report = collect("1.0.0-rc13", 10012, 10011, runner=runner_for(version_dump=old))
        self.assertEqual(report["status"], "blocked")
        self.assertFalse(report["checks"]["newer_than_previous_installed_code"])
        with self.assertRaises(PreflightError):
            collect("1.0.0-rc12", 10011, 10011, runner=runner_for())

    def test_wrong_phone_or_wrong_package_version_is_blocked(self):
        report = collect("1.0.0-rc13", 10012, 10011, runner=runner_for(model="SM-S918B"))
        self.assertFalse(report["checks"]["physical_s24_ultra_model"])
        report = collect("1.0.0-rc13", 10012, 10011, runner=runner_for(manufacturer="other"))
        self.assertFalse(report["checks"]["physical_s24_ultra_model"])
        report = collect("1.0.0-rc14", 10012, 10011, runner=runner_for())
        self.assertFalse(report["checks"]["exact_expected_version"])

    def test_package_parser_does_not_accept_other_app_or_package_changes(self):
        with self.assertRaises(PreflightError):
            parse_installed_version(DUMP.replace("[app.myfinhub.android]", "[other.package]"))
        with self.assertRaises(PreflightError):
            parse_installed_version("Package changes: app.myfinhub.android versionCode=10012 versionName=1.0.0-rc13")
        with self.assertRaises(PreflightError):
            parse_installed_version(DUMP.replace("versionName=1.0.0-rc13", "notAVersionField=1.0.0-rc13"))

    def test_one_connected_authorized_device_required_and_no_serial_in_report(self):
        with self.assertRaises(PreflightError):
            choose_device("List of devices attached\na\tunauthorized\n", None)
        with self.assertRaises(PreflightError):
            choose_device("List of devices attached\na\tdevice\nb\tdevice\n", None)
        self.assertEqual(choose_device("List of devices attached\na\tdevice\nb\tdevice\n", "b"), "b")
        with self.assertRaises(PreflightError):
            choose_device("List of devices attached\na\tdevice\n", "b")


if __name__ == "__main__":
    unittest.main()
