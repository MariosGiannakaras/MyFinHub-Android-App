import hashlib
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

from scripts.phase6_release_publisher import (
    CHANNEL,
    NotFound,
    PublisherError,
    ReleaseSpec,
    RemoteWriteError,
    build_spec,
    publish_release,
)


def make_spec(data: bytes = b"apk", version_code: int = 6012) -> ReleaseSpec:
    return ReleaseSpec(
        version_code=version_code,
        version_name=f"0.1.0-phase6.{version_code - 6000}",
        storage_path=f"{CHANNEL}/{version_code}/MyFinHub-Phase6-{version_code}.apk",
        sha256=hashlib.sha256(data).hexdigest(),
        size_bytes=len(data),
    )


class FakeClient:
    def __init__(self, spec: ReleaseSpec, data: bytes) -> None:
        self.spec = spec
        self.data = data
        self.release = None
        self.object_data = None
        self.calls = []
        self.upload_error = False
        self.upload_materializes = False
        self.insert_error = False
        self.insert_materializes = False

    def get_release(self, version_code):
        self.calls.append(("get_release", version_code))
        return self.release

    def get_latest_version_code(self):
        self.calls.append(("get_latest",))
        return None

    def download_object(self, storage_path):
        self.calls.append(("download", storage_path))
        if self.object_data is None:
            raise NotFound("missing")
        return self.object_data

    def upload_object(self, storage_path, data):
        self.calls.append(("upload", storage_path))
        if self.upload_materializes:
            self.object_data = data
        if self.upload_error:
            raise RemoteWriteError("ambiguous")
        self.object_data = data

    def insert_release(self, metadata):
        self.calls.append(("insert", metadata["version_code"]))
        if self.insert_materializes:
            self.release = dict(metadata)
        if self.insert_error:
            raise RemoteWriteError("ambiguous")
        self.release = dict(metadata)


class PublisherFlowTest(unittest.TestCase):
    def test_fresh_publish_orders_upload_verify_then_metadata(self):
        data = b"fresh-apk"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        result = publish_release(client, spec, data)
        self.assertEqual("published", result)
        self.assertEqual(
            ["get_release", "download", "upload", "download", "insert", "get_release"],
            [call[0] for call in client.calls],
        )

    def test_existing_exact_object_skips_upload_and_publishes_metadata(self):
        data = b"resume-apk"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.object_data = data
        self.assertEqual("published", publish_release(client, spec, data))
        self.assertNotIn("upload", [call[0] for call in client.calls])

    def test_ambiguous_upload_reconciles_without_second_write(self):
        data = b"ambiguous-upload"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.upload_error = True
        client.upload_materializes = True
        self.assertEqual("published", publish_release(client, spec, data))
        self.assertEqual(1, [call[0] for call in client.calls].count("upload"))

    def test_ambiguous_upload_without_object_fails_without_metadata(self):
        data = b"missing-after-upload"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.upload_error = True
        with self.assertRaises(PublisherError):
            publish_release(client, spec, data)
        names = [call[0] for call in client.calls]
        self.assertEqual(1, names.count("upload"))
        self.assertNotIn("insert", names)

    def test_mismatched_existing_object_fails_before_metadata(self):
        data = b"expected"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.object_data = b"different"
        with self.assertRaises(PublisherError):
            publish_release(client, spec, data)
        names = [call[0] for call in client.calls]
        self.assertNotIn("upload", names)
        self.assertNotIn("insert", names)

    def test_ambiguous_metadata_insert_reconciles_without_second_insert(self):
        data = b"metadata-ambiguous"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.object_data = data
        client.insert_error = True
        client.insert_materializes = True
        self.assertEqual("published-after-reconciliation", publish_release(client, spec, data))
        self.assertEqual(1, [call[0] for call in client.calls].count("insert"))

    def test_already_published_exact_release_is_idempotent(self):
        data = b"already"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.object_data = data
        client.release = spec.metadata()
        self.assertEqual("already-published", publish_release(client, spec, data))
        names = [call[0] for call in client.calls]
        self.assertNotIn("upload", names)
        self.assertNotIn("insert", names)

    def test_existing_metadata_mismatch_fails_closed(self):
        data = b"expected"
        spec = make_spec(data)
        client = FakeClient(spec, data)
        client.release = {**spec.metadata(), "sha256": "0" * 64}
        with self.assertRaises(PublisherError):
            publish_release(client, spec, data)
        self.assertNotIn("upload", [call[0] for call in client.calls])

    def test_build_spec_derives_locked_phase6_path_and_name(self):
        with TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"candidate")
            spec = build_spec(apk, 6012, "0.1.0-phase6.12")
        self.assertEqual("phase6-test/6012/MyFinHub-Phase6-6012.apk", spec.storage_path)

    def test_build_spec_rejects_noncanonical_version_name(self):
        with TemporaryDirectory() as tmp:
            apk = Path(tmp) / "candidate.apk"
            apk.write_bytes(b"candidate")
            with self.assertRaises(PublisherError):
                build_spec(apk, 6012, "1.0.0")


if __name__ == "__main__":
    unittest.main()
