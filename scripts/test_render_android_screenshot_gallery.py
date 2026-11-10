"""Tests for the offline Compose screenshot gallery index."""
import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE_PATH = Path(__file__).with_name("render_android_screenshot_gallery.py")
SPEC = importlib.util.spec_from_file_location("render_android_screenshot_gallery", MODULE_PATH)
assert SPEC and SPEC.loader
module = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(module)

# Tiny PNG signature is sufficient because this tool indexes, never decodes pixels.
FAKE_PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16


class ScreenshotGalleryTests(unittest.TestCase):
    def test_indexes_real_pngs_by_screen_and_uses_relative_urls(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "app/src/screenshotTestDebug/reference"
            (src / "home").mkdir(parents=True)
            (src / "utilities").mkdir(parents=True)
            (src / "home/Home Preview.png").write_bytes(FAKE_PNG)
            (src / "utilities/settings_compact.png").write_bytes(FAKE_PNG)
            output = root / "app/build/reports/screenshotTest/gallery/index.html"

            self.assertEqual(module.render_gallery(src, output), 2)
            html = output.read_text(encoding="utf-8")
            self.assertIn("2 πραγματικά PNG", html)
            self.assertIn("Αρχική", html)
            self.assertIn("Ρυθμίσεις Android", html)
            self.assertIn("Home%20Preview.png", html)
            self.assertIn("../../../../src/screenshotTestDebug/reference", html)
            self.assertNotIn("data:image", html)

    def test_refuses_empty_or_fake_png_sources(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "references"
            src.mkdir()
            output = root / "index.html"
            with self.assertRaisesRegex(ValueError, "No official PNG"):
                module.render_gallery(src, output)
            (src / "invalid.png").write_text("not a screenshot", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Not a PNG"):
                module.render_gallery(src, output)

    def test_refuses_output_inside_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            src = root / "references"
            src.mkdir()
            (src / "ok.png").write_bytes(FAKE_PNG)
            with self.assertRaisesRegex(ValueError, "must not be inside"):
                module.render_gallery(src, src / "index.html")


if __name__ == "__main__":
    unittest.main()
