"""Validate the generated, offline Web category vectors and semantic targets."""
import json
import re
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / "app/src/main/assets/category-icons"
POLICY = ROOT / "app/src/main/java/app/myfinhub/android/designsystem/WebCategoryIconPolicy.kt"

class CategoryCatalogAssetsTests(unittest.TestCase):
    def test_all_pinned_packs_and_local_path_shapes(self):
        manifest = json.loads((CATALOG / "manifest.json").read_text())
        expected = {"lucide": 1876, "tabler": 5166, "phosphor": 1512, "heroicons": 322, "bootstrap": 1404}
        self.assertEqual(set(expected), set(manifest["packs"]))
        policy = POLICY.read_text()
        all_names = {}
        for pack, count in expected.items():
            self.assertEqual(count, manifest["packs"][pack]["count"])
            seen = set()
            for file in sorted((CATALOG / pack).glob("*.tsv")):
                for line in file.read_text().splitlines():
                    name, viewport, *paths = line.split("\t")
                    self.assertRegex(name, r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
                    self.assertNotIn(name, seen)
                    seen.add(name)
                    self.assertIn(viewport, {"16", "24", "256"})
                    self.assertTrue(paths)
                    for raw in paths:
                        fill, stroke, width, fill_alpha, stroke_alpha, evenodd, transform, path = raw.split("|", 7)
                        self.assertIn(fill, {"0", "1"})
                        self.assertIn(stroke, {"0", "1"})
                        self.assertTrue(float(width) > 0)
                        self.assertTrue(0 <= float(fill_alpha) <= 1)
                        self.assertTrue(0 <= float(stroke_alpha) <= 1)
                        self.assertIn(evenodd, {"0", "1"})
                        self.assertIn(transform, {"0", "1"})
                        self.assertRegex(path, r"^[Mm]")
                        self.assertRegex(path, r"^[MmLlHhVvCcSsQqTtAaZzEe0-9.,+\- ]+$")
            self.assertEqual(count, len(seen))
            all_names[pack] = seen
            body = re.search(r'"'+pack+r'" to mapOf\((.*?)\n        \),', policy, re.S).group(1)
            semantic = re.findall(r'"([^"]+)" to "([^"]+)"', body)
            self.assertEqual(82, len(semantic))
            self.assertEqual(82, len({key for key, _ in semantic}))
            self.assertTrue({name for _, name in semantic} <= seen)
        self.assertEqual(10280, sum(map(len, all_names.values())))
        self.assertIn("ISC License", (CATALOG / "THIRD_PARTY_NOTICES.md").read_text())

if __name__ == "__main__":
    unittest.main()
