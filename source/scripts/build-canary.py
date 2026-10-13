#!/usr/bin/env python3
"""Compatibility entry point; multi-version builds are configured in Gradle."""

import subprocess
from pathlib import Path

if __name__ == "__main__":
    root = Path(__file__).resolve().parents[1]
    raise SystemExit(subprocess.call([str(root / "gradlew"), "clean", "build"], cwd=root))
