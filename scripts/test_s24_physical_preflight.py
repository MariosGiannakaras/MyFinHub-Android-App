"""Synthetic, hermetic tests for the D7 read-only phone preflight."""
import contextlib
import io
import json
import subprocess
import unittest
from unittest.mock import patch

import s24_physical_preflight as preflight


class PhysicalS24PreflightTests(unittest.TestCase):
    def setUp(self):
        self.commands = []
        self.model = "SM-S928B"
        self.manufacturer = "samsung"
        self.installed = True
        self.version_name = "1.0.0-rc12"
        self.version_code = 10011
        self.error = None
        self.path_override = None
        self.dumpsys_override = None

    def run_adb(self, args, **kwargs):
        self.commands.append(args)
        self.assertEqual(args[:2], ["adb", "-d"])
        self.assertEqual(kwargs["timeout"], 15)
        self.assertFalse(kwargs["check"])
        safe_queries = {
            ("get-state",): "device",
            ("shell", "getprop", "ro.product.model"): self.model,
            ("shell", "getprop", "ro.product.manufacturer"): self.manufacturer,
            ("shell", "getprop", "ro.build.version.release"): "15",
            ("shell", "settings", "get", "system", "font_scale"): "1.2",
            ("shell", "pm", "path", preflight.PACKAGE):
                "package:/data/app/redacted/base.apk" if self.installed else "",
            ("shell", "dumpsys", "package", preflight.PACKAGE):
                f"Packages:\n  Package [{preflight.PACKAGE}]\n"
                f"    versionCode={self.version_code} minSdk=26 targetSdk=36\n"
                f"    versionName={self.version_name}\n"
                "    synthetic-SECRET-PAN=4111111111111111\n",
        }
        self.assertIn(tuple(args[2:]), safe_queries, "Unapproved ADB command")
        if self.path_override is not None:
            safe_queries[("shell", "pm", "path", preflight.PACKAGE)] = self.path_override
        if self.dumpsys_override is not None:
            safe_queries[("shell", "dumpsys", "package", preflight.PACKAGE)] = self.dumpsys_override
        if self.error is not None:
            return subprocess.CompletedProcess(
                args=args, returncode=1, stdout="", stderr=self.error,
            )
        return subprocess.CompletedProcess(
            args=args, returncode=0, stdout=safe_queries[tuple(args[2:])], stderr="",
        )

    def check(self):
        with patch.object(preflight.shutil, "which", return_value="/usr/bin/adb"):
            with patch.object(preflight.subprocess, "run", side_effect=self.run_adb):
                return preflight.inspect()

    def test_old_rc12_reports_insufficient_build_and_never_claims_acceptance(self):
        result = self.check()
        self.assertEqual(result["version_code"], 10011)
        self.assertEqual(result["candidate_check"], "older_than_published_rc13")
        self.assertEqual(result["physical_acceptance"], "not_assessed")
        exported = json.dumps(result)
        self.assertNotIn("SECRET", exported)
        self.assertNotIn("4111111111111111", exported)
        self.assertNotIn("/data/app", exported)
        self.assertTrue(all(cmd[:2] == ["adb", "-d"] for cmd in self.commands))
        self.assertFalse(any("install" in cmd or "pull" in cmd or "put" in cmd for cmd in self.commands))

    def test_published_rc13_version_alone_cannot_claim_signer_or_owner_acceptance(self):
        self.version_code = 10012
        self.version_name = "1.0.0-rc13"
        result = self.check()
        self.assertEqual(
            result["candidate_check"],
            "rc13_version_only_requires_signer_and_owner_verification",
        )
        self.assertEqual(result["physical_acceptance"], "not_assessed")

    def test_unrecognized_same_code_name_is_not_trusted_as_rc13(self):
        self.version_code = 10012
        self.version_name = "1.0.0-rc12"
        result = self.check()
        self.assertEqual(
            result["candidate_check"],
            "unrecognized_build_requires_source_and_signer_verification",
        )
        self.assertEqual(result["physical_acceptance"], "not_assessed")

    def test_future_version_still_requires_source_and_signer_verification(self):
        self.version_code = 10013
        self.version_name = "1.0.0-rc14"
        result = self.check()
        self.assertEqual(
            result["candidate_check"], "newer_build_requires_source_and_signer_verification",
        )
        self.assertEqual(result["physical_acceptance"], "not_assessed")

    def test_different_physical_phone_is_rejected_before_app_inspection(self):
        self.model = "SM-S926B"
        with self.assertRaisesRegex(preflight.PreflightError, "not the supported"):
            self.check()
        self.assertFalse(any("dumpsys" in cmd for cmd in self.commands))

    def test_non_samsung_identity_is_rejected(self):
        self.manufacturer = "other"
        with self.assertRaises(preflight.PreflightError):
            self.check()

    def test_not_installed_does_not_invent_version_or_acceptance(self):
        self.installed = False
        result = self.check()
        self.assertEqual(result["installation"], "not_found")
        self.assertEqual(result["candidate_check"], "not_installed")
        self.assertEqual(result["physical_acceptance"], "not_assessed")
        self.assertNotIn("version_code", result)

    def test_invalid_package_version_is_rejected(self):
        self.version_name = "bad version"
        with self.assertRaisesRegex(preflight.PreflightError, "not valid"):
            self.check()

    def test_package_version_is_scoped_to_exact_package_not_first_unrelated_version(self):
        self.dumpsys_override = (
            "Packages:\n  Package [com.unrelated.app]\n"
            "    versionCode=99999 minSdk=24\n    versionName=not-myfinhub\n"
            f"  Package [{preflight.PACKAGE}]\n"
            "    versionCode=10012 minSdk=26\n    versionName=1.0.0-rc13\n"
        )
        result = self.check()
        self.assertEqual(result["version_code"], 10012)
        self.assertEqual(
            result["candidate_check"],
            "rc13_version_only_requires_signer_and_owner_verification",
        )

    def test_missing_exact_package_section_fails_closed(self):
        self.dumpsys_override = (
            "Packages:\n  Package [com.unrelated.app]\n"
            "    versionCode=10013 minSdk=26\n    versionName=1.0.0-rc14\n"
        )
        with self.assertRaisesRegex(preflight.PreflightError, "identity"):
            self.check()

    def test_duplicate_exact_package_sections_fail_closed(self):
        section = (
            f"  Package [{preflight.PACKAGE}]\n"
            "    versionCode=10012 minSdk=26\n    versionName=1.0.0-rc13\n"
        )
        self.dumpsys_override = "Packages:\n" + section + section
        with self.assertRaisesRegex(preflight.PreflightError, "identity"):
            self.check()

    def test_duplicate_version_fields_fail_closed(self):
        self.dumpsys_override = (
            f"Packages:\n  Package [{preflight.PACKAGE}]\n"
            "    versionCode=10012 minSdk=26\n    versionCode=10013 minSdk=26\n"
            "    versionName=1.0.0-rc13\n"
        )
        with self.assertRaisesRegex(preflight.PreflightError, "not readable"):
            self.check()

    def test_malformed_pm_path_does_not_masquerade_as_not_installed(self):
        self.path_override = "Error: permission denied for package"
        with self.assertRaisesRegex(preflight.PreflightError, "path was not readable"):
            self.check()
        self.assertFalse(any("dumpsys" in cmd for cmd in self.commands))

    def test_adb_failure_never_leaks_raw_stderr_or_credential(self):
        self.error = "Bearer top-secret-credential"
        with self.assertRaises(preflight.PreflightError) as ctx:
            self.check()
        self.assertNotIn("top-secret", str(ctx.exception))

    def test_missing_adb_returns_private_data_free_error(self):
        with patch.object(preflight.shutil, "which", return_value=None):
            with self.assertRaisesRegex(preflight.PreflightError, "not installed"):
                preflight.inspect()

    def test_cli_json_output_only_contains_whitelisted_fields(self):
        sink = io.StringIO()
        with patch.object(preflight, "inspect", return_value={
            "version_name": "1.0.0-rc13",
            "physical_acceptance": "not_assessed",
        }):
            with contextlib.redirect_stdout(sink):
                self.assertEqual(preflight.main(["--json"]), 0)
        self.assertEqual(json.loads(sink.getvalue())["physical_acceptance"], "not_assessed")


if __name__ == "__main__":
    unittest.main()
