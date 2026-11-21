#!/usr/bin/env python3
"""Builds core/src/main/resources/compat/bedrock-versions.json.

Reads every Bedrock codec class from CloudburstMC/Protocol (branch configurable) and extracts the
protocol version and the Minecraft version string embedded in the codec. The result is the
authoritative protocol <-> version table used by the compat layer.

Usage:
    python3 tools/compat-gen/fetch_bedrock_versions.py [--branch 3.0] [--out PATH]
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import sys
import urllib.request

API = "https://api.github.com"
REPO = "CloudburstMC/Protocol"
CODEC_DIR = "bedrock-codec/src/main/java/org/cloudburstmc/protocol/bedrock/codec"

PROTOCOL_RE = re.compile(r"\.protocolVersion\((\d+)\)")
VERSION_RE = re.compile(r'\.minecraftVersion\("([^"]+)"\)')
PARENT_RE = re.compile(r"class\s+Bedrock_(\d+)\s+extends\s+Bedrock_(\d+)")


def api(url: str) -> dict:
    request = urllib.request.Request(url, headers={"Accept": "application/vnd.github+json",
                                                   "User-Agent": "geyser-compat-gen"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.load(response)


def list_codecs(branch: str) -> dict[int, str]:
    tree = api(f"{API}/repos/{REPO}/git/trees/{branch}?recursive=1")
    codecs: dict[int, str] = {}
    for entry in tree.get("tree", []):
        path = entry["path"]
        match = re.search(rf"^{re.escape(CODEC_DIR)}/v(\d+)/Bedrock_v(\d+)\.java$", path)
        if match:
            codecs[int(match.group(1))] = path
    return codecs


def read_file(path: str, branch: str) -> str:
    payload = api(f"{API}/repos/{REPO}/contents/{path}?ref={branch}")
    return base64.b64decode(payload["content"]).decode("utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--branch", default="3.0")
    parser.add_argument("--out", default="core/src/main/resources/compat/bedrock-versions.json")
    args = parser.parse_args()

    codecs = list_codecs(args.branch)
    if not codecs:
        print("No codecs found - API layout changed?", file=sys.stderr)
        return 1
    print(f"Found {len(codecs)} codec classes on branch {args.branch}", file=sys.stderr)

    protocols: dict[int, int] = {}   # codec dir -> protocol
    versions: dict[int, str] = {}    # protocol -> minecraft version
    parents: dict[int, int] = {}     # codec dir -> parent codec dir

    for codec_dir in sorted(codecs):
        source = read_file(codecs[codec_dir], args.branch)
        protocol_match = PROTOCOL_RE.search(source)
        if protocol_match:
            protocols[codec_dir] = int(protocol_match.group(1))
        version_match = VERSION_RE.search(source)
        if protocol_match and version_match:
            versions[int(protocol_match.group(1))] = version_match.group(1)
        parent_match = PARENT_RE.search(source)
        if parent_match:
            parents[codec_dir] = int(parent_match.group(2))

    # Codecs that build on a parent without re-declaring the version inherit their parent's entries
    for codec_dir in sorted(codecs):
        protocol = protocols.get(codec_dir)
        if protocol is None or protocol in versions:
            continue
        parent_dir = parents.get(codec_dir)
        if parent_dir is None:
            continue
        parent_protocol = protocols.get(parent_dir)
        if parent_protocol in versions:
            versions[protocol] = versions[parent_protocol]

    entries = [{"protocol": protocol, "version": version}
               for protocol, version in sorted(versions.items())]

    document = {
        "schema": 1,
        "source": f"https://github.com/{REPO}/tree/{args.branch}",
        "generatedBy": "tools/compat-gen/fetch_bedrock_versions.py",
        "versions": entries,
    }

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(document, handle, indent=2)
        handle.write("\n")

    print(f"Wrote {len(entries)} versions to {args.out}", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
