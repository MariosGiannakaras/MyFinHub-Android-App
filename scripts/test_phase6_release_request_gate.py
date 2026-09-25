import json
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.phase6_release_request_gate import RequestGateError, parse_request, resolve_source_pr


class ReleaseRequestGateTest(unittest.TestCase):
    def test_valid_request_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "request.json"
            path.write_text(json.dumps({"source_pr": 62, "request_id": "pr62-6012"}), encoding="utf-8")
            self.assertEqual(62, parse_request(path))

    def test_request_rejects_extra_fields(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "request.json"
            path.write_text(json.dumps({"source_pr": 62, "request_id": "pr62-6012", "channel": "production"}), encoding="utf-8")
            with self.assertRaises(RequestGateError):
                parse_request(path)

    def test_request_rejects_bad_pr(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "request.json"
            path.write_text(json.dumps({"source_pr": 0, "request_id": "pr62-6012"}), encoding="utf-8")
            with self.assertRaises(RequestGateError):
                parse_request(path)

    def test_workflow_dispatch_still_accepts_positive_pr(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "workflow_dispatch", "SOURCE_PR": "62"}, clear=True):
            self.assertEqual(62, resolve_source_pr())

    def test_unsupported_event_fails_closed(self):
        with patch.dict(os.environ, {"GITHUB_EVENT_NAME": "schedule"}, clear=True):
            with self.assertRaises(RequestGateError):
                resolve_source_pr()


if __name__ == "__main__":
    unittest.main()
