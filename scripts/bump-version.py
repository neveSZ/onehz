#!/usr/bin/env python3
"""Bump version.properties in place and keep fastlane changelogs in sync.

Examples:
    python scripts/bump-version.py 1.3.0
    python scripts/bump-version.py --part minor
    python scripts/bump-version.py --part patch --changelog "Fixes and polish"

The versionCode layout is M mm (pp+1) bb, matching releases since 1.0.0:
1.2.0 -> 1020100, 1.2.1 -> 1020200, 1.3.0 -> 1030100.
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
FASTLANE = ROOT / "fastlane" / "metadata" / "android"

SEMVER = re.compile(
    r"^(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<pre>[0-9A-Za-z.-]+))?$"
)
MAX_CHANGELOG = 500


def read_version() -> tuple[str, int]:
    values: dict[str, str] = {}
    for raw in VERSION_FILE.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line and not line.startswith("#") and "=" in line:
            key, value = (part.strip() for part in line.split("=", 1))
            values[key] = value
    return values.get("versionName", ""), int(values.get("versionCode", "0"))


def bump_part(current: str, part: str) -> str:
    match = SEMVER.match(current)
    if not match:
        raise SystemExit(f"current version {current!r} is not semantic versioning")
    major, minor, patch = (int(match.group(name)) for name in ("major", "minor", "patch"))
    if part == "major":
        return f"{major + 1}.0.0"
    if part == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def compute_code(version: str) -> int:
    match = SEMVER.match(version)
    if not match:
        raise SystemExit(f"new version {version!r} is not semantic versioning")
    major, minor, patch = (int(match.group(name)) for name in ("major", "minor", "patch"))
    pre = match.group("pre")
    if pre is None:
        return major * 1_000_000 + minor * 10_000 + (patch + 1) * 100
    tail = re.search(r"(\d+)$", pre)
    if tail is None:
        raise SystemExit(f"prerelease {pre!r} must end with a number")
    index = int(tail.group(1))
    if not 1 <= index <= 99:
        raise SystemExit("prerelease number must be between 1 and 99")
    return major * 1_000_000 + minor * 10_000 + patch * 100 + index


def write_version(version: str, code: int) -> None:
    text = VERSION_FILE.read_text(encoding="utf-8")
    if re.search(r"^versionName=", text, flags=re.M):
        text = re.sub(r"^versionName=.*$", f"versionName={version}", text, flags=re.M)
    else:
        text = f"versionName={version}\n" + text
    if re.search(r"^versionCode=", text, flags=re.M):
        text = re.sub(r"^versionCode=.*$", f"versionCode={code}", text, flags=re.M)
    else:
        text += f"\nversionCode={code}"
    if not text.endswith("\n"):
        text += "\n"
    VERSION_FILE.write_text(text, encoding="utf-8")


def write_changelogs(code: int, message: str) -> list[Path]:
    written: list[Path] = []
    if len(message) > MAX_CHANGELOG:
        raise SystemExit(f"changelog is {len(message)} characters (max {MAX_CHANGELOG})")
    for locale in sorted(p for p in FASTLANE.glob("*/changelogs")):
        target = locale / f"{code}.txt"
        if target.exists():
            print(f"keeping existing {target.relative_to(ROOT)}")
            continue
        target.write_text(message + "\n", encoding="utf-8")
        written.append(target)
    return written


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("version", nargs="?", help="explicit target version, e.g. 1.3.0")
    parser.add_argument("--part", choices=("major", "minor", "patch"), default=None)
    parser.add_argument("--changelog", default="", help="text for fastlane changelogs")
    parser.add_argument(
        "--dry-run", action="store_true", help="print the result without writing files"
    )
    args = parser.parse_args()

    if bool(args.version) == bool(args.part):
        parser.error("pass either a version or --part")

    current_name, current_code = read_version()
    target = args.version or bump_part(current_name, args.part or "patch")
    code = compute_code(target)

    print(f"current: {current_name} ({current_code})")
    print(f"target:  {target} ({code})")

    match = SEMVER.match(target)
    if match and match.group("pre") is None:
        print(f"tag to create: v{target}")
    if code <= current_code:
        print(
            f"ERROR: versionCode {code} is not greater than the current {current_code}",
            file=sys.stderr,
        )
        return 1

    if args.dry_run:
        print("dry run: no files written")
        return 0

    write_version(target, code)
    if args.changelog:
        for path in write_changelogs(code, args.changelog):
            print(f"wrote {path.relative_to(ROOT)}")
    print(
        "next: update docs/releases/<version>.md and the signature verification table, "
        "then run scripts/validate-version.py"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
