#!/usr/bin/env python3
"""Generate this icon pack's drawables and res/xml/appfilter.xml from components.txt (python stdlib only).

    python3 make-pack.py                      write monogram vector drawables + appfilter.xml
    python3 make-pack.py --icons DIR          icons/<package>.png (or .webp/.jpg) replaces that package's monogram
    python3 make-pack.py --check              verify the files on disk match components.txt, change nothing

Monograms are drawn here from a tiny stroke font on a rounded square: neutral art of our own, no brand logos.
`--icons` is how an owner builds a private pack from their own pictures; see README.md. DIR is only read when
given, never committed.
"""
import argparse
import hashlib
import re
import shutil
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_RES = HERE / "app" / "src" / "main" / "res"
PICTURE_SUFFIXES = (".png", ".webp", ".jpg")
PREFIX = "ic_"

# Stroke font on a 4 x 6 grid: each glyph is a list of polylines.
GLYPHS = {
    "A": [[(0, 6), (2, 0), (4, 6)], [(0.8, 4), (3.2, 4)]],
    "B": [[(0, 6), (0, 0), (3, 0), (4, 1), (4, 2), (3, 3), (0, 3)], [(3, 3), (4, 4), (4, 5), (3, 6), (0, 6)]],
    "C": [[(4, 1), (3, 0), (1, 0), (0, 1), (0, 5), (1, 6), (3, 6), (4, 5)]],
    "D": [[(0, 0), (0, 6), (2.5, 6), (4, 4.5), (4, 1.5), (2.5, 0), (0, 0)]],
    "E": [[(4, 0), (0, 0), (0, 6), (4, 6)], [(0, 3), (3, 3)]],
    "F": [[(4, 0), (0, 0), (0, 6)], [(0, 3), (3, 3)]],
    "G": [[(4, 1), (3, 0), (1, 0), (0, 1), (0, 5), (1, 6), (3, 6), (4, 5), (4, 3), (2, 3)]],
    "H": [[(0, 0), (0, 6)], [(4, 0), (4, 6)], [(0, 3), (4, 3)]],
    "I": [[(1, 0), (3, 0)], [(2, 0), (2, 6)], [(1, 6), (3, 6)]],
    "J": [[(4, 0), (4, 5), (3, 6), (1, 6), (0, 5)]],
    "K": [[(0, 0), (0, 6)], [(4, 0), (0, 3.5)], [(1.5, 2.5), (4, 6)]],
    "L": [[(0, 0), (0, 6), (4, 6)]],
    "M": [[(0, 6), (0, 0), (2, 3), (4, 0), (4, 6)]],
    "N": [[(0, 6), (0, 0), (4, 6), (4, 0)]],
    "O": [[(1, 0), (3, 0), (4, 1), (4, 5), (3, 6), (1, 6), (0, 5), (0, 1), (1, 0)]],
    "P": [[(0, 6), (0, 0), (3, 0), (4, 1), (4, 2), (3, 3), (0, 3)]],
    "Q": [[(1, 0), (3, 0), (4, 1), (4, 5), (3, 6), (1, 6), (0, 5), (0, 1), (1, 0)], [(2.5, 4), (4, 6)]],
    "R": [[(0, 6), (0, 0), (3, 0), (4, 1), (4, 2), (3, 3), (0, 3)], [(2, 3), (4, 6)]],
    "S": [[(4, 1), (3, 0), (1, 0), (0, 1), (0, 2), (1, 3), (3, 3), (4, 4), (4, 5), (3, 6), (1, 6), (0, 5)]],
    "T": [[(0, 0), (4, 0)], [(2, 0), (2, 6)]],
    "U": [[(0, 0), (0, 5), (1, 6), (3, 6), (4, 5), (4, 0)]],
    "V": [[(0, 0), (2, 6), (4, 0)]],
    "W": [[(0, 0), (1, 6), (2, 3), (3, 6), (4, 0)]],
    "X": [[(0, 0), (4, 6)], [(4, 0), (0, 6)]],
    "Y": [[(0, 0), (2, 3), (4, 0)], [(2, 3), (2, 6)]],
    "Z": [[(0, 0), (4, 0), (0, 6), (4, 6)]],
    "0": [[(1, 0), (3, 0), (4, 1), (4, 5), (3, 6), (1, 6), (0, 5), (0, 1), (1, 0)], [(1, 5), (3, 1)]],
    "1": [[(1, 1), (2, 0), (2, 6)], [(1, 6), (3, 6)]],
    "2": [[(0, 1), (1, 0), (3, 0), (4, 1), (4, 2), (0, 6), (4, 6)]],
    "3": [[(0, 0), (4, 0), (2, 3), (3, 3), (4, 4), (4, 5), (3, 6), (1, 6), (0, 5)]],
    "4": [[(3, 6), (3, 0), (0, 4), (4, 4)]],
    "5": [[(4, 0), (0, 0), (0, 3), (3, 3), (4, 4), (4, 5), (3, 6), (0, 6)]],
    "6": [[(4, 0), (1, 0), (0, 1), (0, 5), (1, 6), (3, 6), (4, 5), (4, 4), (3, 3), (0, 3)]],
    "7": [[(0, 0), (4, 0), (1.5, 6)]],
    "8": [[(1, 0), (3, 0), (4, 1), (4, 2), (3, 3), (1, 3), (0, 2), (0, 1), (1, 0)],
          [(1, 3), (0, 4), (0, 5), (1, 6), (3, 6), (4, 5), (4, 4), (3, 3)]],
    "9": [[(0, 6), (3, 6), (4, 5), (4, 1), (3, 0), (1, 0), (0, 1), (0, 2), (1, 3), (4, 3)]],
}

# Muted backgrounds that keep white strokes readable.
PALETTE = ("#37474F", "#455A64", "#4E5D78", "#5D4E78", "#6D4C6D", "#78504E", "#4E7060", "#6B6A3E")

GLYPH_SCALE = 7.0
STROKE_WIDTH = 6
ROUNDED_SQUARE = "M20,4H76A16,16 0 0 1 92,20V76A16,16 0 0 1 76,92H20A16,16 0 0 1 4,76V20A16,16 0 0 1 20,4Z"


class Entry:
    def __init__(self, package, activity, label):
        self.package = package
        self.activity = activity
        self.label = label

    @property
    def component(self):
        return f"{self.package}/{self.activity}"

    @property
    def drawable(self):
        return PREFIX + re.sub(r"[^a-z0-9]", "_", self.package.lower())

    @property
    def letter(self):
        for ch in (self.label or self.package.rsplit(".", 1)[-1]).upper():
            if ch in GLYPHS:
                return ch
        return "?"


def parse_components(text):
    """Entries from components.txt text: `package/activity [label]`, `#` comments and blanks skipped."""
    entries = []
    seen = set()
    for number, raw in enumerate(text.splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        parts = line.split(None, 1)
        if "/" not in parts[0]:
            raise ValueError(f"line {number}: expected package/activity, got {parts[0]!r}")
        package, activity = parts[0].split("/", 1)
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+", package):
            raise ValueError(f"line {number}: {package!r} is not a package name")
        if not activity:
            raise ValueError(f"line {number}: no activity after the slash")
        if activity.startswith("."):
            activity = package + activity
        if package in seen:
            raise ValueError(f"line {number}: {package} is listed twice")
        seen.add(package)
        entries.append(Entry(package, activity, parts[1].strip() if len(parts) > 1 else ""))
    return entries


def _number(value):
    return f"{value:.1f}".rstrip("0").rstrip(".")


def monogram_xml(entry):
    """A vector drawable: a muted rounded square with the entry's letter in white strokes."""
    color = PALETTE[hashlib.sha1(entry.package.encode()).digest()[0] % len(PALETTE)]
    ox = 48 - 4 * GLYPH_SCALE / 2
    oy = 48 - 6 * GLYPH_SCALE / 2
    path = "".join(
        "M" + "L".join(f"{_number(ox + x * GLYPH_SCALE)},{_number(oy + y * GLYPH_SCALE)}" for x, y in line)
        for line in GLYPHS[entry.letter]
    )
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="96dp"\n    android:height="96dp"\n'
        '    android:viewportWidth="96"\n    android:viewportHeight="96">\n'
        f'    <path android:fillColor="{color}" android:pathData="{ROUNDED_SQUARE}" />\n'
        '    <path\n        android:pathData="' + path + '"\n'
        '        android:strokeColor="#FFFFFF"\n'
        f'        android:strokeWidth="{STROKE_WIDTH}"\n'
        '        android:strokeLineCap="round"\n        android:strokeLineJoin="round" />\n'
        '</vector>\n'
    )


def appfilter_xml(entries):
    lines = ['<?xml version="1.0" encoding="utf-8"?>', "<resources>"]
    lines += [f'    <item component="ComponentInfo{{{e.component}}}" drawable="{e.drawable}" />' for e in entries]
    lines += ["</resources>", ""]
    return "\n".join(lines)


def find_picture(icons_dir, package):
    for suffix in PICTURE_SUFFIXES:
        candidate = Path(icons_dir) / (package + suffix)
        if candidate.is_file():
            return candidate
    return None


def expected_files(entries, icons_dir=None):
    """{relative path under res/: bytes} the pack consists of."""
    files = {"xml/appfilter.xml": appfilter_xml(entries).encode("utf-8")}
    for entry in entries:
        picture = find_picture(icons_dir, entry.package) if icons_dir else None
        if picture:
            files[f"drawable-nodpi/{entry.drawable}{picture.suffix}"] = picture.read_bytes()
        else:
            files[f"drawable/{entry.drawable}.xml"] = monogram_xml(entry).encode("utf-8")
    return files


def generated_on_disk(res):
    """Paths under res/ this script owns: ic_* drawables and xml/appfilter.xml."""
    found = set()
    for folder in ("drawable", "drawable-nodpi"):
        directory = Path(res) / folder
        if directory.is_dir():
            found.update(f"{folder}/{p.name}" for p in directory.iterdir() if p.name.startswith(PREFIX))
    if (Path(res) / "xml" / "appfilter.xml").is_file():
        found.add("xml/appfilter.xml")
    return found


def write_pack(entries, res, icons_dir=None):
    files = expected_files(entries, icons_dir)
    for stale in generated_on_disk(res):
        (Path(res) / stale).unlink()
    for relative, data in files.items():
        target = Path(res) / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
    return sorted(files)


def check_pack(entries, res, icons_dir=None):
    """Problems (a list of strings) between components.txt and the files under res/."""
    files = expected_files(entries, icons_dir)
    problems = []
    for relative, data in sorted(files.items()):
        target = Path(res) / relative
        if not target.is_file():
            problems.append(f"missing {relative}")
        elif target.read_bytes() != data:
            problems.append(f"out of date {relative}")
    problems += [f"extra {relative}" for relative in sorted(generated_on_disk(res) - set(files))]
    return problems


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--components", default=str(HERE / "components.txt"), help="the list of apps (default: components.txt)")
    parser.add_argument("--res", default=str(DEFAULT_RES), help="the res/ folder to write (default: app/src/main/res)")
    parser.add_argument("--icons", help="folder with <package>.png|webp|jpg that replace the monograms")
    parser.add_argument("--check", action="store_true", help="verify only; exit 1 on any difference")
    args = parser.parse_args(argv)

    try:
        entries = parse_components(Path(args.components).read_text(encoding="utf-8"))
    except (OSError, ValueError) as error:
        print(f"make-pack: {error}", file=sys.stderr)
        return 2
    if args.icons and not Path(args.icons).is_dir():
        print(f"make-pack: --icons {args.icons} is not a folder", file=sys.stderr)
        return 2
    if args.icons:
        listed = {e.package for e in entries}
        for picture in sorted(Path(args.icons).iterdir()):
            if picture.suffix in PICTURE_SUFFIXES and picture.stem not in listed:
                print(f"make-pack: {picture.name} has no line in {Path(args.components).name}, skipped", file=sys.stderr)

    if args.check:
        problems = check_pack(entries, args.res, args.icons)
        for problem in problems:
            print(problem, file=sys.stderr)
        if not problems:
            print(f"ok: {len(entries)} icons")
        return 1 if problems else 0

    written = write_pack(entries, args.res, args.icons)
    print(f"wrote {len(entries)} icons ({len(written)} files) under {args.res}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
