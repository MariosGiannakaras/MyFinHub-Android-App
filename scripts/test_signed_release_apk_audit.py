from __future__ import annotations
import tempfile
import unittest
import zipfile
from pathlib import Path
from audit_signed_release_apk import AuditError, checksum, certificate, compare, identity, inspect_zip

class PublishedApkAuditTest(unittest.TestCase):
    def test_checksum_exact_filename_and_duplicate(self):
        with tempfile.TemporaryDirectory() as d:
            file=Path(d)/"SHA256SUMS.txt"
            line="a"*64+"  signed.apk\n"
            file.write_text(line)
            self.assertEqual("a"*64,checksum(file,"signed.apk"))
            with self.assertRaises(AuditError): checksum(file,"other.apk")
            file.write_text(line+line)
            with self.assertRaises(AuditError): checksum(file,"signed.apk")

    def test_identity_and_certificate(self):
        self.assertEqual(("app.myfinhub.android",10016,"1.0.0-rc17"),identity("package: name='app.myfinhub.android' versionCode='10016' versionName='1.0.0-rc17'"))
        self.assertEqual("f"*64,certificate("Signer #1 certificate SHA-256 digest: "+"F"*64))
        with self.assertRaises(AuditError): certificate("none")

    def test_zip_validation(self):
        with tempfile.TemporaryDirectory() as d:
            path=Path(d)/"app.apk"
            with zipfile.ZipFile(path,"w") as archive:
                archive.writestr("AndroidManifest.xml",b"x")
                archive.writestr("classes.dex",b"y")
            self.assertEqual(["classes.dex"],inspect_zip(path)["dex_entries"])
            with zipfile.ZipFile(path,"w") as archive:
                archive.writestr("AndroidManifest.xml",b"x")
                archive.writestr("../outside",b"y")
            with self.assertRaises(AuditError): inspect_zip(path)

    def test_compare_refuses_signer_change_and_downgrade(self):
        a={"signer_sha256":"a"*64,"version_code":10015,"permissions":["INTERNET"]}
        b={"signer_sha256":"a"*64,"version_code":10016,"permissions":["INTERNET","REQUEST_INSTALL_PACKAGES"]}
        self.assertEqual(["REQUEST_INSTALL_PACKAGES"],compare(a,b)["permissions_added"])
        with self.assertRaises(AuditError): compare(a,{**b,"signer_sha256":"b"*64})
        with self.assertRaises(AuditError): compare(b,a)

if __name__ == "__main__":
    unittest.main()
