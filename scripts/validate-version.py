#!/usr/bin/env python3
"""Validate version.properties and its relation to tags and fastlane metadata.

No third-party dependencies are used so the script runs locally and in CI.
"""

from __future__ import annotations

import argparse
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
VERSION_FILE = ROOT / "version.properties"
FASTLANE = ROOT / "fastlane" / "metadata" / "android"
RELEASE_NOTES = ROOT / "docs" / "releases"

SEMVER = re.compile(
    r"^(?P<major>0|[1-9]\d*)\.(?P<minor>0|[1-9]\d*)\.(?P<patch>0|[1-9]\d*)"
    r"(?:-(?P<pre>[0-9A-Za-z.-]+))?$"
)
MAX_PRERELEASE = 99

errors: list[str] = []
warnings: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def warn(message: str) -> None:
    warnings.append(message)


def parse_properties(path: Path) -> tuple[dict[str, str], list[str]]:
    values: dict[str, str] = {}
    problems: list[str] = []
    for number, raw in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            problems.append(f"line {number} is not a key=value pair")
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key in values:
            problems.append(f"duplicate key: {key}")
        values[key] = value
    return values, problems


def stable_code(major: int, minor: int, patch: int) -> int:
    """versionCode layout: M mm (pp+1) bb (1.2.0 -> 1 02 01 00)."""
    return major * 1_000_000 + minor * 10_000 + (patch + 1) * 100


def prerelease_code(major: int, minor: int, patch: int, pre: str) -> int | None:
    """Suggested prerelease code (1.1.0-beta.1 -> 1010001)."""
    match = re.search(r"(\d+)$", pre)
    if not match:
        return None
    index = int(match.group(1))
    if index > MAX_PRERELEASE:
        return None
    return major * 1_000_000 + minor * 10_000 + patch * 100 + index


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--tag",
        default=None,
        help="release tag to compare against, e.g. v1.2.0 "
        "(defaults to GITHUB_REF_NAME when GITHUB_REF_TYPE is tag)",
    )
    args = parser.parse_args()

    print("Validating version.properties...")
    if not VERSION_FILE.is_file():
        print("ERROR: version.properties not found")
        return 1

    values, problems = parse_properties(VERSION_FILE)
    for problem in problems:
        fail(problem)

    version = values.get("versionName", "")
    code = values.get("versionCode", "")

    if not version:
        fail("versionName is missing")
    if not code:
        fail("versionCode is missing")
    if not code.isdigit():
        fail(f"versionCode must be a positive integer, got {code!r}")
    elif int(code) <= 0:
        fail(f"versionCode must be greater than zero, got {code}")

    match = SEMVER.match(version)
    if not match:
        fail(f"versionName {version!r} is not semantic versioning (X.Y.Z[-prerelease])")
    else:
        major = int(match.group("major"))
        minor = int(match.group("minor"))
        patch = int(match.group("patch"))
        pre = match.group("pre")
        if pre is None:
            expected = stable_code(major, minor, patch)
            if code.isdigit() and int(code) != expected:
                fail(
                    f"versionCode {code} does not match versionName {version}; "
                    f"expected {expected}"
                )
        else:
            expected = prerelease_code(major, minor, patch, pre)
            if expected is not None and code.isdigit() and int(code) != expected:
                warn(
                    f"versionCode {code} differs from the suggested {expected} for {version}"
                )

    if code.isdigit():
        locales = sorted(p.parent for p in FASTLANE.glob("*/changelogs"))
        if not locales:
            warn("no fastlane changelogs found; skipping changelog checks")
        for locale in locales:
            if not (locale / "changelogs" / f"{code}.txt").is_file():
                target = fail if locale.name == "en-US" else warn
                target(f"{locale.name}: missing changelogs/{code}.txt for {version}")

    if RELEASE_NOTES.is_dir() and not (RELEASE_NOTES / f"{version}.md").is_file():
        warn(f"docs/releases/{version}.md not found; release notes will be generated")

    tag = args.tag
    if tag is None and os.environ.get("GITHUB_REF_TYPE") == "tag":
        tag = os.environ.get("GITHUB_REF_NAME")
    if tag:
        expected = tag[1:] if tag.startswith("v") else tag
        if expected != version:
            fail(f"tag {tag} does not match versionName {version}")

    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}")

    if errors:
        print(f"FAILED: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1

    print(f"OK: {version} ({code})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
