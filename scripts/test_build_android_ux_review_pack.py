"""Hermetic review-pack tests; no Android SDK or user data required."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

path = Path(__file__).with_name("build_android_ux_review_pack.py")
spec = importlib.util.spec_from_file_location("build_android_ux_review_pack", path)
assert spec and spec.loader
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)
FAKE_PNG = b"\x89PNG\r\n\x1a\n" + b"0" * 16


class ReviewPackTests(unittest.TestCase):
    def test_screen_paths_states_provenance_and_truthful_limits(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "app/src/screenshotTestDebug/reference"
            files = [
                "app/myfinhub/android/feature/money/CanonicalCardDetailScreenshotTestKt/CanonicalCardDetailLight.png",
                "app/myfinhub/android/feature/money/WalletAccountsScreenshotTestKt/WalletCardsDark.png",
                "app/myfinhub/android/feature/plan/S7PlanScreenshotTestKt/S7PlanLight.png",
                "app/myfinhub/android/feature/money/CanonicalCorrectionScreensScreenshotTestKt/CanonicalCardCreateLight.png",
            ]
            for f in files:
                p = src / f
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(FAKE_PNG)
            dest = root / "app/build/reports/screenshotTest/gallery/review.html"
            result = mod.render(src, dest, source_sha="a" * 40, run_id="123", validation="success")
            self.assertEqual(result["screenshot_count"], 4)
            self.assertEqual(result["device_acceptance"], "not_established")
            by_id = {x["id"]: x for x in result["journeys"]}
            self.assertEqual(by_id["cards"]["count"], 1)
            self.assertEqual(by_id["card-detail"]["count"], 1)
            self.assertEqual(by_id["legacy"]["count"], 2)
            self.assertTrue(by_id["card-detail"]["requires_behavioral_tests"])
            self.assertIn("large-font", by_id["card-detail"]["missing_baseline_variants"])
            self.assertIn("Αλλαγή κάρτας", dest.read_text(encoding="utf-8"))
            self.assertIn("src/screenshotTestDebug/reference", dest.read_text(encoding="utf-8"))
            self.assertEqual(json.loads(dest.with_name("review-index.json").read_text())["source_sha"], "a" * 40)
            self.assertIn("EVERY control", dest.with_name("review-checklist.md").read_text())
            with self.assertRaisesRegex(ValueError, "unsafe output"):
                mod.render(src, src / "bad.html")

    def test_missing_and_fake_pngs_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            src = Path(tmp) / "refs"
            src.mkdir()
            dest = Path(tmp) / "review.html"
            with self.assertRaisesRegex(ValueError, "No official"):
                mod.render(src, dest)
            (src / "bad.png").write_text("no")
            with self.assertRaisesRegex(ValueError, "Invalid official"):
                mod.render(src, dest)

    def test_legacy_and_secondary_are_not_mobile_roots(self):
        self.assertEqual(mod.surface(Path("app/feature/insights/InsightsTest/InsightsLight.png")), "legacy")
        self.assertEqual(mod.surface(Path("app/feature/money/CanonicalMoneyTest/CanonicalMoneyLight.png")), "legacy")
        self.assertEqual(mod.surface(Path("app/feature/money/CanonicalCorrectionScreensTest/CanonicalCardCreateLight.png")), "legacy")
        self.assertEqual(mod.surface(Path("app/feature/money/InlineCreditCardWorkspaceTest/InlineCreditWorkspaceDark.png")), "legacy")
        self.assertEqual(mod.surface(Path("app/feature/money/CanonicalMoneyScreenshotTestKt/CanonicalCardDeleteDialogLight.png")), "legacy")
        self.assertEqual(mod.surface(Path("app/feature/money/WalletAccountsTest/WalletCardsDark.png")), "cards")
        self.assertEqual(mod.variant("CanonicalCardDetailLargeFontScreenshot.png"), "large-font")


if __name__ == "__main__":
    unittest.main()
