"""Strip jadx boilerplate from decompiled classes so the real logic is readable.

jadx output for Kotlin is mostly noise: @Metadata blobs, Intrinsics null checks,
generated data-class members, synthetic bridge methods. This prints what a human
would have written, plus the Kotlin signature list that the @Metadata `d2` array
preserves verbatim (parameter names and all).

Usage:
    python tools/jadx_digest.py reference/jadx/sources/com/harish/wordlookup/data/GeminiProvider.java
    python tools/jadx_digest.py --sigs <file>      # only the Kotlin signatures
"""

from __future__ import annotations

import argparse
import re
import sys

_DROP_LINE = re.compile(
    r"^\s*(import |package |/\* compiled from|/\* loaded from|/\* JADX|"
    r"public static final int \$stable|Intrinsics\.check|@Metadata)"
)

_DROP_METHOD = re.compile(
    r"^\s*(public|private|protected|static|final|synthetic).*\b("
    r"component\d+|copy\$default|copy\(|equals\(|hashCode\(|toString\(|"
    r"access\$|\$values\(|valueOf\(|values\(\)|write\$Self|childSerializers|"
    r"getDescriptor|typeParametersSerializers"
    r")"
)


def kotlin_signatures(text: str) -> list[str]:
    """Pull the `d2` string array out of @Metadata - it holds the original
    Kotlin class name, member names and signatures."""
    match = re.search(r'd2\s*=\s*\{(.*?)\}\s*,\s*k\s*=', text, re.S)
    if not match:
        return []
    parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
    return [p.encode().decode("unicode_escape") for p in parts]


def digest(text: str) -> str:
    out, depth, skipping, skip_depth = [], 0, False, 0
    for line in text.splitlines():
        if _DROP_LINE.match(line):
            continue
        if not skipping and _DROP_METHOD.match(line) and "{" in line:
            skipping, skip_depth = True, depth
        opens, closes = line.count("{"), line.count("}")
        if skipping:
            depth += opens - closes
            if depth <= skip_depth and closes:
                skipping = False
            continue
        depth += opens - closes
        if line.strip():
            out.append(line)
    # squeeze blank runs
    return re.sub(r"\n{3,}", "\n\n", "\n".join(out))


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    ap = argparse.ArgumentParser()
    ap.add_argument("files", nargs="+")
    ap.add_argument("--sigs", action="store_true", help="only Kotlin signatures")
    args = ap.parse_args()
    for path in args.files:
        with open(path, encoding="utf-8", errors="replace") as fh:
            text = fh.read()
        print("=" * 70)
        print(path)
        print("=" * 70)
        sigs = kotlin_signatures(text)
        if sigs:
            print("// kotlin signatures: " + " | ".join(sigs))
        if not args.sigs:
            print(digest(text))
    return 0


if __name__ == "__main__":
    sys.exit(main())
