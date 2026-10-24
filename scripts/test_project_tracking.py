"""Regression checks for memory-independent redesign progress integrity."""
import copy
import unittest

import render_project_tracking as tracking


class RedesignTrackingTests(unittest.TestCase):
    def setUp(self):
        self.state = copy.deepcopy(tracking.load_state())
        for task in self.state["redesign_tasks"]:
            for sub in task["subtasks"]:
                sub.update(status="pending", evidence=[])

    def test_counts_are_derived_and_preparation_is_separate(self):
        for sub in self.state["redesign_tasks"][0]["subtasks"]:
            sub.update(status="completed", evidence=["Fixture validation passed"])
        self.state["redesign_tasks"][1]["subtasks"][0].update(
            status="completed", evidence=["Rendered fixture reviewed"]
        )
        tracking.validate_redesign(self.state)
        for content in tracking.rendered(self.state).values():
            self.assertIn("Tasks: 1/10 · Subtasks: 5/40", content)

    def test_completion_requires_evidence(self):
        self.state["redesign_tasks"][0]["subtasks"][0]["status"] = "completed"
        with self.assertRaisesRegex(SystemExit, "needs evidence"):
            tracking.validate_redesign(self.state)

    def test_blocker_requires_reason(self):
        self.state["redesign_tasks"][0]["subtasks"][0]["status"] = "blocked"
        with self.assertRaisesRegex(SystemExit, "needs a reason"):
            tracking.validate_redesign(self.state)

    def test_blocker_is_visible_in_generated_handoff(self):
        self.state["redesign_tasks"][0]["subtasks"][0].update(
            status="blocked", blocker="Existing contract unavailable"
        )
        tracking.validate_redesign(self.state)
        self.assertIn("S1.1: Existing contract unavailable", "\n".join(tracking.redesign_lines(self.state)))

    def test_missing_slice_is_rejected(self):
        self.state["redesign_tasks"].pop()
        with self.assertRaisesRegex(SystemExit, "ten agreed slices"):
            tracking.validate_redesign(self.state)

    def test_reassigned_subtask_id_is_rejected(self):
        self.state["redesign_tasks"][0]["subtasks"][0]["id"] = "S9.1"
        with self.assertRaisesRegex(SystemExit, "IDs must remain stable"):
            tracking.validate_redesign(self.state)

    def test_completed_redesign_does_not_claim_future_physical_acceptance(self):
        for task in self.state["redesign_tasks"]:
            for sub in task["subtasks"]:
                sub.update(status="completed", evidence=["Acceptance evidence"])
        self.state["current_redesign_pass"]["blockers"] = []
        rendered = "\n".join(tracking.rendered(self.state).values())
        self.assertIn("No recorded blocker.", rendered)
        self.assertNotIn("Physical S24 acceptance remains a future required gate", rendered)

    def test_completed_workstream_does_not_show_open_implementation(self):
        self.state["active_workstream"].update(status="completed", next=[])
        outputs = tracking.rendered(self.state)
        status = next(value for path, value in outputs.items() if path.name == "STATUS.md")
        todo = next(value for path, value in outputs.items() if path.name == "TODO.md")
        handoff = next(value for path, value in outputs.items() if path.name == "CURRENT_HANDOFF.md")
        self.assertIn("## Last completed workstream", status)
        self.assertIn("No active implementation tasks.", status)
        self.assertIn("No active implementation tasks.", todo)
        self.assertNotIn("- [ ]", todo.split("## Completed foundations", 1)[0])
        self.assertIn("## Completed workstream outcome", handoff)
        self.assertNotIn("## Why implementation is open", handoff)

    def test_historical_state_remains_readable(self):
        del self.state["redesign_tasks"]
        tracking.validate_redesign(self.state)
        self.assertEqual(tracking.redesign_lines(self.state), [])


class WebParityTrackingTests(unittest.TestCase):
    def setUp(self):
        self.state = copy.deepcopy(tracking.load_state())

    def test_reconciliation_counts_are_derived(self):
        stages = self.state['reconciliation_workstream']['stages']
        # Isolate this synthetic fixture from any future live stage completions.
        for stage in stages:
            for sub in stage['subtasks']:
                sub.update(status='pending', evidence=[])
        for sub in stages[0]['subtasks']:
            sub.update(status='completed', evidence=['merged and validated'])
        stages[1]['subtasks'][0].update(status='completed', evidence=['merged and validated'])
        self.state['reconciliation_workstream']['current_subtask'] = 'W2.2'
        tracking.validate_reconciliation(self.state)
        lines = '\n'.join(tracking.reconciliation_lines(self.state))
        self.assertIn('Implementations: 1/6 · Subtasks: 5/24', lines)

    def test_completed_requires_evidence(self):
        sub = self.state['reconciliation_workstream']['stages'][0]['subtasks'][0]
        sub.update(status='completed', evidence=[])
        with self.assertRaisesRegex(SystemExit, 'needs evidence'):
            tracking.validate_reconciliation(self.state)

    def test_reconciliation_id_invariant(self):
        self.state['reconciliation_workstream']['stages'][0]['subtasks'][0]['id'] = 'W9.1'
        with self.assertRaisesRegex(SystemExit, 'ID'):
            tracking.validate_reconciliation(self.state)

    def test_old_schema_without_web_parity_still_reads(self):
        del self.state['reconciliation_workstream']
        tracking.validate_reconciliation(self.state)
        self.assertEqual(tracking.reconciliation_lines(self.state), [])



class WorkflowGateConsistencyTests(unittest.TestCase):
    """Protect all four CI PR jobs from mutable checkout drift."""

    def test_kotlin_checks_fail_fast_before_costly_benchmark_build(self):
        source = (tracking.ROOT / ".github" / "workflows" / "android-ci.yml").read_text(encoding="utf-8")
        unit = source.index("      - name: Unit, instrumentation compile, lint and debug assembly")
        benchmark = source.index("      - name: Benchmark and Baseline Profile tooling build")
        release = source.index("      - name: Optimized unsigned release and Play bundle assembly")
        self.assertLess(unit, benchmark)
        self.assertLess(benchmark, release)
        self.assertIn(":app:compileDebugAndroidTestKotlin", source)
        self.assertIn("assembleRelease bundlePlayRelease analyzeReleaseR8Config", source)

    def test_ui_jobs_fail_fast_on_stale_tracking_before_sdk_bootstrap(self):
        source = (tracking.ROOT / ".github" / "workflows" / "android-ui-quality.yml").read_text(encoding="utf-8")
        jobs = source.split("  s24-ultra-target-instrumented:\n", 1)
        self.assertEqual(len(jobs), 2)
        for job in jobs:
            check = job.index("      - name: Fail fast on canonical tracking drift")
            setup = job.index("      - name: Set up JDK 17")
            self.assertLess(check, setup)
            self.assertIn("python3 scripts/render_project_tracking.py --check", job)

    def test_gate_jobs_remain_present(self):
        root = tracking.ROOT / ".github" / "workflows"
        ci = (root / "android-ci.yml").read_text(encoding="utf-8")
        ui = (root / "android-ui-quality.yml").read_text(encoding="utf-8")
        checks = (root / "project-tracking.yml").read_text(encoding="utf-8")
        self.assertIn("jobs:\n  verify:", ci)
        self.assertIn("  screenshot-regression:", ui)
        self.assertIn("  s24-ultra-target-instrumented:", ui)
        self.assertIn("jobs:\n  verify:", checks)
        self.assertIn("validateDebugScreenshotTest", ui)
        self.assertIn("scripts/run-connected-android-tests.sh", ui)
        self.assertIn("python3 scripts/render_project_tracking.py --check", checks)

    def test_all_four_gates_use_immutable_pull_request_merge_checkout(self):
        root = tracking.ROOT / ".github" / "workflows"
        pinned = "uses: actions/checkout@d23441a48e516b6c34aea4fa41551a30e30af803"
        for name in ("android-ci.yml", "android-ui-quality.yml", "project-tracking.yml"):
            source = (root / name).read_text(encoding="utf-8")
            self.assertIn("pull_request:", source, name)
            self.assertIn("cancel-in-progress: true", source, name)
            self.assertNotIn("ref: ${{ github.head_ref }}", source, name)
            self.assertIn(pinned, source, name)
        ui = (root / "android-ui-quality.yml").read_text(encoding="utf-8")
        screenshot, emulator = ui.split("  s24-ultra-target-instrumented:", 1)
        for job in (screenshot, emulator):
            self.assertEqual(job.count(pinned), 1)
            step = job.split(pinned, 1)[1].split("      - name:", 1)[0]
            self.assertNotIn("ref:", step)
            self.assertNotIn("head_ref", step)

if __name__ == "__main__":
    unittest.main()
