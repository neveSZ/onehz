#!/usr/bin/env python3
"""Validate the fastlane metadata used by F-Droid and IzzyOnDroid.

The script intentionally has no third-party dependencies: it only uses the
standard library so it can run both locally and in GitHub Actions.
"""

from __future__ import annotations

import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FASTLANE = ROOT / "fastlane" / "metadata" / "android"
VERSION_FILE = ROOT / "version.properties"

MIN_SCREENSHOT_SIDE = 320
MAX_SCREENSHOT_SIDE = 3840
MAX_SCREENSHOT_ASPECT = 2.0
MAX_SCREENSHOTS = 8
MIN_SCREENSHOTS = 2
ICON_SIDE = 512
MAX_TITLE = 30  # IzzyOnDroid limit; F-Droid accepts up to 50
MAX_SHORT = 80
MAX_FULL = 4000
MAX_CHANGELOG = 500

errors: list[str] = []
warnings: list[str] = []


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8").strip()


def parse_properties(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip()
    return values


def jpeg_size(path: Path) -> tuple[int, int] | None:
    with path.open("rb") as f:
        if f.read(2) != b"\xff\xd8":
            return None
        while True:
            byte = f.read(1)
            if not byte:
                return None
            if byte != b"\xff":
                continue
            marker = f.read(1)
            while marker == b"\xff":
                marker = f.read(1)
            if not marker:
                return None
            code = marker[0]
            if code in (0xD8, 0xD9):
                continue
            length_bytes = f.read(2)
            if len(length_bytes) != 2:
                return None
            length = struct.unpack(">H", length_bytes)[0]
            if length < 2:
                return None
            if code in (0xC0, 0xC1, 0xC2, 0xC3, 0xC5, 0xC6, 0xC7,
                        0xC9, 0xCA, 0xCB, 0xCD, 0xCE, 0xCF):
                f.read(1)
                height, width = struct.unpack(">HH", f.read(4))
                return width, height
            f.seek(length - 2, 1)


def image_size(path: Path) -> tuple[int, int] | None:
    with path.open("rb") as f:
        head = f.read(24)
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        if head[12:16] != b"IHDR":
            return None
        return struct.unpack(">II", head[16:24])
    if head.startswith(b"\xff\xd8"):
        return jpeg_size(path)
    return None


def check_text(locale: str, filename: str, limit: int, required: bool) -> str | None:
    path = FASTLANE / locale / filename
    if not path.is_file():
        if required:
            errors.append(f"{locale}: missing {filename}")
        else:
            warnings.append(f"{locale}: missing optional {filename}")
        return None
    text = read_text(path)
    if not text:
        errors.append(f"{locale}: {filename} is empty")
        return None
    if len(text) > limit:
        errors.append(
            f"{locale}: {filename} has {len(text)} characters (max {limit})"
        )
    return text


def check_locale(locale: str, version_code: str, required: bool) -> None:
    locale_dir = FASTLANE / locale
    if not locale_dir.is_dir():
        if required:
            errors.append(f"missing required locale directory: {locale}")
        else:
            warnings.append(f"missing optional locale directory: {locale}")
        return

    title = check_text(locale, "title.txt", MAX_TITLE, required=False)
    short = check_text(locale, "short_description.txt", MAX_SHORT, required=True)
    full = check_text(locale, "full_description.txt", MAX_FULL, required=True)

    if short and short.endswith("."):
        errors.append(f"{locale}: short_description.txt must not end with a period")

    icon = locale_dir / "images" / "icon.png"
    if not icon.is_file():
        errors.append(f"{locale}: missing images/icon.png")
    else:
        size = image_size(icon)
        if size is None:
            errors.append(f"{locale}: images/icon.png is not a PNG/JPEG image")
        elif size != (ICON_SIDE, ICON_SIDE):
            errors.append(
                f"{locale}: images/icon.png is {size[0]}x{size[1]} (expected "
                f"{ICON_SIDE}x{ICON_SIDE})"
            )

    screenshots_dir = locale_dir / "images" / "phoneScreenshots"
    screenshots = sorted(
        p for p in screenshots_dir.glob("*")
        if p.is_file() and p.suffix.lower() in {".png", ".jpg", ".jpeg"}
    ) if screenshots_dir.is_dir() else []
    if len(screenshots) < MIN_SCREENSHOTS:
        errors.append(
            f"{locale}: {len(screenshots)} phone screenshot(s); "
            f"at least {MIN_SCREENSHOTS} are required"
        )
    if len(screenshots) > MAX_SCREENSHOTS:
        errors.append(
            f"{locale}: {len(screenshots)} phone screenshots; "
            f"at most {MAX_SCREENSHOTS} are allowed"
        )
    for screenshot in screenshots:
        size = image_size(screenshot)
        if size is None:
            errors.append(f"{locale}: {screenshot.name} is not a PNG/JPEG image")
            continue
        width, height = size
        if width < MIN_SCREENSHOT_SIDE or height < MIN_SCREENSHOT_SIDE:
            errors.append(
                f"{locale}: {screenshot.name} is {width}x{height}; "
                f"the smallest side must be at least {MIN_SCREENSHOT_SIDE}px"
            )
        if width > MAX_SCREENSHOT_SIDE or height > MAX_SCREENSHOT_SIDE:
            errors.append(
                f"{locale}: {screenshot.name} is {width}x{height}; "
                f"the largest side must be at most {MAX_SCREENSHOT_SIDE}px"
            )
        ratio = max(width, height) / min(width, height)
        if ratio > MAX_SCREENSHOT_ASPECT:
            errors.append(
                f"{locale}: {screenshot.name} has a {ratio:.2f}:1 aspect ratio; "
                f"the maximum is {MAX_SCREENSHOT_ASPECT:.1f}:1"
            )

    changelogs = locale_dir / "changelogs"
    changelog = changelogs / f"{version_code}.txt"
    if not changelog.is_file():
        message = f"{locale}: missing changelogs/{version_code}.txt for the current version"
        if required:
            errors.append(message)
        else:
            warnings.append(message)
    if changelogs.is_dir():
        for path in sorted(changelogs.glob("*.txt")):
            text = read_text(path)
            if len(text) > MAX_CHANGELOG:
                errors.append(
                    f"{locale}: changelogs/{path.name} has {len(text)} characters "
                    f"(max {MAX_CHANGELOG})"
                )
            if len(text.encode("utf-8")) > MAX_CHANGELOG:
                errors.append(
                    f"{locale}: changelogs/{path.name} has "
                    f"{len(text.encode('utf-8'))} UTF-8 bytes (max {MAX_CHANGELOG})"
                )

    summary = [
        f"short={len(short) if short else 'missing'}",
        f"full={len(full) if full else 'missing'}",
        f"title={len(title) if title else 'missing'}",
        f"icon={'ok' if icon.is_file() else 'missing'}",
        f"screenshots={len(screenshots)}",
        f"changelog={'ok' if changelog.is_file() else 'missing'}",
    ]
    print(f"  {locale}: " + ", ".join(summary))


def main() -> int:
    print("Validating fastlane metadata...")

    if not VERSION_FILE.is_file():
        print("ERROR: version.properties not found")
        return 1
    version = parse_properties(VERSION_FILE)
    version_code = version.get("versionCode")
    version_name = version.get("versionName", "?")
    if not version_code:
        print("ERROR: version.properties has no versionCode")
        return 1
    print(f"  current version: {version_name} ({version_code})")

    if not FASTLANE.is_dir():
        print(f"ERROR: {FASTLANE.relative_to(ROOT)} not found")
        return 1

    locales = sorted(p.name for p in FASTLANE.iterdir() if p.is_dir())
    if "en-US" not in locales:
        errors.append("missing required locale directory: en-US")
    for locale in locales:
        check_locale(locale, version_code, required=locale == "en-US")

    for warning in warnings:
        print(f"WARNING: {warning}")
    for error in errors:
        print(f"ERROR: {error}")

    if errors:
        print(f"FAILED: {len(errors)} error(s), {len(warnings)} warning(s)")
        return 1

    print(f"OK: {len(locales)} locale(s), 0 errors, {len(warnings)} warning(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
