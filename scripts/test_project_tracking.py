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

    def test_historical_state_remains_readable(self):
        del self.state["redesign_tasks"]
        tracking.validate_redesign(self.state)
        self.assertEqual(tracking.redesign_lines(self.state), [])


if __name__ == "__main__":
    unittest.main()
