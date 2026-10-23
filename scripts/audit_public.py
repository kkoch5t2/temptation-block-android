#!/usr/bin/env python3
"""Inspect staged blobs or a named commit; report paths/rules, never matched values."""
import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import PurePosixPath

def git(*args):
    return subprocess.check_output(["git", *args])

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--ref", help="inspect all files at this commit instead of the index")
args = parser.parse_args()
if args.ref:
    listing = git("ls-tree", "-r", "-z", args.ref).split(b"\0")
    entries = [(row.split(b"\t", 1)[1].decode(), row.split()[2].decode(),
                row.split()[0].decode()) for row in listing if row]
else:
    listing = git("ls-files", "--stage", "-z").split(b"\0")
    entries = []
    for row in listing:
        if not row:
            continue
        header, path = row.split(b"\t", 1)
        mode, oid, stage = header.decode().split()
        if stage != "0":
            sys.exit("FAIL unresolved index entries")
        entries.append((path.decode(), oid, mode))

rules = {
    "private-key": r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |ENCRYPTED )?PRIVATE KEY",
    "github-token": r"(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})",
    "cloud-access-key": r"\b(?:AKIA|ASIA)[A-Z0-9]{16}\b",
    "google-api-key": r"AIza[A-Za-z0-9_-]{30,}",
    "api-secret": r"\bsk-(?:proj-)?[A-Za-z0-9_-]{30,}",
    "private-home-path": r"/(?:home|Users)/[A-Za-z0-9_.-]+/",
    "windows-user-path": r"[A-Za-z]:[\\/]+Users[\\/]+[A-Za-z0-9_.-]+",
    "private-network-address": r"\b(?:192\.168\.\d{1,3}\.\d{1,3}|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3})\b",
    "literal-credential": r"""(?i)(?:password|passwd|api[_-]?key|access[_-]?token|client[_-]?secret)\s*[:=]\s*["'][A-Za-z0-9_+/=-]{12,}["']""",
}
email = re.compile(r"[A-Za-z0-9._%+-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})")
safe_email_domains = {"example.com", "example.org", "example.net", "evil.test"}
bad_paths = {"backups", "private", "device-data", "test-reports", "screenshots",
             "captures", ".android", ".ssh", ".git", ".gradle", "build"}
bad_names = {"local.properties", "key.properties", "keystore.properties",
             "signing.properties", "google-services.json"}
bad_extensions = {".apk", ".aab", ".apks", ".jks", ".keystore", ".p12", ".pfx",
                  ".pem", ".key", ".log", ".zip"}
# Only these individually reviewed, app-only PNG assets may bypass the binary ban.
# Update the hash only after visually reviewing the exact replacement image.
approved_readme_images = {
    "docs/images/groups.png": "2d4da857d61e65c68ca23652ee3eee0a0cefb2a77abb71aca9298b0f1c9e0b4b",
    "docs/images/home.png": "b5b42dbd12537702408d34410122a147bbaead42b6ff2642e40da0de5fb127ca",
    "docs/images/new-apps.png": "39832b72a3ed2e4f8a6bc7e6f3792214c11cf1520655cf61db90aea44507bb36",
    "docs/images/settings.png": "3ea5058fbd26576f4dfe22b8cbcad721efee9371dca61402a0d9e0537c35a74a",
    "docs/images/strict-mode.png": "3ef4fe5f402ee8e40c8167f8b00c771d4287948c5402b3587cebc6ae2daea2a3",
}
failures = []
for name, oid, mode in entries:
    path = PurePosixPath(name)
    if (any(part in bad_paths for part in path.parts)
            or path.name in bad_names or path.suffix.lower() in bad_extensions
            or path.name.startswith("adbkey")
            or path.name == ".env"
            or (path.name.startswith(".env.") and path.name != ".env.example")):
        failures.append((name, "excluded-file"))
    if mode not in {"100644", "100755"}:
        failures.append((name, "non-regular-file"))
        continue
    data = git("cat-file", "blob", oid)
    if name in approved_readme_images:
        if (not data.startswith(b"\x89PNG\r\n\x1a\n")
                or hashlib.sha256(data).hexdigest() != approved_readme_images[name]):
            failures.append((name, "unapproved-documentation-image"))
        continue
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError:
        failures.append((name, "binary-requires-review"))
        continue
    for label, pattern in rules.items():
        if re.search(pattern, text):
            failures.append((name, label))
    for match in email.finditer(text):
        domain = match.group(1).lower()
        if domain not in safe_email_domains and not domain.endswith(".test"):
            failures.append((name, "email-address"))
            break
for name, label in sorted(set(failures)):
    print("FAIL", name, label)
if failures:
    sys.exit(1)
print(f"PASS public information audit ({len(entries)} files)")
