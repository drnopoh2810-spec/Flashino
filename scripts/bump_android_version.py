#!/usr/bin/env python3
"""
Bump Android app version in app/build.gradle.kts.

Usage:
  python scripts/bump_android_version.py --bump patch
  python scripts/bump_android_version.py --bump minor
  python scripts/bump_android_version.py --set-version 1.2.3

Outputs key=value pairs for GitHub Actions:
  version_name=<new version>
  version_code=<new version code>
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path


BUILD_GRADLE = Path(__file__).resolve().parents[1] / "app" / "build.gradle.kts"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bump", choices=["major", "minor", "patch"])
    parser.add_argument("--set-version")
    return parser.parse_args()


def parse_semver(version: str) -> tuple[int, int, int]:
    match = re.fullmatch(r"(\d+)\.(\d+)\.(\d+)", version.strip())
    if not match:
        raise ValueError(f"Invalid version format '{version}'. Expected X.Y.Z")
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def bump_version(version: str, bump_type: str) -> str:
    major, minor, patch = parse_semver(version)
    if bump_type == "major":
        return f"{major + 1}.0.0"
    if bump_type == "minor":
        return f"{major}.{minor + 1}.0"
    return f"{major}.{minor}.{patch + 1}"


def main() -> int:
    args = parse_args()
    if bool(args.bump) == bool(args.set_version):
        print("Provide exactly one of --bump or --set-version", file=sys.stderr)
        return 1

    raw = BUILD_GRADLE.read_text(encoding="utf-8")
    line_ending = "\r\n" if "\r\n" in raw else "\n"
    content = raw

    code_match = re.search(r"versionCode\s*=\s*(\d+)", content)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', content)
    if not code_match or not name_match:
        print("Could not find versionCode/versionName in app/build.gradle.kts", file=sys.stderr)
        return 1

    current_code = int(code_match.group(1))
    current_name = name_match.group(1)

    if args.set_version:
        new_name = args.set_version.strip()
        parse_semver(new_name)
    else:
        new_name = bump_version(current_name, args.bump)

    new_code = current_code + 1

    updated = re.sub(r"versionCode\s*=\s*\d+", f"versionCode = {new_code}", content, count=1)
    updated = re.sub(r'versionName\s*=\s*"[^"]+"', f'versionName = "{new_name}"', updated, count=1)
    normalized = updated.replace("\r\n", "\n")
    if line_ending == "\r\n":
        normalized = normalized.replace("\n", "\r\n")
    BUILD_GRADLE.write_text(normalized, encoding="utf-8")

    print(f"version_name={new_name}")
    print(f"version_code={new_code}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

