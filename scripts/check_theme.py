#!/usr/bin/env python3
"""Check the paired Material palettes and contrast of actual app text/surfaces.

Run with Python 3; no third-party packages or connected Android device needed.
"""
from pathlib import Path
import xml.etree.ElementTree as ET

RES = Path(__file__).resolve().parents[1] / "app/src/main/res"


def load(folder):
    root = ET.parse(RES / folder / "colors.xml").getroot()
    return {node.attrib["name"]: node.text.strip() for node in root.findall("color")}


def luminance(hex_color):
    rgb = [int(hex_color[i:i + 2], 16) / 255 for i in (1, 3, 5)]
    linear = [x / 12.92 if x <= 0.04045 else ((x + 0.055) / 1.055) ** 2.4 for x in rgb]
    return sum(x * weight for x, weight in zip(linear, (0.2126, 0.7152, 0.0722)))


def contrast(a, b):
    high, low = sorted((luminance(a), luminance(b)), reverse=True)
    return (high + 0.05) / (low + 0.05)


def main():
    light, dark = load("values"), load("values-night")
    assert light.keys() == dark.keys(), "Every semantic color needs a light and dark value"
    theme = ET.parse(RES / "values/styles.xml").getroot().find("style")
    assert theme.attrib["parent"] == "Theme.Material3.DayNight.NoActionBar"
    pairs = [
        ("on_primary", "primary"),
        ("on_primary_container", "primary_container"),
        ("on_secondary", "secondary"),
        ("on_secondary_container", "secondary_container"),
        ("on_surface", "surface"),
        ("on_surface", "surface_low"),
        ("on_surface_variant", "surface"),
        ("on_surface_variant", "surface_high"),
        ("primary", "secondary_container"),
        ("error", "error_container"),
    ]
    for name, colors in (("light", light), ("dark", dark)):
        for foreground, background in pairs:
            ratio = contrast(colors[foreground], colors[background])
            assert ratio >= 4.5, f"{name}: {foreground}/{background} contrast is only {ratio:.2f}:1"
    print(f"PASS: DayNight theme, matching palettes, {len(pairs) * 2} text contrast checks (≥4.5:1)")


if __name__ == "__main__":
    main()
