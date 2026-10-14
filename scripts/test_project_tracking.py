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
        self.assertNotIn("- [ ]", todo)
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

if __name__ == "__main__":
    unittest.main()
