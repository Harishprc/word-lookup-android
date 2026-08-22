#!/usr/bin/env python3
"""Generate one adaptive launcher icon per language, glyph taken from
data/Languages.kt, matching the app's monochrome brand mark (ink rounded
square, light glyph). Outputs are additive: the original v0.1.0 recovered
ic_launcher_background.xml / ic_launcher_foreground.xml are never touched.

Font per script was chosen by rendering a proof sheet first
(scratchpad/icon-font-proof.png) - not guessed. One language (Indonesian)
has no real outline available on this machine for its Balinese glyph
(every locally-installed font that claims cmap coverage for U+1B05 renders
an empty "tofu" box, confirmed visually) - its launcher icon uses the
Latin fallback "ID" instead. This is a build-environment limitation, not a
design choice: the in-app brand mark and the Quick Settings tile render
their glyph live on-device via Android's own font stack (which does carry
Noto Sans Balinese as a system fallback) and keep the real "\u1b05" glyph.

Usage:
    python tools/gen_lang_icons.py
"""
import os

from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.boundsPen import BoundsPen
from fontTools.ttLib import TTFont, TTCollection

REPO = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(REPO, "app", "src", "main", "res")

VIEWPORT = 108
# Keep glyph artwork inside the adaptive-icon safe zone (roughly the inner
# 66dp of a 108dp viewport). First real-device screenshot (round 7 feedback)
# showed 60 reading as too large and, combined with bbox-centroid centering
# on an asymmetric glyph, visibly off-center - 44 leaves enough margin
# inside the safe circle that any residual centroid-vs-optical-center
# mismatch stops being visible.
TARGET_SPAN = 44.0

ARIAL = "C:/Windows/Fonts/arial.ttf"
NIRMALA = "C:/Windows/Fonts/Nirmala.ttc"
MALGUN = "C:/Windows/Fonts/malgun.ttf"
LEELA_BOLD = "C:/Windows/Fonts/LeelaUIb.ttf"

# (language name, code, glyph-or-text, font path, font index, is_fallback_text)
LANGUAGES = [
    ("Kannada", "kn", "\u0c95", NIRMALA, 0, False),
    ("Hindi", "hi", "\u0905", NIRMALA, 0, False),
    ("Tamil", "ta", "\u0ba4", NIRMALA, 0, False),
    ("Telugu", "te", "\u0c24", NIRMALA, 0, False),
    ("Malayalam", "ml", "\u0d2e", NIRMALA, 0, False),
    ("Marathi", "mr", "\u092e", NIRMALA, 0, False),
    ("Bengali", "bn", "\u09ac", NIRMALA, 0, False),
    ("Gujarati", "gu", "\u0a97", NIRMALA, 0, False),
    ("Punjabi", "pa", "\u0aaa", NIRMALA, 0, False),
    ("Odia", "or", "\u0b13", NIRMALA, 0, False),
    ("Urdu", "ur", "\u0627", ARIAL, 0, False),
    ("Spanish", "es", "\u00d1", ARIAL, 0, False),
    ("French", "fr", "\u00c7", ARIAL, 0, False),
    ("German", "de", "\u00df", ARIAL, 0, False),
    ("Swedish", "sv", "\u00c5", ARIAL, 0, False),
    ("Japanese", "ja", "\u3042", MALGUN, 0, False),
    ("Korean", "ko", "\ud55c", MALGUN, 0, False),
    ("Chinese", "zh", "\u4e2d", MALGUN, 0, False),
    ("Arabic", "ar", "\u0639", ARIAL, 0, False),
    ("Russian", "ru", "\u042f", ARIAL, 0, False),
    ("Portuguese", "pt", "\u00c3", ARIAL, 0, False),
    ("Italian", "it", "\u00c8", ARIAL, 0, False),
    ("Turkish", "tr", "\u015e", ARIAL, 0, False),
    ("Vietnamese", "vi", "\u01a1", ARIAL, 0, False),
    ("Thai", "th", "\u0e17", LEELA_BOLD, 0, False),
    # Fallback: no font on this machine has a real Balinese outline for
    # U+1B05 - every cmap "hit" renders an empty tofu box. See module docstring.
    ("Indonesian", "id", "ID", ARIAL, 0, True),
]


def load_font(path, index):
    if path.lower().endswith(".ttc"):
        return TTCollection(path).fonts[index]
    return TTFont(path, fontNumber=0)


def glyph_path_data(font, text):
    """Returns Android vector pathData for `text` (one glyph, or a short
    Latin fallback string), transformed into a centered 108x108 viewport
    with the font's y-up coordinate system flipped to the y-down convention
    vector drawables (and SVG) use.
    """
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()

    # Build one combined bounds + path across all characters in `text` (only
    # ever >1 character for the "ID" fallback), laid out left-to-right using
    # each glyph's own advance width.
    bounds_pen = BoundsPen(glyph_set)
    x_cursor = 0
    glyph_names = []
    advances = []
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            raise ValueError(f"font has no glyph for U+{ord(ch):04X} ({ch!r})")
        glyph_names.append(gname)
        glyph = glyph_set[gname]
        advances.append(glyph.width)
        tpen = TransformPen(bounds_pen, (1, 0, 0, 1, x_cursor, 0))
        glyph.draw(tpen)
        x_cursor += glyph.width

    xmin, ymin, xmax, ymax = bounds_pen.bounds
    width, height = xmax - xmin, ymax - ymin
    scale = TARGET_SPAN / max(width, height)
    cx, cy = (xmin + xmax) / 2, (ymin + ymax) / 2
    tx = VIEWPORT / 2 - scale * cx
    ty = VIEWPORT / 2 + scale * cy  # y flip: y' = -scale*y + ty

    svg_pen = SVGPathPen(glyph_set)
    x_cursor = 0
    for gname, advance in zip(glyph_names, advances):
        glyph = glyph_set[gname]
        transform = (scale, 0, 0, -scale, tx + scale * x_cursor, ty)
        tpen = TransformPen(svg_pen, transform)
        glyph.draw(tpen)
        x_cursor += advance

    return svg_pen.getCommands()


FOREGROUND_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_lang_icons.py - do not hand-edit, regenerate instead. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:pathData="{path_data}"
        android:fillColor="#FFFFFFFF" />
</vector>
"""

MIPMAP_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_lang_icons.py - do not hand-edit, regenerate instead. -->
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_lang_background" />
    <foreground android:drawable="@drawable/ic_lang_{code}" />
</adaptive-icon>
"""

BACKGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<!-- Generated by tools/gen_lang_icons.py. Shared by every per-language
     launcher icon; the original ic_launcher_background.xml (violet, v0.1.0
     recovered) is untouched and stays the base app icon's background. -->
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#FF16181C" />
</shape>
"""


def main():
    drawable_dir = os.path.join(RES, "drawable")
    mipmap_dir = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(drawable_dir, exist_ok=True)
    os.makedirs(mipmap_dir, exist_ok=True)

    with open(os.path.join(drawable_dir, "ic_launcher_lang_background.xml"), "w", encoding="utf-8") as f:
        f.write(BACKGROUND_XML)

    font_cache = {}
    written = []
    for name, code, text, font_path, font_index, is_fallback in LANGUAGES:
        key = (font_path, font_index)
        if key not in font_cache:
            font_cache[key] = load_font(font_path, font_index)
        font = font_cache[key]

        path_data = glyph_path_data(font, text)
        if not path_data.strip():
            raise SystemExit(f"{name} ({code}): produced empty path data - aborting, nothing written for it")

        fg_path = os.path.join(drawable_dir, f"ic_lang_{code}.xml")
        with open(fg_path, "w", encoding="utf-8") as f:
            f.write(FOREGROUND_TEMPLATE.format(path_data=path_data))

        mip_path = os.path.join(mipmap_dir, f"ic_launcher_{code}.xml")
        with open(mip_path, "w", encoding="utf-8") as f:
            f.write(MIPMAP_TEMPLATE.format(code=code))

        written.append((name, code, is_fallback))

    print(f"Wrote {len(written)} language icons + 1 shared background:")
    for name, code, is_fallback in written:
        tag = " (Latin fallback, see docstring)" if is_fallback else ""
        print(f"  {code:3} {name}{tag}")


if __name__ == "__main__":
    main()
