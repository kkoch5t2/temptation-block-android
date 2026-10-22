#!/usr/bin/env python3
"""Run the standalone Java URL, tab matching and usage policy regressions."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/jp/personal/entertainmentguard"
with tempfile.TemporaryDirectory(prefix="guard-core-") as directory:
    sources = [src / (name + ".java") for name in
               ("Classifier", "BlockedTabMatcher", "UsageLimit", "CooldownPolicy")]
    sources += [root / name for name in
                ("ClassifierTest.java", "BlockedTabMatcherTest.java", "UsageLimitTest.java")]
    subprocess.run(["javac", "-d", directory, *map(str, sources)], check=True)
    for name in ("ClassifierTest", "BlockedTabMatcherTest", "UsageLimitTest"):
        subprocess.run(["java", "-cp", directory,
                        "jp.personal.entertainmentguard." + name], check=True)
