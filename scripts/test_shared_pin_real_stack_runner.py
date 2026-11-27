import json
import tempfile
import unittest
from pathlib import Path

from run_shared_pin_real_stack import local_status, require_test_executed, safe_test_failure


class IsolatedRunnerSafetyTest(unittest.TestCase):
    def test_only_exact_loopback_and_distinct_keys(self):
        config = {"API_URL": "http://127.0.0.1:54321", "PUBLISHABLE_KEY": "p" * 30, "SERVICE_ROLE_KEY": "s" * 30}
        self.assertEqual(local_status(json.dumps(config)), ("p" * 30, "s" * 30))
        for url in ("https://production.example", "http://localhost:54321", "http://127.0.0.1:9000",
                    "http://127.0.0.1:54321@production.example", "http://127.0.0.1:54321/path"):
            with self.subTest(url=url), self.assertRaises(RuntimeError):
                local_status(json.dumps({**config, "API_URL": url}))
        with self.assertRaises(RuntimeError):
            local_status(json.dumps({**config, "SERVICE_ROLE_KEY": "p" * 30}))
        with self.assertRaises(RuntimeError):
            local_status(json.dumps({**config, "nested": {"api_url": "https://production.example"}}))
        self.assertEqual(local_status(json.dumps({"nested": config})), ("p" * 30, "s" * 30))

    def test_failure_diagnostic_restricts_stage_and_never_emits_raw_payload(self):
        name = "nativeBearerLockAndRecoveryAgainstRealLocalStack"
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "test.xml"
            report.write_text(
                f'<testsuite><testcase name="{name}"><failure message="java.lang.IllegalStateException: '
                'other device disabled status: lock request failed (AUTH_REQUIRED, HTTP 401)">'
                'private_fixture_material\nSharedAppLockRealStackTest.kt:274</failure></testcase></testsuite>')
            self.assertEqual(safe_test_failure(report),
                             'Isolated native test stage: other device disabled status; lock request failed: AUTH_REQUIRED; HTTP 401; source lines 274')
            report.write_text(f'<testsuite><testcase name="{name}"><failure message="private_fixture_material">'
                              'private_fixture_material</failure></testcase></testsuite>')
            self.assertEqual(safe_test_failure(report), 'Isolated native test failed; source lines unknown')
            report.unlink()
            self.assertIsNone(safe_test_failure(report))

    def test_report_must_prove_execution_not_skip_or_empty_suite(self):
        name = "nativeBearerLockAndRecoveryAgainstRealLocalStack"
        with tempfile.TemporaryDirectory() as tmp:
            report = Path(tmp) / "test.xml"
            for body in ("", f'<testcase name="{name}"><skipped/></testcase>',
                         f'<testcase name="{name}"><failure/></testcase>', '<testcase name="other"/>'):
                report.write_text(f"<testsuite>{body}</testsuite>")
                with self.assertRaises(RuntimeError):
                    require_test_executed(report)
            report.write_text(f'<testsuite><testcase name="{name}"/></testsuite>')
            require_test_executed(report)


if __name__ == "__main__":
    unittest.main()
