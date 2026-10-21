# Changelog

Each `## <version>` section becomes the GitHub Release notes for tag `v<version>`, and the release
workflow refuses to publish a version without one.

## 1.0.0 - 2026-10-21

**Minecraft 26.x, on Fabric, Quilt, NeoForge and Forge.** This release is for Minecraft 26.1 through 26.3. It
has one jar per band and loader, named `the_continent-1.0.0+<loader>-mc<band>.jar`:

| Minecraft | Fabric and Quilt | NeoForge | Forge |
|---|---|---|---|
| 26.1 – 26.1.2 | `mc26.1` | `mc26.1.2` (26.1.2 only) | `mc26.1.2` (26.1.2 only) |
| 26.2 | `mc26.2` | `mc26.2` | `mc26.2` |
| 26.3 | `mc26.3` | `mc26.3` | `mc26.3` |

Minecraft 1.21.11 stays on 0.2.0, which takes critical fixes only.

- **New biomes where the game has them.** On 26.2 and newer, Sulfur Caves lies under the north-east hills, above
  the Deep Dark, for cinnabar, sulfur, potent sulfur and sulfur spikes. On 26.3, Dappled Forest sits in the
  north-east foothills between the Old Growth Pine Taiga and the Meadow, for poplar, red shrubs and shelf
  mushrooms. Otherwise the map is 0.2.0's, unchanged.
- **Updating a world to a newer band:** chunks that are already generated stay as they are. The new biomes appear
  only in chunks generated afterwards, so they can meet the old ones at a seam.
- Quilt runs the Fabric jar. CI boots it on Quilt Loader 0.31.0-beta.4, the only Quilt line for 26.x.
- Every jar is boot-tested on its loader's real dedicated server for its band on every CI run. The Fabric jar
  also runs the full game test suite and a headless client, which walks real chunks and checks that every block
  only the new biomes have is there.

## 0.2.0 - 2026-10-19

**World generation changed.** The map lives in the mod, not in the save, so after updating, a world created
with 0.1.0 generates its *new* chunks on the new map. They won't line up with chunks generated before,
along the coast and the borders between biomes. Start a new world for the new map, or update only after the area you play in is generated.

- The coast is no longer a circle. It is hand-laid, with bays and headlands all round, and sits 880 to
  1120 blocks from the centre. The beach keeps its width along it. Offshore bands run up to ~13% narrower in
  the bays.
- Taller mountains. The northern range is lifted past vanilla's height ceiling: Jagged Peaks summits reach
  y ~300 (vanilla tops out near 256), with separate summits and saddles, sheer cliffs into the Frozen Ocean,
  Snowy Slopes up to ~275 and Grove ~140-170.
- Region interiors roll. Forests, jungles, savanna, taiga, meadow and cherry grove get real hills and
  hollows, and plains and desert a gentle roll. Swamps and badlands are unchanged.
- Borders between biomes are ragged instead of ruled lines: they meander and jag by up to 24 blocks, with the
  odd stray patch of one biome in its neighbour, and the terrain follows them. The coast, river and island
  keep their shape.
- The guaranteed village moved to x −68, z 104, the spot in Plains farthest from every border, so the ragged
  borders stay clear of it on every seed. In a world made with 0.1.0 the old village stays where it was.
- A client test plays a real singleplayer world on every CI run: the day cycle runs, a lit portal leads to
  the Nether, and the nearest stronghold's portal opens and leads to the End.

## 0.1.0 - 2026-10-17

First release, for Minecraft 1.21.11 with Fabric Loader 0.19.5+ and Fabric API.

- New world type, **The Continent**: one hand-laid continent, about 1000 blocks in radius, in an endless
  ocean. It holds the smallest set of overworld biomes that still covers every obtainable block. The layout is
  the same on every seed, and the seed only changes local detail.
- Terrain follows the map: mountains in the north, lowlands in the middle, coasts that slope to a shelf,
  a river from the north-centre to the east coast, and a Mushroom Fields island to the west.
- One guaranteed plains village at x −100, z 130 on every seed.
- Ocean and underground structures that are the only source of a block (ocean monument, warm ocean ruins,
  buried treasure, trial chambers, stronghold) generate as usual.
- `/continent map [radius] [blocksPerPixel]` (admins only) exports a biome map PNG of the current dimension.
- Servers: `level-type=the_continent\:continent` in `server.properties`.
