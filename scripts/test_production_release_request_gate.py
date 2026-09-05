from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import production_release_request_gate as gate


class ProductionRequestGateTests(unittest.TestCase):
    def test_accepts_exact_request_shape(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "production.json"
            path.write_text(json.dumps({
                "source_pr": 70,
                "version_name": "1.0.0",
                "request_id": "prod-20260905-a",
            }), encoding="utf-8")
            source_pr, version_name = gate.parse_request(path)
        self.assertEqual(70, source_pr)
        self.assertEqual("1.0.0", version_name)

    def test_rejects_extra_fields(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "production.json"
            path.write_text(json.dumps({
                "source_pr": 70,
                "version_name": "1.0.0",
                "request_id": "prod-20260905-a",
                "signer": "do-not-accept",
            }), encoding="utf-8")
            with self.assertRaises(gate.RequestGateError):
                gate.parse_request(path)

    def test_rejects_unsafe_version_name(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "production.json"
            path.write_text(json.dumps({
                "source_pr": 70,
                "version_name": "1.0.0 unsafe",
                "request_id": "prod-20260905-a",
            }), encoding="utf-8")
            with self.assertRaises(gate.RequestGateError):
                gate.parse_request(path)


if __name__ == "__main__":
    unittest.main()
