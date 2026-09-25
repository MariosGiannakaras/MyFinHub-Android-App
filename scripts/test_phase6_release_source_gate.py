import unittest

from scripts.phase6_release_source_gate import SourceGateError, validate_required_checks, validate_source_pr


REPO = "MariosGiannakaras/MyFinHub-Android-App"
SHA = "a" * 40


def pr_payload(**overrides):
    payload = {
        "number": 62,
        "state": "open",
        "draft": False,
        "base": {"ref": "develop"},
        "head": {
            "ref": "android/ui-ux-canonical-correction-pass",
            "sha": SHA,
            "repo": {"full_name": REPO},
        },
    }
    payload.update(overrides)
    return payload


def successful_checks():
    return {
        "check_runs": [
            {"id": 10, "name": "verify", "status": "completed", "conclusion": "success"},
            {"id": 11, "name": "screenshot-regression", "status": "completed", "conclusion": "success"},
            {"id": 12, "name": "s24-ultra-target-instrumented", "status": "completed", "conclusion": "success"},
        ]
    }


class SourcePrValidationTest(unittest.TestCase):
    def test_accepts_open_ready_same_repo_android_pr_to_develop(self):
        self.assertEqual(SHA, validate_source_pr(REPO, 62, pr_payload()))

    def test_rejects_fork_source(self):
        payload = pr_payload()
        payload["head"]["repo"]["full_name"] = "someone/fork"
        with self.assertRaises(SourceGateError):
            validate_source_pr(REPO, 62, payload)

    def test_rejects_wrong_base(self):
        payload = pr_payload()
        payload["base"]["ref"] = "main"
        with self.assertRaises(SourceGateError):
            validate_source_pr(REPO, 62, payload)

    def test_rejects_draft(self):
        with self.assertRaises(SourceGateError):
            validate_source_pr(REPO, 62, pr_payload(draft=True))

    def test_rejects_non_android_branch(self):
        payload = pr_payload()
        payload["head"]["ref"] = "feature/random"
        with self.assertRaises(SourceGateError):
            validate_source_pr(REPO, 62, payload)


class CheckValidationTest(unittest.TestCase):
    def test_accepts_required_successful_latest_checks(self):
        validate_required_checks(successful_checks())

    def test_rejects_missing_check(self):
        payload = successful_checks()
        payload["check_runs"] = payload["check_runs"][:-1]
        with self.assertRaises(SourceGateError):
            validate_required_checks(payload)

    def test_rejects_newer_failed_attempt_even_if_older_succeeded(self):
        payload = successful_checks()
        payload["check_runs"].extend(
            [
                {"id": 20, "name": "verify", "status": "completed", "conclusion": "failure"},
            ]
        )
        with self.assertRaises(SourceGateError):
            validate_required_checks(payload)

    def test_rejects_in_progress_check(self):
        payload = successful_checks()
        payload["check_runs"][1] = {
            "id": 30,
            "name": "screenshot-regression",
            "status": "in_progress",
            "conclusion": None,
        }
        with self.assertRaises(SourceGateError):
            validate_required_checks(payload)


if __name__ == "__main__":
    unittest.main()
