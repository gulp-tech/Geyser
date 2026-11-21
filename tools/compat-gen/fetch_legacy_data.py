#!/usr/bin/env python3
"""Downloads per-version Bedrock data files from CloudburstMC/Data into the Geyser resources.

Geyser needs, for every supported Bedrock version family, the block palette, runtime item states
and creative items that were current at that version. CloudburstMC/Data keeps every historical
release in its git history, tagged by version in the commit message, so the data can be rebuilt
without owning an old client.

Usage:
    python3 tools/compat-gen/fetch_legacy_data.py --protocols 1001 2168
    python3 tools/compat-gen/fetch_legacy_data.py --all-since 685
    python3 tools/compat-gen/fetch_legacy_data.py --protocols 1001 --dry-run
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import urllib.request
from pathlib import Path

API = "https://api.github.com"
DATA_REPO = "CloudburstMC/Data"
RESOURCE_DIR = Path("core/src/main/resources/bedrock")
REPORT_PATH = Path("docs/bedrock-data-report.md")

# files Geyser needs per version (name in CloudburstMC/Data -> name suffix pattern in Geyser)
FILES = {
    "block_palette.nbt": "block_palette.{suffix}.nbt",
    "runtime_item_states.json": "runtime_item_states.{suffix}.json",
    "creative_items.json": "creative_items.{suffix}.json",
    "item_components.nbt": "item_components.{suffix}.nbt",
    # Never overwrite the shared "latest" file Geyser ships with
    "entity_identifiers.dat": "entity_identifiers.{suffix}.dat",
}

PROTOCOL_IN_MESSAGE = re.compile(r"(?:\(|v)(\d{3,4})\)?", re.IGNORECASE)
VERSION_IN_MESSAGE = re.compile(r"\b(\d{1,2}\.\d{1,2}(?:\.\d{1,2})?)\b")


def api(url: str) -> dict | list:
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json",
                                                   "User-Agent": "geyser-compat-gen"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def commit_index(pages: int = 6) -> dict[int, str]:
    """Maps protocol version -> commit sha, picking the newest commit that mentions it."""
    index: dict[int, str] = {}
    for page in range(1, pages + 1):
        commits = api(f"{API}/repos/{DATA_REPO}/commits?per_page=100&page={page}")
        if not isinstance(commits, list) or not commits:
            break
        for commit in commits:
            message = commit["commit"]["message"].split("\n")[0]
            for match in PROTOCOL_IN_MESSAGE.finditer(message):
                protocol = int(match.group(1))
                # Only trust plausible Bedrock protocol numbers
                if 291 <= protocol <= 3000:
                    index.setdefault(protocol, commit["sha"])
    return index


def version_index() -> dict[str, int]:
    table = json.load(open("core/src/main/resources/compat/bedrock-versions.json", encoding="utf-8"))
    return {entry["version"]: entry["protocol"] for entry in table["versions"]}


def file_at(commit: str, path: str) -> bytes | None:
    try:
        payload = api(f"{API}/repos/{DATA_REPO}/contents/{path}?ref={commit}")
    except urllib.error.HTTPError:
        return None
    if "content" not in payload:
        return None
    return base64.b64decode(payload["content"])


def suffix_for(version: str) -> str:
    # Geyser file naming: 1.26.30 -> "26_30"
    parts = version.split(".")
    if parts[0] == "1" and len(parts) >= 3:
        parts = parts[1:]
    return "_".join(parts[:2])


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--protocols", nargs="*", type=int, default=[])
    parser.add_argument("--all-since", type=int, default=None)
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    versions = version_index()
    protocols = args.protocols
    if args.all_since is not None:
        protocols = [protocol for protocol in versions.values() if protocol >= args.all_since]
    if not protocols:
        parser.error("nothing to fetch: pass --protocols or --all-since")

    commits = commit_index()
    report: list[tuple[str, int, str, list[str]]] = []

    for protocol in sorted(set(protocols)):
        version = next((name for name, value in versions.items() if value == protocol), None)
        if version is None:
            print(f"! protocol {protocol}: not in compat/bedrock-versions.json, skipped", file=sys.stderr)
            continue
        commit = commits.get(protocol)
        if commit is None:
            print(f"! {version} ({protocol}): no CloudburstMC/Data commit found", file=sys.stderr)
            report.append((version, protocol, "-", []))
            continue

        suffix = suffix_for(version)
        written: list[str] = []
        for source, target_pattern in FILES.items():
            target = RESOURCE_DIR / target_pattern.format(suffix=suffix)
            data = file_at(commit, source)
            if data is None:
                print(f"  {version}: {source} missing at {commit[:8]}", file=sys.stderr)
                continue
            if not args.dry_run:
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            written.append(target.name)
            print(f"  {version}: {source} -> {target} ({len(data)} bytes)")
        report.append((version, protocol, commit, written))

    if not args.dry_run:
        REPORT_PATH.parent.mkdir(parents=True, exist_ok=True)
        lines = ["# Данные Bedrock по версиям", "",
                 "Сгенерировано `tools/compat-gen/fetch_legacy_data.py` из истории `CloudburstMC/Data`.", "",
                 "| Версия | Протокол | Коммит данных | Файлы |", "|---|---|---|---|"]
        for version, protocol, commit, written in report:
            files = ", ".join(f"`{name}`" for name in written) if written else "—"
            lines.append(f"| {version} | {protocol} | `{commit[:8] if commit != '-' else '-'}` | {files} |")
        REPORT_PATH.write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(f"Report written to {REPORT_PATH}", file=sys.stderr)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
