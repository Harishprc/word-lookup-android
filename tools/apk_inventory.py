"""Extract a comparable inventory from an Android APK, and diff two of them.

This is the acceptance test for the clone: whatever `app-release.apk` (the
shipped v0.1.0 binary) reports here, our own build must report too. Anything
that differs is a named bug rather than a vague "looks about right".

Everything is read straight out of the APK with the standard library - no
aapt, no apktool, no JDK. Binary AndroidManifest.xml and res/*.xml are parsed
as AXML chunks; resources.arsc contributes its string pool; the dex files are
scanned for printable runs, which is enough to recover class names, SQL,
the Gemini prompt, and hardcoded UI copy from an unshrunk APK.

Usage:
    python tools/apk_inventory.py <apk-or-zip> -o reference/inventory-original.json
    python tools/apk_inventory.py --diff reference/inventory-original.json build.json
"""

from __future__ import annotations

import argparse
import hashlib
import io
import json
import re
import struct
import sys
import zipfile

# --- AXML (binary XML) ------------------------------------------------------

_CHUNK_STRING_POOL = 0x0001
_CHUNK_START_ELEMENT = 0x0102
_CHUNK_END_ELEMENT = 0x0103

# Attribute value types we care about; everything else is rendered as hex so a
# diff still catches a change we didn't teach the parser to name.
_TYPE_REFERENCE = 0x01
_TYPE_INT_DEC = 0x10
_TYPE_INT_BOOL = 0x12
_TYPE_INT_HEX = 0x11


def _string_pool(data: bytes, off: int) -> tuple[list[str], int]:
    """Parse the string pool chunk at `off`; return (strings, chunk size)."""
    _typ, _hs, size = struct.unpack_from("<HHI", data, off)
    count, _styles, flags, strings_start, _ = struct.unpack_from("<IIIII", data, off + 8)
    utf8 = bool(flags & (1 << 8))
    base = off + strings_start
    offsets = struct.unpack_from("<%dI" % count, data, off + 28)
    out = []
    for o in offsets:
        p = base + o
        if utf8:
            n = data[p]
            p += 1
            if n & 0x80:
                p += 1  # skip the second byte of the char count
            m = data[p]
            p += 1
            if m & 0x80:
                m = ((m & 0x7F) << 8) | data[p]
                p += 1
            out.append(data[p:p + m].decode("utf-8", "replace"))
        else:
            n = struct.unpack_from("<H", data, p)[0]
            p += 2
            if n & 0x8000:
                n = ((n & 0x7FFF) << 16) | struct.unpack_from("<H", data, p)[0]
                p += 2
            out.append(data[p:p + n * 2].decode("utf-16-le", "replace"))
    return out, size


def parse_axml(data: bytes) -> list[dict]:
    """Return a flat list of elements: {name, depth, attrs: {name: value}}."""
    strings, pool_size = _string_pool(data, 8)
    pos = 8 + pool_size
    depth = 0
    elements: list[dict] = []
    while pos + 8 <= len(data):
        typ, _hs, size = struct.unpack_from("<HHI", data, pos)
        if size == 0:
            break
        if typ == _CHUNK_START_ELEMENT:
            ext = pos + 16
            _ns, name = struct.unpack_from("<iI", data, ext)
            attr_start, attr_size, attr_count = struct.unpack_from("<HHH", data, ext + 8)
            attrs = {}
            for i in range(attr_count):
                a = ext + attr_start + i * attr_size
                _ans, an, raw = struct.unpack_from("<iii", data, a)
                dtype = data[a + 15]
                dval = struct.unpack_from("<I", data, a + 16)[0]
                if raw >= 0:
                    value = strings[raw]
                elif dtype == _TYPE_REFERENCE:
                    value = "@0x%08x" % dval
                elif dtype == _TYPE_INT_BOOL:
                    value = "true" if dval else "false"
                elif dtype == _TYPE_INT_DEC:
                    value = str(struct.unpack("<i", struct.pack("<I", dval))[0])
                elif dtype == _TYPE_INT_HEX:
                    value = hex(dval)
                else:
                    value = hex(dval)
                key = strings[an] if 0 <= an < len(strings) else "?%d" % an
                attrs[key] = value
            elements.append({"name": strings[name], "depth": depth, "attrs": attrs})
            depth += 1
        elif typ == _CHUNK_END_ELEMENT:
            depth = max(0, depth - 1)
        pos += size
    return elements


def axml_to_text(elements: list[dict]) -> list[str]:
    """Render parsed elements as indented pseudo-XML - stable across runs, so
    it diffs cleanly."""
    lines = []
    for el in elements:
        attrs = " ".join('%s="%s"' % kv for kv in sorted(el["attrs"].items()))
        lines.append("%s<%s%s>" % ("  " * el["depth"], el["name"], (" " + attrs) if attrs else ""))
    return lines


# --- dex ---------------------------------------------------------------------

_PRINTABLE = re.compile(rb"[\x20-\x7e]{4,}")

_LANGUAGES = [
    "Kannada", "Hindi", "Tamil", "Telugu", "Malayalam", "Marathi", "Bengali",
    "Gujarati", "Punjabi", "Odia", "Urdu", "Spanish", "French", "German",
    "Swedish", "Japanese", "Korean", "Chinese", "Arabic", "Russian",
    "Portuguese", "Italian", "Turkish", "Vietnamese", "Thai", "Indonesian",
]

_SQL = re.compile(
    r"^(SELECT|INSERT|UPDATE|DELETE|CREATE TABLE|DROP TABLE)\b", re.IGNORECASE
)

# Fragments of the Gemini prompt survive as separate constants, because a
# Kotlin string template is split at every ${...} interpolation point.
_PROMPT_MARKERS = (
    "dictionary. For the English word",
    "part_of_speech",
    "example_native",
    "synonyms may be an empty list",
    "native speaker would recognise",
    "wrong alphabet",
)


def scan_dex(zf: zipfile.ZipFile, package: str) -> dict:
    app_classes: set[str] = set()
    sql: set[str] = set()
    prompt: set[str] = set()
    urls: set[str] = set()
    ui_text: set[str] = set()
    languages: set[str] = set()

    # Type descriptors sit inside a longer printable run (dex strings carry a
    # length prefix that is often itself printable), so match anywhere in the
    # run rather than at its start.
    pkg_re = re.compile(r"L%s/[A-Za-z0-9_$/]+;" % re.escape(package.replace(".", "/")))
    for name in sorted(n for n in zf.namelist() if re.fullmatch(r"classes\d*\.dex", n)):
        blob = zf.read(name)
        for lang in _LANGUAGES:
            if lang.encode() in blob:
                languages.add(lang)
        for match in _PRINTABLE.finditer(blob):
            s = match.group().decode()
            for hit in pkg_re.findall(s):
                app_classes.add(hit[1:-1].replace("/", "."))
            stripped = s.lstrip("!\"#$%&'()*+,-./0123456789:;<=>?@")
            if _SQL.match(stripped) and "lookups" in stripped:
                sql.add(stripped.strip())
            if any(m in s for m in _PROMPT_MARKERS):
                prompt.add(s)
            if s.startswith(("http://", "https://")) and "schemas.android" not in s:
                urls.add(s)
            # Hardcoded UI copy: sentence-shaped, not a library identifier.
            if (
                20 < len(s) < 200
                and " " in s
                and s[0].isupper()
                and not any(bad in s for bad in ("androidx", "kotlin", "java.", "Lcom", "()", "$"))
                and re.fullmatch(r"[A-Za-z0-9 ,.'\"()—→:;/?!’-]+", s)
            ):
                ui_text.add(s)

    return {
        "app_classes": sorted(app_classes),
        "sql": sorted(sql),
        "prompt_fragments": sorted(prompt),
        "urls": sorted(urls),
        "languages": sorted(languages),
        "ui_text": sorted(ui_text),
    }


# --- resources ---------------------------------------------------------------

def app_string_resources(zf: zipfile.ZipFile) -> list[str]:
    """String-pool entries of resources.arsc that are app copy rather than
    bundled Material/Compose strings. Heuristic but stable."""
    try:
        data = zf.read("resources.arsc")
    except KeyError:
        return []
    strings, _ = _string_pool(data, 12)
    return sorted(
        s for s in strings
        if len(s) > 2
        and not s.startswith(("res/", "@", "androidx"))
        and ("Word Lookup" in s or len(s) > 90)
    )


# --- signing -----------------------------------------------------------------

_SIG_MAGIC = b"APK Sig Block 42"
_BLOCK_NAMES = {0x7109871A: "v2", 0xF05368C0: "v3", 0x42726577: "padding",
                0x504B4453: "dependency-info", 0x6DFF800D: "source-stamp"}


def signing_info(raw: bytes) -> dict:
    i = raw.rfind(_SIG_MAGIC)
    if i < 0:
        return {"signed": False}
    size2 = struct.unpack_from("<Q", raw, i - 8)[0]
    block = raw[i + 16 - 8 - size2:i]
    ids = []
    p = 8
    while p < len(block) - 8:
        ln = struct.unpack_from("<Q", block, p)[0]
        if ln < 4 or p + 8 + ln > len(block):
            break
        ids.append(_BLOCK_NAMES.get(struct.unpack_from("<I", block, p + 8)[0], "unknown"))
        p += 8 + ln
    out = {"signed": True, "blocks": ids}
    # The certificate is a DER SEQUENCE; find the first plausible one and read
    # its printable RDN fragments. Enough to tell a debug key from a real one.
    for j in range(len(block) - 4):
        if block[j] == 0x30 and block[j + 1] == 0x82:
            length = struct.unpack_from(">H", block, j + 2)[0]
            if 400 < length < 2000 and j + 4 + length <= len(block):
                cert = block[j:j + 4 + length]
                if b"\x06\x03U\x04\x03" in cert:  # OID 2.5.4.3 (commonName)
                    out["cert_sha256"] = hashlib.sha256(cert).hexdigest()
                    out["cert_rdn"] = [
                        m.group().decode()
                        for m in re.finditer(rb"[A-Za-z][A-Za-z0-9 .]{3,40}", cert)
                    ][:6]
                    break
    return out


# --- inventory ---------------------------------------------------------------

def _manifest_summary(elements: list[dict]) -> dict:
    manifest = next((e for e in elements if e["name"] == "manifest"), {"attrs": {}})
    uses_sdk = next((e for e in elements if e["name"] == "uses-sdk"), {"attrs": {}})
    perms = sorted(
        e["attrs"].get("name", "") for e in elements if e["name"] == "uses-permission"
    )
    components = {kind: sorted(
        e["attrs"].get("name", "") for e in elements if e["name"] == kind
    ) for kind in ("activity", "service", "receiver", "provider")}
    return {
        "package": manifest["attrs"].get("package"),
        "versionCode": manifest["attrs"].get("versionCode"),
        "versionName": manifest["attrs"].get("versionName"),
        "compileSdkVersion": manifest["attrs"].get("compileSdkVersion"),
        "minSdkVersion": uses_sdk["attrs"].get("minSdkVersion"),
        "targetSdkVersion": uses_sdk["attrs"].get("targetSdkVersion"),
        "permissions": perms,
        "components": components,
    }


def _accessibility_config(zf: zipfile.ZipFile) -> dict:
    for name in zf.namelist():
        if not name.endswith(".xml") or name == "AndroidManifest.xml":
            continue
        try:
            elements = parse_axml(zf.read(name))
        except Exception:
            continue
        for el in elements:
            if el["name"] == "accessibility-service":
                return el["attrs"]
    return {}


def _dependencies(zf: zipfile.ZipFile) -> dict:
    out = {}
    for name in zf.namelist():
        if name.startswith("META-INF/") and name.endswith(".version"):
            value = zf.read(name).decode("utf-8", "replace").strip()
            if len(value) < 40:  # skip the odd Gradle task-name placeholder
                out[name[len("META-INF/"):-len(".version")]] = value
    return out


def open_apk(path: str) -> tuple[bytes, str]:
    """Return (apk bytes, label). Accepts an .apk or a .zip containing one."""
    with open(path, "rb") as fh:
        blob = fh.read()
    if path.lower().endswith(".apk"):
        return blob, path
    zf = zipfile.ZipFile(io.BytesIO(blob))
    inner = [n for n in zf.namelist() if n.lower().endswith(".apk")]
    if not inner:
        sys.exit("no .apk inside %s" % path)
    return zf.read(inner[0]), "%s!%s" % (path, inner[0])


def inventory(path: str) -> dict:
    raw, label = open_apk(path)
    zf = zipfile.ZipFile(io.BytesIO(raw))
    elements = parse_axml(zf.read("AndroidManifest.xml"))
    manifest = _manifest_summary(elements)
    package = manifest["package"] or "com.harish.wordlookup"
    return {
        "source": label,
        "apk_sha256": hashlib.sha256(raw).hexdigest(),
        "apk_bytes": len(raw),
        "manifest": manifest,
        "manifest_xml": axml_to_text(elements),
        "accessibility_config": _accessibility_config(zf),
        "dependencies": _dependencies(zf),
        "native_libs": sorted(n for n in zf.namelist() if n.startswith("lib/")),
        "resources_app_strings": app_string_resources(zf),
        "dex": scan_dex(zf, package),
        "signing": signing_info(raw),
    }


# --- diff --------------------------------------------------------------------

# Keys compared for parity. apk_sha256/apk_bytes/source/signing are expected to
# differ between the original and our build, so they are reported, not failed.
_COMPARED = [
    "manifest", "manifest_xml", "accessibility_config", "dependencies",
    "resources_app_strings", "dex",
]


def _flatten(value, prefix=""):
    if isinstance(value, dict):
        for k, v in value.items():
            yield from _flatten(v, "%s.%s" % (prefix, k) if prefix else k)
    elif isinstance(value, list):
        yield prefix, value
    else:
        yield prefix, value


def diff(left: dict, right: dict) -> int:
    problems = 0
    for key in _COMPARED:
        lf = dict(_flatten(left.get(key), key))
        rf = dict(_flatten(right.get(key), key))
        for field in sorted(set(lf) | set(rf)):
            a, b = lf.get(field), rf.get(field)
            if a == b:
                continue
            problems += 1
            if isinstance(a, list) or isinstance(b, list):
                a, b = a or [], b or []
                missing = [x for x in a if x not in b]
                extra = [x for x in b if x not in a]
                print("~ %s" % field)
                for x in missing[:20]:
                    print("    - %s" % x)
                for x in extra[:20]:
                    print("    + %s" % x)
                if len(missing) > 20 or len(extra) > 20:
                    print("    ... %d missing, %d extra" % (len(missing), len(extra)))
            else:
                print("~ %s\n    - %r\n    + %r" % (field, a, b))
    print()
    print("%d difference(s)" % problems if problems else "identical on every compared key")
    return problems


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("path", nargs="?", help=".apk, or .zip containing one")
    ap.add_argument("-o", "--out", help="write inventory JSON here")
    ap.add_argument("--diff", nargs=2, metavar=("BASE", "OURS"),
                    help="compare two inventory JSON files")
    args = ap.parse_args()

    if args.diff:
        with open(args.diff[0], encoding="utf-8") as fh:
            base = json.load(fh)
        with open(args.diff[1], encoding="utf-8") as fh:
            ours = json.load(fh)
        return 1 if diff(base, ours) else 0

    if not args.path:
        ap.error("give an APK path, or --diff two inventories")

    data = inventory(args.path)
    text = json.dumps(data, indent=2, ensure_ascii=False)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(text + "\n")
        m = data["manifest"]
        print("%s %s (versionCode %s), minSdk %s target %s"
              % (m["package"], m["versionName"], m["versionCode"],
                 m["minSdkVersion"], m["targetSdkVersion"]))
        print("%d deps, %d app classes, %d SQL, %d languages -> %s"
              % (len(data["dependencies"]), len(data["dex"]["app_classes"]),
                 len(data["dex"]["sql"]), len(data["dex"]["languages"]), args.out))
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
