"""addons/icon-pack-minimal: make-pack.py turns components.txt into drawables and appfilter.xml, the committed
files match it, the manifest asks for no permission and answers the two icon-pack intents."""
import importlib.util
import tempfile
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "addons" / "icon-pack-minimal"
RES = PACK / "app" / "src" / "main" / "res"
ANDROID = "{http://schemas.android.com/apk/res/android}"

spec = importlib.util.spec_from_file_location("make_pack", PACK / "make-pack.py")
make_pack = importlib.util.module_from_spec(spec)
spec.loader.exec_module(make_pack)

SAMPLE = """# comment

com.example.one/.Main One
com.example.two/com.example.two.ui.Home
"""
PNG = b"\x89PNG\r\n\x1a\n" + b"\x00" * 16


class ParseTest(unittest.TestCase):
    def test_entries_labels_and_dot_expansion(self):
        one, two = make_pack.parse_components(SAMPLE)
        self.assertEqual(one.component, "com.example.one/com.example.one.Main")
        self.assertEqual((one.letter, one.drawable), ("O", "ic_com_example_one"))
        self.assertEqual(two.letter, "T")  # no label: last word of the package

    def test_bad_lines_are_refused(self):
        for text in ("justapackage", "bad pkg/.Main", "com.example.x/", "com.a.b/.M\ncom.a.b/.N"):
            with self.subTest(text=text), self.assertRaises(ValueError):
                make_pack.parse_components(text)

    def test_every_letter_and_digit_has_a_glyph(self):
        for ch in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789":
            self.assertIn(ch, make_pack.GLYPHS)
            for line in make_pack.GLYPHS[ch]:
                self.assertGreaterEqual(len(line), 2)
                for x, y in line:
                    self.assertTrue(0 <= x <= 4 and 0 <= y <= 6, (ch, x, y))


class GenerateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.res = Path(self.tmp.name) / "res"
        self.entries = make_pack.parse_components(SAMPLE)

    def test_write_then_check_is_clean_and_xml_is_valid(self):
        make_pack.write_pack(self.entries, self.res)
        self.assertEqual(make_pack.check_pack(self.entries, self.res), [])
        for xml in self.res.rglob("*.xml"):
            ET.parse(xml)
        items = ET.parse(self.res / "xml" / "appfilter.xml").getroot().findall("item")
        self.assertEqual([i.get("drawable") for i in items], ["ic_com_example_one", "ic_com_example_two"])
        self.assertEqual(items[0].get("component"), "ComponentInfo{com.example.one/com.example.one.Main}")

    def test_check_reports_missing_extra_and_outdated(self):
        make_pack.write_pack(self.entries, self.res)
        (self.res / "drawable" / "ic_com_example_one.xml").unlink()
        (self.res / "drawable" / "ic_stray.xml").write_text("<x/>")
        (self.res / "drawable" / "ic_com_example_two.xml").write_text("<x/>")
        problems = make_pack.check_pack(self.entries, self.res)
        self.assertEqual(problems, ["missing drawable/ic_com_example_one.xml",
                                    "out of date drawable/ic_com_example_two.xml",
                                    "extra drawable/ic_stray.xml"])

    def test_own_pictures_replace_monograms_and_stale_files_go(self):
        make_pack.write_pack(self.entries, self.res)
        icons = Path(self.tmp.name) / "icons"
        icons.mkdir()
        (icons / "com.example.one.png").write_bytes(PNG)
        (icons / "com.example.two.webp").write_bytes(PNG)
        (icons / "com.not.listed.png").write_bytes(PNG)
        make_pack.write_pack(self.entries, self.res, icons)
        self.assertTrue((self.res / "drawable-nodpi" / "ic_com_example_one.png").is_file())
        self.assertTrue((self.res / "drawable-nodpi" / "ic_com_example_two.webp").is_file())
        self.assertFalse(list((self.res / "drawable").glob("ic_*")))
        self.assertFalse((self.res / "drawable-nodpi" / "ic_com_not_listed.png").exists())
        self.assertEqual(make_pack.check_pack(self.entries, self.res, icons), [])
        make_pack.write_pack(self.entries, self.res)  # back to monograms
        self.assertFalse(list((self.res / "drawable-nodpi").glob("ic_*")))

    def test_cli_check_exit_codes(self):
        components = Path(self.tmp.name) / "components.txt"
        components.write_text(SAMPLE)
        base = ["--components", str(components), "--res", str(self.res)]
        self.assertEqual(make_pack.main(base + ["--check"]), 1)
        self.assertEqual(make_pack.main(base), 0)
        self.assertEqual(make_pack.main(base + ["--check"]), 0)
        self.assertEqual(make_pack.main(base + ["--icons", str(Path(self.tmp.name) / "nope")]), 2)
        components.write_text("broken")
        self.assertEqual(make_pack.main(base), 2)


class CommittedPackTest(unittest.TestCase):
    def test_committed_files_match_components_txt(self):
        entries = make_pack.parse_components((PACK / "components.txt").read_text(encoding="utf-8"))
        self.assertGreaterEqual(len(entries), 10)
        self.assertEqual(make_pack.check_pack(entries, RES), [])

    def test_no_brand_art_or_private_pack_is_committed(self):
        self.assertEqual(list((RES / "drawable-nodpi").glob("*")) if (RES / "drawable-nodpi").is_dir() else [], [])
        self.assertEqual([p for p in PACK.rglob("*.apk") if "build" not in p.parts], [])

    def test_manifest_has_no_permission_and_both_icon_pack_intents(self):
        text = (PACK / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertNotIn("<uses-permission", text)
        root = ET.fromstring(text)
        actions = {a.get(ANDROID + "name") for a in root.iter("action")}
        self.assertTrue({"org.adw.launcher.THEMES", "com.novalauncher.THEME"} <= actions)
        self.assertTrue(all(a.get(ANDROID + "exported") == "true" for a in root.iter("activity")))

    def test_readme_says_no_permissions_and_how_to_build_a_private_pack(self):
        readme = (PACK / "README.md").read_text(encoding="utf-8")
        self.assertRegex(readme, r"(?im)^#+\s*permissions")
        self.assertIn("make-pack.py --icons", readme)


if __name__ == "__main__":
    unittest.main()
