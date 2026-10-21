"""Write the Continent's copies of vanilla terrain JSON from a Minecraft jar (the inverse of TerrainResourcesTest).

Usage: python tools/terrain_copies.py MINECRAFT_JAR common/versions/<band>/src/main/resources
The jar is Loom's, e.g. ~/.gradle/caches/fabric-loom/<band>/minecraft-merged.jar.

What is copied: every vanilla overworld density function that references one of SEEDS, directly or through
another copied function (26.3 moved final_density and the surface levels out of the noise settings, so the set
grows there), plus the overworld noise settings. Each copy is the jar's file with every such reference re-pointed
to the_continent:overworld/, `offset` renamed `offset_spline` (our offset.json adds the layout's uplift to it),
and the top slide raised from y 240-256 to 288-304, wherever it is written (twice in every band). The channel
files that wrap the layout (HANDWRITTEN) are ours and not written here. TerrainResourcesTest checks exactly this
against the band's own jar, so run the band's unit tests after.
Refuses to overwrite an existing file (delete the old copies first, from a clean git tree); prints every file it
writes.
"""
import os
import re
import sys
import zipfile

SEEDS = ["continents", "erosion", "ridges", "ridges_folded", "offset", "factor", "jaggedness", "depth", "sloped_cheese"]
HANDWRITTEN = {"continents", "erosion", "ridges"}
RENAMED = {"offset": "offset_spline"}
FUNCTIONS = "data/minecraft/worldgen/density_function/overworld/"
# Before 26.3 the slide is a y_clamped_gradient-style pair of fields; from 26.3 it is a gradient's coordinates.
TOP_SLIDES = [
    (re.compile(r'("from_value": 1\.0,\s*"from_y": )240(,\s*"to_value": 0\.0,\s*"to_y": )256'), r"\g<1>288\g<2>304"),
    (re.compile(r'("from_coordinate": )240(,\s*"from_value": 1\.0,\s*"to_coordinate": )256(,\s*"to_value": 0\.0)'),
     r"\g<1>288\g<2>304\g<3>"),
]


def rewired_names(z):
    """SEEDS plus every overworld function that references one of them, transitively."""
    texts = {n[len(FUNCTIONS):-len(".json")]: z.read(n).decode("utf-8")
             for n in z.namelist() if n.startswith(FUNCTIONS) and n.endswith(".json")}
    names = set(SEEDS)
    grew = True
    while grew:
        grew = False
        for name, text in texts.items():
            if name not in names and any('"minecraft:overworld/' + n + '"' in text for n in names):
                names.add(name)
                grew = True
    return sorted(names)


def rewire(text, names):
    for name in names:
        text = text.replace('"minecraft:overworld/' + name + '"', '"the_continent:overworld/' + name + '"')
    return text


def raise_slides(text):
    raised = 0
    for pattern, replacement in TOP_SLIDES:
        text, n = pattern.subn(replacement, text)
        raised += n
    return text, raised


def write(out, rel, text):
    path = os.path.join(out, rel)
    if os.path.exists(path):
        sys.exit("refusing to overwrite " + path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text)
    print("wrote", path)


def main():
    jar, out = sys.argv[1], sys.argv[2]
    with zipfile.ZipFile(jar) as z:
        names = rewired_names(z)
        pending = []
        raised = 0
        for name in names:
            if name in HANDWRITTEN:
                continue
            text, n = raise_slides(rewire(z.read(FUNCTIONS + name + ".json").decode("utf-8"), names))
            raised += n
            pending.append(("data/the_continent/worldgen/density_function/overworld/" + RENAMED.get(name, name) + ".json",
                            text))
        settings, n = raise_slides(rewire(z.read("data/minecraft/worldgen/noise_settings/overworld.json").decode("utf-8"),
                                          names))
        raised += n
        pending.append(("data/the_continent/worldgen/noise_settings/continent.json", settings))
        if raised != 2:
            sys.exit("expected to raise 2 top slides, found %d; nothing written" % raised)
        print("re-pointed:", ", ".join(names))
        for rel, text in pending:
            write(out, rel, text)


main()
