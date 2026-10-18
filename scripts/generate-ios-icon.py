#!/usr/bin/env python3
"""Draws the iOS app icons from the Android launcher icon, so there is one source of truth.

The Android adaptive icon (a 108-unit foreground over a background colour) becomes a full-bleed
1024 px square: iOS masks the corners itself. Only the middle 72 units are drawn, which is what a
launcher shows of an adaptive icon. Release builds get AppIcon, debug builds AppIconDebug (the
near-black debug icon), as on Android.

Needs rsvg-convert (`brew install librsvg`). Re-run after changing the Android icon.
"""

import json
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/android/src"
ASSETS = ROOT / "app/ios/Cassette/Sources/Assets.xcassets"
A = "{http://schemas.android.com/apk/res/android}"

ICONS = {
    "AppIcon": "main",
    "AppIconDebug": "debug",
}


def colour(value: str) -> tuple[str, float]:
    """Android's #RRGGBB or #AARRGGBB as an SVG colour and opacity."""
    hex_ = value.lstrip("#")
    if len(hex_) == 8:
        return "#" + hex_[2:], int(hex_[:2], 16) / 255
    return "#" + hex_, 1.0


def svg(source_set: str) -> str:
    background = ET.parse(RES / source_set / "res/values/ic_launcher.xml").find("color").text
    vector = ET.parse(RES / source_set / "res/drawable/ic_launcher_foreground.xml").getroot()
    paths = []
    for path in vector.iter("path"):
        attrs = {"d": path.get(A + "pathData")}
        fill = path.get(A + "fillColor")
        if fill:
            attrs["fill"], opacity = colour(fill)
            alpha = float(path.get(A + "fillAlpha", "1")) * opacity
            if alpha < 1:
                attrs["fill-opacity"] = f"{alpha:.3f}"
        else:
            attrs["fill"] = "none"
        stroke = path.get(A + "strokeColor")
        if stroke:
            attrs["stroke"], _ = colour(stroke)
            attrs["stroke-width"] = path.get(A + "strokeWidth", "1")
            if path.get(A + "strokeLineJoin"):
                attrs["stroke-linejoin"] = path.get(A + "strokeLineJoin")
        if path.get(A + "fillType") == "evenOdd":
            attrs["fill-rule"] = "evenodd"
        paths.append("<path " + " ".join(f'{k}="{v}"' for k, v in attrs.items()) + "/>")
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="18 18 72 72" width="1024" height="1024">'
        f'<rect x="0" y="0" width="108" height="108" fill="{background}"/>'
        + "".join(paths)
        + "</svg>"
    )


def main() -> None:
    for name, source_set in ICONS.items():
        out = ASSETS / f"{name}.appiconset"
        out.mkdir(parents=True, exist_ok=True)
        subprocess.run(
            ["rsvg-convert", "--background-color", "white", "-o", str(out / f"{name}.png")],
            input=svg(source_set).encode(),
            check=True,
        )
        contents = {
            "images": [
                {"filename": f"{name}.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}
            ],
            "info": {"author": "xcode", "version": 1},
        }
        (out / "Contents.json").write_text(json.dumps(contents, indent=2) + "\n")
        print(f"Wrote {out}")
    (ASSETS / "Contents.json").write_text(
        json.dumps({"info": {"author": "xcode", "version": 1}}, indent=2) + "\n"
    )


if __name__ == "__main__":
    main()
