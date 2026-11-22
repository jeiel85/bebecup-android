"""Writes update.json for a GitHub Release (read by the GitHub build's updater).

    python3 scripts/update_manifest.py <versionCode> <versionName> <apk> <out.json>

The app (GithubUpdateManifest.parse) accepts only exactly this shape and builds
the download URL itself from the repository, "v" + versionName and "apk".
"""
from __future__ import annotations

import hashlib
import json
import os
import re
import sys
from pathlib import Path

VERSION_NAME = re.compile(r"\d{1,3}\.\d{1,3}\.\d{1,3}")
APK_NAME = re.compile(r"[A-Za-z0-9._-]{1,80}\.apk")


def manifest(version_code: int, version_name: str, apk: Path) -> dict:
    if version_code <= 0:
        raise ValueError(f"versionCode must be positive: {version_code}")
    if not VERSION_NAME.fullmatch(version_name):
        raise ValueError(f"versionName must look like 1.2.3: {version_name}")
    if not APK_NAME.fullmatch(apk.name) or apk.name.startswith("."):
        raise ValueError(f"unexpected APK file name: {apk.name}")
    size = apk.stat().st_size
    if size <= 0:
        raise ValueError(f"APK is empty: {apk}")
    digest = hashlib.sha256()
    with apk.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)
    return {
        "versionCode": version_code,
        "versionName": version_name,
        "apk": apk.name,
        "sha256": digest.hexdigest(),
        "size": size,
    }


def main(argv: list[str]) -> int:
    if len(argv) != 4:
        print(__doc__, file=sys.stderr)
        return 2
    code, name, apk, out = argv
    data = manifest(int(code), name, Path(apk))
    tmp = f"{out}.tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(data, f)
    os.replace(tmp, out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
