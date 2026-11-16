import hashlib
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from audit_play_protect_apk import AuditError, metadata_check, report, validate_zip


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

    def test_signer_continuity_required(self):
        a = {"signer": "a" * 64, "perms": [], "version": "rc16", "size": 1,
             "entries": 2, "dex": 1, "digest": "a" * 64}
        with self.assertRaises(AuditError):
            report(a, {**a, "signer": "b" * 64})


if __name__ == "__main__":
    unittest.main()
