from __future__ import annotations

import hashlib
import tempfile
import unittest
from pathlib import Path

import production_release_publisher as publisher


class FakeClient:
    def __init__(self, latest: int | None = None) -> None:
        self.latest = latest
        self.rows: dict[int, dict] = {}
        self.objects: dict[str, bytes] = {}
        self.calls: list[str] = []
        self.raise_upload = False
        self.raise_insert = False

    def get_release(self, version_code: int):
        self.calls.append("get_release")
        return self.rows.get(version_code)

    def get_release_by_run_id(self, release_run_id: str):
        self.calls.append("get_release_by_run_id")
        marker = publisher.release_notes(release_run_id)
        for row in self.rows.values():
            if row.get("channel") == publisher.CHANNEL and row.get("notes") == marker:
                return row
        return None

    def get_latest_version_code(self):
        self.calls.append("get_latest_version_code")
        if self.rows:
            return max(self.rows)
        return self.latest

    def download_object(self, storage_path: str) -> bytes:
        self.calls.append("download_object")
        if storage_path not in self.objects:
            raise publisher.NotFound("missing")
        return self.objects[storage_path]

    def upload_object(self, storage_path: str, data: bytes) -> None:
        self.calls.append("upload_object")
        self.objects[storage_path] = data
        if self.raise_upload:
            raise publisher.RemoteWriteError("ambiguous")

    def insert_release(self, metadata: dict) -> None:
        self.calls.append("insert_release")
        self.rows[int(metadata["version_code"])] = dict(metadata)
        if self.raise_insert:
            raise publisher.RemoteWriteError("ambiguous")


class ProductionPublisherTests(unittest.TestCase):
    def test_first_production_plan_starts_at_reserved_range(self) -> None:
        plan = publisher.plan_next_release(FakeClient(), "123456", "1.0.0")
        self.assertEqual(10000, plan["version_code"])
        self.assertEqual("1.0.0", plan["version_name"])
        self.assertEqual("production/10000/MyFinHub-10000.apk", plan["storage_path"])

    def test_next_production_plan_increments_existing_version(self) -> None:
        plan = publisher.plan_next_release(FakeClient(latest=10004), "123457", "1.1.0")
        self.assertEqual(10005, plan["version_code"])
        self.assertEqual("production/10005/MyFinHub-10005.apk", plan["storage_path"])

    def test_rejects_version_below_reserved_production_range(self) -> None:
        with self.assertRaises(publisher.PublisherError):
            publisher.expected_storage_path(9999)

    def test_rejects_unsafe_version_name(self) -> None:
        with self.assertRaises(publisher.PublisherError):
            publisher.validate_version_name("1.0.0 bad")

    def test_fresh_publish_re_reads_bytes_before_metadata(self) -> None:
        data = b"signed-production-apk"
        spec = publisher.ReleaseSpec(
            version_code=10000,
            version_name="1.0.0",
            storage_path=publisher.expected_storage_path(10000),
            sha256=hashlib.sha256(data).hexdigest(),
            size_bytes=len(data),
            release_run_id="123458",
        )
        client = FakeClient()
        result = publisher.publish_release(client, spec, data)
        self.assertEqual("published", result)
        self.assertLess(client.calls.index("upload_object"), client.calls.index("download_object"))
        self.assertLess(client.calls.index("download_object"), client.calls.index("insert_release"))
        self.assertEqual(spec.metadata(), client.rows[10000])

    def test_ambiguous_upload_is_reconciled_without_second_upload(self) -> None:
        data = b"signed-production-apk"
        spec = publisher.ReleaseSpec(
            version_code=10001,
            version_name="1.0.1",
            storage_path=publisher.expected_storage_path(10001),
            sha256=hashlib.sha256(data).hexdigest(),
            size_bytes=len(data),
            release_run_id="123459",
        )
        client = FakeClient()
        client.raise_upload = True
        self.assertEqual("published", publisher.publish_release(client, spec, data))
        self.assertEqual(1, client.calls.count("upload_object"))
        self.assertGreaterEqual(client.calls.count("download_object"), 1)

    def test_build_spec_hashes_exact_bytes(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "candidate.apk"
            apk.write_bytes(b"apk-bytes")
            spec = publisher.build_spec(apk, 10000, "1.0.0", "123460")
        self.assertEqual(hashlib.sha256(b"apk-bytes").hexdigest(), spec.sha256)
        self.assertEqual(len(b"apk-bytes"), spec.size_bytes)


if __name__ == "__main__":
    unittest.main()
