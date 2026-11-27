import hashlib
import json
import sys
import tempfile
import unittest
from unittest.mock import patch
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_play_protect_apk import (AuditError, RELEASES, UNATTENDED_INSTALL_PERMISSION, apk_check,
                                   metadata_check, report, validate_zip, signer_digests, zip_diff,
                                   receipt_provider_check)


class PublishedApkAuditTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def apk(self, prefix=b"", traversal=False):
        file = self.root / "mock.apk"
        with zipfile.ZipFile(file, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("AndroidManifest.xml", "x")
            z.writestr("classes.dex", "d")
            if traversal:
                z.writestr("../escape", "bad")
        if prefix:
            file.write_bytes(prefix + file.read_bytes())
        return file

    def test_v3_apksigner_certificate_line(self):
        value = "a" * 64
        self.assertEqual([value], signer_digests(
            "V3.0 Signer: certificate SHA-256 digest: " + value + "\n"
            + "V3.0 Signer: public key SHA-256 digest: " + "b" * 64))

    def test_legacy_apksigner_certificate_line(self):
        value = "c" * 64
        self.assertEqual([value], signer_digests(
            "Signer #1 certificate SHA-256 digest: " + value))

    def test_valid_archive(self):
        self.assertEqual(1, validate_zip(self.apk())[1])

    def test_nonstandard_prefix_rejected(self):
        with self.assertRaises(AuditError):
            validate_zip(self.apk(prefix=b"BAD"))

    def test_traversal_rejected(self):
        with self.assertRaises(AuditError):
            validate_zip(self.apk(traversal=True))

    def test_release_metadata_rejects_changed_bytes(self):
        file = self.apk()
        digest = hashlib.sha256(file.read_bytes()).hexdigest()
        metadata = self.root / "metadata.json"
        metadata.write_text(json.dumps({
            "version_name": "1.0.0-rc16", "version_code": 10015,
            "direct_apk_size_bytes": file.stat().st_size, "direct_apk_sha256": digest,
            "signer_certificate_sha256": "a" * 64,
        }))
        release = ("1.0.0-rc16", 10015, digest)
        self.assertEqual(digest, metadata_check(file, metadata, release)[0])
        file.write_bytes(file.read_bytes() + b"tampered")
        with self.assertRaises(AuditError):
            metadata_check(file, metadata, release)

    def test_zip_diff_detects_changed_dex_without_executing_it(self):
        old = self.apk()
        new = self.root / "new.apk"
        with zipfile.ZipFile(new, "w", zipfile.ZIP_DEFLATED) as z:
            z.writestr("AndroidManifest.xml", "x")
            z.writestr("classes.dex", "different-dex")
        difference = zip_diff(old, new)
        self.assertEqual(["classes.dex"], difference["changed"])
        self.assertEqual([], difference["added"])
        self.assertEqual([], difference["removed"])

    def test_hardened_release_rejects_unattended_install_permission(self):
        for label in ("rc18", "rc20", "rc21", "rc22", "rc23", "rc24", "rc25"):
            name, code, _ = RELEASES[label]
            badging = f"package: name='app.myfinhub.android' versionCode='{code}' versionName='{name}'"
            permissions = f"uses-permission: name='{UNATTENDED_INSTALL_PERMISSION}'"
            with self.subTest(label=label), patch("audit_play_protect_apk.run", side_effect=[badging, permissions]):
                with self.assertRaisesRegex(AuditError, "Unexpected dangerous APK permissions"):
                    apk_check(self.apk(), RELEASES[label], "a" * 64, "aapt", "apksigner")

    def test_historical_rc17_retains_permission_evidence(self):
        name, code, _ = RELEASES["rc17"]
        badging = f"package: name='app.myfinhub.android' versionCode='{code}' versionName='{name}'"
        permissions = f"uses-permission: name='{UNATTENDED_INSTALL_PERMISSION}'"
        signer = "a" * 64
        with patch("audit_play_protect_apk.run", side_effect=[
            badging, permissions, "Signer #1 certificate SHA-256 digest: " + signer,
        ]):
            self.assertEqual((signer, [UNATTENDED_INSTALL_PERMISSION]),
                             apk_check(self.apk(), RELEASES["rc17"], signer, "aapt", "apksigner"))

    def test_report_labels_actual_compared_releases(self):
        a = {"signer": "a" * 64, "perms": [], "version": "1.0.0-rc18", "size": 1,
             "entries": 2, "dex": 1, "digest": "a" * 64}
        b = {**a, "version": "1.0.0-rc22", "digest": "b" * 64}
        text = report(a, b)
        self.assertIn("rc18 versus rc22", text)
        self.assertIn("rc22 SHA-256: " + "b" * 64, text)
        self.assertIn("cannot determine malware absence", text)

    def test_receipt_provider_is_private_and_grant_scoped(self):
        xml = """    E: provider (line=42)
      A: android:name(0x01010003)="androidx.core.content.FileProvider" (Raw: "androidx.core.content.FileProvider")
      A: android:authorities(0x01010018)="app.myfinhub.android.receipt" (Raw: "app.myfinhub.android.receipt")
      A: android:exported(0x01010010)=(type 0x12)0x0
      A: android:grantUriPermissions(0x0101001b)=(type 0x12)0xffffffff
      E: meta-data (line=44)
        A: android:name(0x01010003)="android.support.FILE_PROVIDER_PATHS"
    E: receiver (line=48)
"""
        receipt_provider_check(xml)
        for invalid in (
            xml.replace("0x0\n", "0xffffffff\n"),
            xml.replace("0xffffffff", "0x0"),
            xml.replace(".receipt", ".wrong"),
            xml.replace("androidx.core.content.FileProvider", "untrusted.Provider"),
            xml + xml,
        ):
            with self.subTest(invalid=invalid), self.assertRaises(AuditError):
                receipt_provider_check(invalid)

    def test_rc23_permissions_must_match_rc22(self):
        a = {"signer": "a" * 64, "perms": ["android.permission.INTERNET"],
             "version": "1.0.0-rc22", "size": 1, "entries": 2,
             "dex": 1, "digest": "a" * 64}
        b = {**a, "version": "1.0.0-rc23"}
        self.assertIn("Added permissions: none", report(a, b))
        with self.assertRaisesRegex(AuditError, "permission set differs"):
            report(a, {**b, "perms": a["perms"] + ["android.permission.CAMERA"]})

    def test_rc24_permissions_and_executable_delta(self):
        a = {"signer": "a" * 64, "perms": ["android.permission.INTERNET"],
             "version": "1.0.0-rc23", "size": 1, "entries": 2,
             "dex": 1, "digest": "a" * 64}
        b = {**a, "version": "1.0.0-rc24"}
        diff = {"added": [], "removed": [], "changed": ["classes.dex"]}
        self.assertIn("Added permissions: none", report(a, b, diff))
        with self.assertRaisesRegex(AuditError, "permission set differs"):
            report(a, {**b, "perms": a["perms"] + ["android.permission.CAMERA"]}, diff)
        for unverified in (None, {**diff, "changed": ["AndroidManifest.xml"]}):
            with self.subTest(diff=unverified):
                with self.assertRaisesRegex(AuditError, "executable DEX delta"):
                    report(a, b, unverified)

    def test_rc25_only_approved_camera_permission_and_executable_delta(self):
        a = {"signer": "a" * 64, "perms": ["android.permission.INTERNET"],
             "version": "1.0.0-rc24", "size": 1, "entries": 2,
             "dex": 1, "digest": "a" * 64}
        b = {**a, "version": "1.0.0-rc25", "perms": a["perms"] + ["android.permission.CAMERA"]}
        diff = {"added": [], "removed": [], "changed": ["classes.dex"]}
        self.assertIn("Added permissions: android.permission.CAMERA", report(a, b, diff))
        for invalid in (a["perms"], b["perms"] + ["android.permission.RECORD_AUDIO"],
                        ["android.permission.CAMERA"]):
            with self.subTest(perms=invalid):
                with self.assertRaisesRegex(AuditError, "permission delta must be exactly CAMERA"):
                    report(a, {**b, "perms": invalid}, diff)
        for unverified in (None, {**diff, "changed": ["AndroidManifest.xml"]}):
            with self.subTest(diff=unverified):
                with self.assertRaisesRegex(AuditError, "executable DEX delta"):
                    report(a, b, unverified)

    def test_signer_continuity_required(self):
        a = {"signer": "a" * 64, "perms": [], "version": "rc16", "size": 1,
             "entries": 2, "dex": 1, "digest": "a" * 64}
        with self.assertRaises(AuditError):
            report(a, {**a, "signer": "b" * 64})


if __name__ == "__main__":
    unittest.main()
