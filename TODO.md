# TODO

The working plan. `plan-work` writes here **before** any code is written — a plan that
lives only in the chat window did not happen. Keep this current as you execute; a stale
TODO is worse than an empty one, because the next session trusts it.

> Long-lived context does **not** belong here. Decisions and gotchas go to
> `.agent/memory/`; this file is the active work list.

---

## Task template

```markdown
## <Task name>

**Goal:** <one sentence — what "done" means>
**Approach:** <chosen approach, and the one-line reason it beat the alternative>
**Not doing:** <explicit out-of-scope list — this is what stops scope creep>
**Blast radius:** <what this destroys or overwrites, and where it comes back from —
                  or "none: additive only">

- [ ] 1. <concrete step — names a real file>
- [ ] 2. <concrete step>
- [ ] 3. <the test that proves it works>

**Rollback:** <the exact command that undoes this — not "revert the change">
```

Rules for steps: each names a real file or command, each is independently verifiable, and
tests are steps rather than an afterthought. More than ~8 steps means it's a Tier 2 task —
split it into stages with commits between them.

`Blast radius:` and `Rollback:` are **mandatory** — "none, additive only" is a valid
answer, a blank field is not. Destructive steps are marked as such and gated through
`guard-destructive` when you reach them; irreversible steps go last.

---

## Active

## Minecraft 26.x on Fabric, Quilt, NeoForge and Forge, then go public  *(Tier 2 — owner asked 2026-10-20)*

**Goal:** one tag on `main` publishes a jar per band and loader for every Minecraft 26.x release. Each
band's map holds every biome that is the only natural source of a block in that version. The README's support
table matches what the release ships. Then the repo goes public with no credentials or personal data in any commit.

**Status:** owner approved 2026-10-20; Stage A in progress. Decision record: decisions.md 2026-10-20.
**Owner answers 2026-10-20:** push to `origin/main` once per stage, after the stage is green on Windows. The first
26.x release is **1.0.0**. Public commit identity is the GitHub noreply address. Credentials are not rotated yet, so
Stage E stays blocked on E.1.

### Facts this plan stands on  *(checked 2026-10-20; re-check before Stage A, never bump from memory)*
- **Stable 26.x releases** (meta.fabricmc.net): 26.1, 26.1.1, 26.1.2, 26.2, 26.3. All need **Java 25**, and all
  ship **unobfuscated**, so there are no mappings or remapping: Loom's no-remap plugin, the 1.19.x line
  (decisions.md 2026-10-16).
- **NeoForge** (maven.neoforged.net): stable lines 26.1.2, 26.2.0, 26.3.0. There is none for 26.1 or 26.1.1.
- **Forge** (promotions_slim.json): one major per release. 62 is 26.1, 63 is 26.1.1, 64 is 26.1.2 (recommended
  64.1.0), 65 is 26.2 (recommended 65.1.0), 66 is 26.3 (latest 66.0.13, no recommended build yet).
- **Quilt** (meta.quiltmc.org): Quilt Loader runs Fabric mods. The loaders for 26.x are beta only (0.31.0-beta.4),
  and Quilt Standard Libraries were discontinued in December 2025. A Quilt-only build would add nothing.
- **Data pack format** changes every minor: 101.1 (26.1), 107.1 (26.2), 121.0 (26.3). 26.2 also removed the
  `weird_scaled_sampler` density function in favour of `interval_select`. Our copied JSON doesn't use it, because
  the cave functions are referenced by name, but every copy has to be regenerated from each band's own jar.
- **New biomes** (minecraft.wiki):
  - 26.1 adds none.
  - **26.2 adds Sulfur Caves**, a cave biome that vanilla also puts inside hills and mountains. It is the only
    natural source of cinnabar, sulfur, potent sulfur and sulfur spikes.
  - **26.3 adds Dappled Forest**, a surface biome next to cold biomes. It is the only natural source of poplar
    logs, red/orange/yellow poplar leaves, red shrubs and shelf mushrooms. The wandering trader sells some of
    these, but this project counts natural generation only, so both biomes are required.
  - 26.3 also adds **Abandoned Camps** in 18 biomes, 11 of which we have. They gate no block: straw beds are
    crafted, and cushions are entities. Report them, don't require them.
- **Fabric-only code** in `src/main` is one class: `ContinentMod`. It registers the biome source codec, the
  density function type, the placement type and the `/continent` command. Everything else (layout, terrain,
  worldgen, map export) is plain Minecraft code.

### Bands: three, the minimum the loaders allow
| Band | Compiled against | Fabric (+ Quilt) | NeoForge | Forge | Map |
|---|---|---|---|---|---|
| **26.1 – 26.1.2** | 26.1.2 | 26.1 – 26.1.2 | 26.1.2 | 26.1.2 (major 64) | the 0.2.0 layout |
| **26.2** | 26.2 | 26.2 | 26.2 | 26.2 | + Sulfur Caves |
| **26.3** | 26.3 | 26.3 | 26.3 | 26.3 | + Sulfur Caves + Dappled Forest |

- **Fewer isn't possible yet:** each minor changes the data pack format, the Fabric API line, NeoForge's line and
  the biome set. mcMMO-Singleplayer measured the same three-band split for 26.x. A band is widened (for example, a
  Forge 26.1.x jar declaring 26.1 too) only after a boot test on that exact version passes. A range is never
  assumed.
- **1.21.11 is frozen at 0.2.0** (Fabric only). *Owner, 2026-10-20:* it gets its own branch, `mc/1.21.11`, cut
  from `main` on 2026-10-20 after the MIT licence commit, before any port work. It takes critical fixes only, and no features. Once Stage A
  lands, `main` no longer builds 1.21.11.

### Branches: `main` for every 26.x band, plus the frozen `mc/1.21.11`
**Approach:** Stonecutter covers the version axis, and MultiLoader-style subprojects (`common`, `fabric`,
`neoforge`, `forge`) cover the loader axis, all on `main`. Each loader has one entrypoint class that registers the
four things above through `common`. Per-version differences go in Stonecutter `//? if` blocks and per-version
property files, never in copied source trees.
**Rejected:** a branch per band, which is mcMMO's setup: 9 branches, `drift-audit.py`, `Backport-of:` trailers,
byte-identity audits, and back-ports that still went missing. Architectury API: it would add a runtime
dependency for players to save four registrations. A repo per loader: the same drift problem.

**Not doing:** 1.21.10 and older · new features for 1.21.11 · a Quilt-specific jar or QSL · Modrinth/CurseForge
(a separate outward-facing call for the owner) · 26.4 or 27.x snapshots · changing the 26.1 band's map ·
guaranteeing Abandoned Camps.

**Blast radius:**
- Stages A–D are in-repo, revertible commits. Existing worlds keep their saved generator. On 26.2 and 26.3, chunks
  generated after an upgrade can seam against old ones where the new biomes take space. The README has to say so.
- The `src/` → subproject move is a large rename, made with `git mv` in its own commit and no other change.
- **Stage E is destructive and outward-facing:** a history rewrite, a new public repo and re-published releases.
  Every step there goes through `guard-destructive` with the owner's in-the-moment confirmation.

### Stage A — Fabric on all three bands  → one commit per step
- [x] A.1 Pins, checked 2026-10-20. They go into `gradle.properties` (with these sources as comments) in the step
      that first uses them, so the file never describes a build it doesn't run:
      - **Stonecutter 0.9.8** (Gradle Plugin Portal, 2026-08-31). Needs Gradle ≥ 9.0 (we run 9.7.1 on Java 25).
        A Groovy controller works with `kotlinController = false` and `centralScript = "build.gradle"`.
      - **Loom 1.18.4, plugin id `net.fabricmc.fabric-loom`** (the no-remap id). `fabric-example-mod` branches
        26.1, 26.2 and 26.3 all pin `1.18-SNAPSHOT` with that id and Gradle 9.7.1, so the 2026-10-16 call (a
        release on the template's line) still holds. Only the plugin id changes.
      - **Fabric Loader 0.19.5** (newest stable; 0.20.x is beta). The template pins it for every 26.x branch.
      - **Fabric API** (newest per version on maven.fabricmc.net): `0.155.3+26.1.2`, `0.161.0+26.2`,
        `0.164.0+26.3`.
- [x] A.2 `refactor: split common and fabric`. `git mv` the code into `common/` and `fabric/`, so that
      `ContinentMod` is the only class in `fabric/src/main`. It's still 1.21.11, and it's a pure move: same
      unit test and gametest counts, and `./gradlew build` green on Windows and on CI.
      Found 2026-10-20: 11 common classes take `ContinentMod.MOD_ID` for their loggers and ids, so this is two commits:
      - A.2a `refactor: move the mod id and logger into Continent`. A loader-free `com.wulfic.continent.Continent`
        holds `MOD_ID` and `LOGGER`, every user switches to it, and `ContinentMod` keeps only the registrations.
      - A.2b the pure `git mv`. `common/` is a Loom project (Minecraft only, no Fabric API) holding layout, map,
        world, command, the resources and the unit tests. `fabric/` holds `ContinentMod`, `fabric.mod.json`, the
        gametests and `ModMetadataTest`, and compiles common's sources into its own jar (the MultiLoader-Template
        `commonJava`/`commonResources` pattern), so there is still one jar, named `the_continent-<ver>.jar`. CI
        paths move from `build/` to `fabric/build/`. `ModMetadataTest` gets the repo root from a system
        property instead of the working directory.
      Done 2026-10-20: 199 unit tests in common (198 moved + `LoaderFreeTest`), 5 in fabric, 11/11 gametests, and
      the jar and sources jar list exactly the same files as before. Two traps on the way (gotchas.md 2026-10-20):
      common needs `modImplementation fabric-loader` for its tests, so `LoaderFreeTest` guards the sources instead
      of the classpath, and `WorldPresetResourcesTest` had been reading vanilla's tag or ours by classpath order.
- [x] A.3 Port to **26.1.2** (Fabric): no-remap Loom, `options.release = 25`, and in `fabric.mod.json`
      `"minecraft": ">=26.1 <26.2"` and `"java": ">=25"`. Regenerate the copied worldgen JSON from the 26.1.2 jar,
      with `TerrainResourcesTest` passing unedited. Check every API rename against the jar, not memory (26.1
      also moved dimensions into a `dimensions/` folder, so check that `MapFiles` resolves `<world>/continent-maps`).
      Gate: unit tests, gametests and the client gametest green. A headless `runServer` on **26.1** (the
      band's floor) reaches "Done" with zero ERROR lines.
      Done 2026-10-20. Only two API breaks: `ChunkPos` is a record (`x()`, `ChunkPos.containing`), and day time is a
      world clock (`getOverworldClockTime()`; `time set noon` is now a time marker). `TerrainResourcesTest` passed
      unedited, so the copied spline JSON equals 26.1.2's and nothing was regenerated. 204 unit tests, 11/11
      gametests, client gametest green after stopping the sprint before close (gotchas.md 2026-10-20). A 26.1
      server with Fabric API 0.145.1+26.1 reached Done with 0 ERROR lines, and its `world_gen_settings.dat`
      names `the_continent:continent`. Map export: `LevelResource.ROOT` is unchanged, and a gametest asserts it.
- [x] A.4 **Found 2026-10-20 by compiling the 26.1.2 tree against each jar** (`-Pminecraft_version=…`):
      - 26.2: 3 errors, all in `LayoutDensityFunction` (`DensityFunction` gained `mapChildren(Visitor)`,
        `HOLDER_HELPER_CODEC` is gone). Vanilla `noise_settings/overworld.json` and `sloped_cheese.json` changed;
        every other copied file is byte-identical to 26.1.2.
      - 26.3: 92 errors. The density-function engine was replaced: `levelgen.densityfunction.DensityFunction`
        compiles to a `DensitySampler` (`compileSampler`, `rewriteChildren`, `range()`, `domainAxes()`, float
        values), `RandomState` has samplers instead of `router()`/`sampler()`, `BiomeSource` resolves through
        `createResolver(Climate.Sampler)`, `StructurePlacement` is an interface with no `StructurePlacementType`,
        and `KeyDispatchDataCodec` is gone. **Every** copied worldgen JSON changed, and
        `noise_settings/overworld.json` shrank from 111 KB to 3.9 KB. So 26.3 is a second port of the worldgen layer
        (`world/` + the terrain data pack, with the spline facts re-checked), not an `//? if` patch.
      **Owner, 2026-10-20: all three bands for 1.0.0, with 26.3 as its own stage after 26.2.** So A.4 is Stonecutter
      plus the 26.2 node, and A.4b (below) is the 26.3 port. Per-version worldgen JSON lives in each node's own
      resource folder (`versions/<v>/src/main/resources`), because JSON can't hold `//? if` comments.
      Done 2026-10-20 for 26.2 (decisions.md "Bands as Stonecutter nodes"): both bands build and test in one
      `./gradlew build` (199 + 5 unit tests and 11/11 gametests each), and the 26.2 client gametest is green.
      Original step: Stonecutter nodes `26.2` and `26.3` for Fabric: per-version properties, regenerated JSON, and `//? if`
      only where an API really differs. One `./gradlew build` builds and tests all three.
- [x] A.4b **26.3 port** (Fabric). Read from Loom's decompiled 26.3 sources (`./gradlew :common:26.3:genSources`):
      - **Spline facts hold:** all 118 spline point lists in offset/factor/jaggedness are identical to 26.1.2's.
        Only the wrappers changed (`flat_cache`+`cache_2d` → `cache`+`lerp`), so no retuning.
      - **Data (b1):** 26.3 moved `final_density`, `preliminary_surface_level` and `chunk_surface_level` out of the
        noise settings into density functions, and the top slide is now a `gradient` 240→256 inside the first two.
        Copies needed: the six we have, plus those three (the transitive closure of functions referencing a
        rewired one; caves and ore veins reference none). `flat_cache` → `cache`, `shifted_noise` → `noise` with
        shifts, and the router's temperature is a reference. So: our four hand-written channel files
        (continents/erosion/ridges/offset) become per band too; `tools/terrain_copies.py` computes the closure
        and raises either slide form; `TerrainResourcesTest` derives the closure from the band's own jar instead
        of fixed lists.
      - **Code (b2):** `LayoutDensityFunction` compiles to a sampler and derives the layout seed once per compile
        from the compiled `seed_source`. `ContinentBiomeSource.createResolver` derives it from
        `sampler.temperature()`. `drivesTerrainOf` unwraps the climate sampler's continentalness (the router is
        private now). `AnchoredPlacement` extends `AbstractSpreadingStructurePlacement`, and the registry takes
        its `MapCodec`. `ContinentCommand` samples through `createUncachedResolver`.
      - **Gate (b3):** all three bands' unit tests and gametests, the 26.3 client gametest, and a 26.3 server boot.
      Done 2026-10-20 (decisions.md "26.3: compile-time layout seed…"): on 26.3, 204 common unit tests, 7 Fabric
      unit tests, 11/11 gametests and the client gametest are green. The server boot is covered by the gametests'
      dedicated server, since 26.3 is the band's floor. The copy set came from the rule: 26.3 adds
      `final_density`, `preliminary_surface_level` and `chunk_surface_level`.
- [x] A.5 `BandLabelTest`: the jar name `the_continent-<ver>+<loader>-mc<band>.jar` and the loader metadata's
      Minecraft range agree in both directions, in the style of mcMMO's `BandVersionLabelTest`. Mutation check:
      widen the metadata range and the test goes red.
- [x] A.6 `.github/workflows/build.yml`: a matrix over the nodes on Temurin 25, with the client gametest per
      band under `xvfb-run`. `actionlint` clean.

### Stage B — NeoForge, Forge, Quilt
- [x] B.1 `neoforge/`: `ContinentNeoForge` registers the four things (`DeferredRegister` / `RegisterEvent` for the
      codecs, so they exist before datapacks load, and `RegisterCommandsEvent`). Ranges go in
      `neoforge.mods.toml`. Nodes 26.1.2, 26.2 and 26.3.
- [x] B.2 `forge/`: the same on Forge's API, with `mods.toml`. Because Forge has one major per release, the band
      A jar targets 26.1.2 alone, unless boot tests on 26.1 and 26.1.1 pass.
- [x] B.3 Tests per loader:
      - Unit tests live in `common` and run once per band.
      - Fabric keeps the full gametest suite.
      - NeoForge and Forge: port the server gametests to each loader's gametest framework if A.1 finds that
        feasible. Otherwise run a boot smoke per band: a dedicated server with
        `level-type=the_continent\:continent` reaches "Done", there are zero ERROR lines, and `/locate biome
        minecraft:plains` answers near the origin.
      - Mutation check: drop one registration and the smoke goes red.
- [x] B.4 Quilt: CI boots each band's **Fabric jar** on the newest Quilt Loader, with Fabric API, using the same
      server smoke. There's no Quilt jar. If a band won't boot on Quilt, the README says so instead of claiming
      support.

**Stage B done 2026-10-20** (decisions.md "Every shipped jar boots on its loader's real server"):
- NeoForge (ModDevGradle 2.0.148) and Forge (ForgeGradle 7.0.40) are Stonecutter branches beside `fabric`, each
  compiling common's sources, with one entrypoint class (`ContinentNeoForge`, `ContinentForge`) that registers the
  three codecs through `DeferredRegister` and the command through `RegisterCommandsEvent`.
- Tests per loader: the Fabric gametests stay the deep suite. Every shipped jar (Fabric, Quilt via the Fabric jar,
  NeoForge, Forge) boots on its loader's real dedicated server via `tools/smoke_band.py` → `tools/server_smoke.py`,
  which fingerprints the world: Mushroom Fields near the layout's (-1250, -50) and `the_continent:continent` in the
  saved generator, plus zero ERROR lines and a clean stop. All 12 loader × band runs pass locally, Fabric and Quilt
  on the band floor (26.1). Mutation check: an unregistered biome source fails NeoForge's smoke in 33 s with 5 reasons.
- Forge needed a `pack.mcmeta` or it silently drops the mod's data (gotchas.md 2026-10-20).
- CI runs the smoke for all four loaders in every band leg. The NeoForge and Forge 26.1 jars are labelled `mc26.1.2`:
  neither loader has a stable build for 26.1 or 26.1.1.

### Stage C — the new biomes, per band
A biome is required if it's the only natural source of a block (Q4 in the 0.2.0 plan).
- [x] C.1 `layout/`: give each biome the band it needs. **Owner, 2026-10-20: a compile-time list, not the registry**
      (decisions.md "The band's biome set is a compile-time list"). `VanillaBiomes.inThisBand()` is the newer biomes
      this band has, in `//? if` blocks. `DefaultLayout.create(Set)` / `terrain(Set)` are pure functions of that
      set, and `create()` / `terrain()` pass the band's set, so the biome source, terrain, command and map can't
      disagree. Tests:
      - `BandBiomesTest` (world/, may touch Minecraft): `inThisBand()` equals exactly the newer biomes declared in
        vanilla's `Biomes` class for this band, in both directions.
      - `BandLayoutsTest`: for each of the three sets, across seeds, the layout constructs (village margin, river,
        island channel), the terrain constructs, every surface region and every cave biome clears its area floor,
        and `biomeIds()` is exactly what `sample` emits.
      - The 26.1 set's map is unchanged: a golden hash of the samples, taken before the change.
      - `cavesFollowTheMap`'s Dripstone points move off the north-east, where Sulfur Caves takes over on 26.2+.
- [x] C.2 **Sulfur Caves (26.2+):** a cave seed in `layout/Caves`. The candidate spot is under the north-east hills
      (Meadow / Cherry Grove, around (650, −200)), which matches vanilla putting it inside hills. It must not
      reach the Deep Dark (z < −350, y ≤ 0). The block walk finds cinnabar, sulfur, potent sulfur and sulfur
      spikes.
      Checked 2026-10-20 against the jars: nothing to copy. The cinnabar and sulfur bands are a surface rule
      (26.2: inside our copied `continent.json`, 26.3: vanilla's `minecraft:overworld` material rule that ours
      references), and the spikes and pools are the biome's own features.
      **Block walk** = a new step in `ContinentClientGameTest`, the only test with a real Continent world. It
      generates full chunks around each new biome's centre on this band and requires every gated block.
- [x] C.3 **Dappled Forest (26.3+):** a surface seed in the cold north. The candidate spot is between Old Growth
      Pine Taiga (200, −450) and Meadow (550, −350), around (420, −520). It must clear the area floor
      (40k blocks²), the river and the village margin. Give it a rolling forest profile like Forest or Birch. The
      block walk finds poplar logs, all three poplar leaf colours, red shrubs and shelf mushrooms.
- [x] C.4 Census gametest per band: the new biomes clear their area floor, and Abandoned Camps are reported only.
      Extend the block checklist below. Re-export `docs/continent-map.png` from the 26.3 band and note the
      26.1 / 26.2 differences under it.
- [x] C.5 Mutation check: drop the Sulfur Caves seed and the 26.2 census goes red. Drop Dappled Forest and the 26.3
      census goes red. Also drop a band's `inThisBand()` entry and `BandBiomesTest` goes red.

**Stage C done 2026-10-20:** unit tests green on all 3 bands, Fabric server gametests 11/11 per band (the census
also requires `inThisBand()`, so a dropped seed can't hide a biome), and the client block walk found every gated
block on 26.2 and 26.3. C.5 mutations all went red: the Sulfur seed (26.2 unit + census), the Dappled seed (26.3
census), an `inThisBand()` entry (`BandBiomesTest`), and a 26.1 biome missing from `BandBiomesTest`'s pinned list,
which stands in for a new vanilla biome nobody listed. The README map is the 26.3 export, seed 12345.

### Stage D — README and release pipeline
**Owner, 2026-10-20:** tag `v1.0.0` as soon as Stage D is green on CI (the owner does or has done the D.3
client check). **Plan, 2026-10-20:**
- The band table is `gradle.properties`: the bands are the `band_label.<band>` keys, and each band must also have
  `minecraft_range`, `neoforge_band_label`, `neoforge_minecraft_range`, `forge_band_label` and
  `forge_minecraft_range`. The guard reads it and expects exactly 2 × 3 × bands files in dist:
  `the_continent-<v>+<loader>-mc<label>.jar` and its `-sources.jar`. A missing file, an extra file (a stale jar)
  or two bands with the same label for one loader is refused.
- Each jar's own metadata must name `the_continent`, the version, and the band's Minecraft range: `fabric.mod.json`
  `depends.minecraft`, and in `META-INF/neoforge.mods.toml` / `META-INF/mods.toml` the `[[mods]]` version and the
  `minecraft` dependency's `versionRange` (read with awk; the files are generated, one key per line).
- The publish step uploads `dist/*.jar` (exactly the set the guard just proved) and a `SHA256SUMS` over them.
- D.2's test is `ReadmeBandsTest` in `fabric/src/test` (it already gets the repo root, like `ModMetadataTest`):
  the README's 26.x rows are exactly the bands, and each row's cells derive from `gradle.properties`
  (Fabric: `smoke_floor` – band, NeoForge/Forge: their labels). No 26.x row says "Planned".
- `version=1.0.0`, a `## 1.0.0` CHANGELOG section, README "Installing" and "CI and releases" for 9 jars.
- **Not doing:** Modrinth/CurseForge, signing, changing the release triggers.
- **Blast radius:** the tag publishes a GitHub Release (outward-facing, in the private repo). Rollback:
  `gh release delete v1.0.0 --cleanup-tag` (the repo is private, so nobody outside has seen it).

- [x] D.1 `.github/scripts/release-guard.sh`: one tag `vX.Y.Z` publishes every band × loader jar (up to 9),
      their sources jars and `SHA256SUMS`. The guard checks each jar's version and Minecraft range against the
      band table. New cases: a missing jar, a jar with the wrong range, a duplicate band.
- [x] D.2 `README.md` "Supported versions": turn the planned rows into the real ones. A test compares the table to
      the build's band list, so the docs can't promise a band the release doesn't ship.
      Done 2026-10-21: the guard checks all 9 jars + 9 sources jars against `gradle.properties` and each jar's
      own metadata. `release-guard-test.sh` has 59 cases (in `ubuntu:24.04` via Docker, shellcheck clean). On the
      real 0.2.0 jars from all 12 nodes it passes, and a 26.2 jar renamed as 26.3 is refused by its range.
      `ReadmeBandsTest` went red for a wrong label and for an extra "Planned" 26.4 row. actionlint clean.
- [x] D.3 The first 26.x release: **1.0.0** (owner, 2026-10-20). **Before the tag:** the owner launches a real
      client on each loader for at least one band and creates a Continent world.
      **Released 2026-10-21:** https://github.com/Wulfic/the-continent/releases/tag/v1.0.0, tag on d1c7545, 9 jars +
      9 sources jars + `SHA256SUMS` (all 18 verified). On the way: the first tag run hit a tick-budget flake
      (dd79c2a) and then the guard refused nested artifacts (d1c7545). The owner approved moving the tag
      (0f14c96 → d1c7545) after nothing had been published. The owner's live client check is theirs (answer
      2026-10-20: "tag when green").

### Stage E — sanitize, then go public  *(last; destructive and outward-facing)*
**Found 2026-10-20:**
- `.env`, `.mcp.json` and `.claude/mcp.vscode-reference.json` are **tracked with live credentials**, including a
  GitHub `ghp_` PAT. All three have been there since the initial commit `1b9209a`, so **every commit** carries them.
- 5 commits carry the owner's personal email. The rest use noreply addresses.
- There was **no LICENSE**. Fixed 2026-10-20: MIT, see E.3.
- The repo is private. Its Actions logs, artifacts and the releases v0.1.0 and v0.2.0 would become public with it.

**Owner, 2026-10-21: archive this repo and publish a fresh one under the same name, from a clean state.** That
replaces the old E.5 (filter-repo) and E.6 (re-push rewritten history). Answers:
- **History:** one clean commit of today's tree, minus the credential files, authored by the noreply identity.
  Nothing from the old history is pushed, so nothing in it can leak. SHAs cited in `.agent/memory` and here resolve
  only in the private archive.
- **Releases:** only v1.0.0, rebuilt by the new repo's own CI from a tag on the clean commit. 0.1.0 and 0.2.0
  (1.21.11) and `mc/1.21.11` stay in the archive.
- **Rotation:** go ahead before rotating. The secrets never reach the public repo. The owner still rotates the
  GitHub PAT, the context7 key and the system passwords in `.env` soon, since the archive holds them.
- **Local copy:** re-point this working copy at the new repo (old history kept in a verified bundle and in the archive).

**Approach:** create the new repo **private**, push, then scan and harden what is actually on GitHub, and only
then flip it to public. Flipping is the one outward-facing moment, and everything before it can be redone.
**Not doing:** filter-repo · carrying 0.1.0 / 0.2.0 / `mc/1.21.11` into the public repo · rotating credentials
(owner) · Modrinth/CurseForge.
**Blast radius:** the GitHub rename makes `Wulfic/the-continent` a redirect until the new repo takes the name, so
links to the old releases stop resolving. The archive keeps every commit, tag, release and Actions log, private
and read-only. Locally: `main` moves to the new root commit and the old tags are deleted locally, all recoverable
from the bundle and from `archive`.

- [ ] E.1 **Owner (no longer blocking):** revoke and reissue the GitHub PAT, the context7 key, and the `.env`
      passwords.
- [ ] E.2 `chore: stop tracking credential files`: `git rm --cached` the three files (the local copies stay), add
      them to `.gitignore`, add `.env.example` and `.mcp.example.json` / `.claude/mcp.vscode-reference.example.json`
      with placeholders, and fix the docs that call them tracked (AGENTS.md, copilot-instructions, skills,
      state.md). Record the reversal in decisions.md.
- [x] E.3 Owner decisions (2026-10-20): MIT licence (done) · agent files stay public if E.4 is clean · noreply
      identity `34788214+Wulfic@users.noreply.github.com`.
- [ ] E.4 Sweep **the tree that will be published**: gitleaks (Docker) on the clean commit, plus grep for
      emails, personal paths, hostnames and the network name. Read `.agent/memory` for anything private (modpack
      details, gotchas 2026-10-19). Fix what it finds in a commit on `main`.
- [ ] E.5 Backup: push `mc/1.21.11` to `origin` (still the private repo) so the archive is complete, then
      `git bundle create <scratchpad>/the-continent-full.bundle --all` and `git bundle verify`.
- [ ] E.6 Build the clean commit in the scratchpad: `git archive HEAD` → new `git init` → one commit as the
      noreply identity. gitleaks on its history must report 0, and `git ls-files` must not list a credential file.
- [ ] E.7 **Outward, owner confirms each:**
      1. `gh repo rename the-continent-archive -R Wulfic/the-continent`, then `gh repo archive`.
      2. `gh repo create Wulfic/the-continent --private`, then push the clean `main`.
      3. CI green on the new repo. Tag `v1.0.0`, and check the release's 9 + 9 jars and `SHA256SUMS`.
      4. Harden: secret scanning with push protection, Dependabot alerts, a ruleset blocking force-push and deletion
         on `main` (no PR requirement, the owner pushes to `main`). Confirm there's no `pull_request_target` and the
         release job only runs on tags or dispatch.
      5. `gh repo edit --visibility public`.
- [ ] E.8 Verify logged out (`curl` with no token): the repo and release are visible, the downloads hash to
      `SHA256SUMS`, and a fresh clone has 1 commit and scans clean.
- [ ] E.9 Re-point local: `git remote rename origin archive`, add the new `origin`, delete the local old tags,
      fetch, `git checkout -B main origin/main` (the trees are identical, so the working copy doesn't change).
      `mc/1.21.11` stays local, tracking `archive`.

**Rollback:** before E.7.5 nothing is public: `gh repo delete` the new repo, `gh repo unarchive` and
`gh repo rename the-continent` the archive. After E.7.5, `gh repo edit --visibility private` hides the repo but
can't take back what was seen. That's why E.4 and E.6 scan before it. Local: `git fetch <bundle>`, then
`git checkout -B main <old sha 4b818a2 or later>`.

### Block checklist: acceptance criteria (biome/structure-locked blocks)
Coral + sea pickles + sniffer egg (warm-ocean ruins) → Warm Ocean · blue/packed ice (icebergs) → Frozen Ocean ·
mycelium, huge mushroom blocks → Mushroom Fields · mud, mangrove roots/propagule → Mangrove · pale oak,
eyeblossom, resin/creaking heart → Pale Garden · dark oak → Dark Forest · lily pad, blue orchid → Swamp · cherry,
pink petals → Cherry Grove · cocoa, melon, jungle wood → Jungle · bamboo → Bamboo Jungle · acacia → Savanna · red
sand, terracotta colours → Badlands · cactus, cactus flower, dry grass → Desert/Badlands · podzol, large ferns,
sweet berries → Old Growth Taiga/Taiga · powder snow, snow → Grove/Snowy Slopes · emerald ore → mountain biomes ·
sculk family → Deep Dark · moss, azalea, glow berries, spore blossom, dripleaf → Lush Caves · pointed dripstone →
Dripstone · wildflowers → Birch/Meadow · bee nest → Plains/Meadow/Birch/Cherry · sunflower → Sunflower Plains ·
turtle egg + conduit (buried treasure) → Beach · kelp → Ocean · sponge, prismarine, sea lantern → Deep Ocean
(monuments) · heavy core → trial chambers · bell → guaranteed village.
**26.2+:** cinnabar, sulfur, potent sulfur, sulfur spike → Sulfur Caves.
**26.3+:** poplar log, red/orange/yellow poplar leaves, red shrub, shelf mushroom → Dappled Forest.

### Risks
- **Forge has one major per release.** A 26.1.2 jar may refuse 26.1 and 26.1.1. The README says exactly what each
  jar runs on.
- **Quilt is beta-only on 26.x.** B.4 decides whether it's claimed at all.
- **Registration timing on NeoForge and Forge.** If the biome source or density function codec isn't registered
  before datapacks load, the world type silently disappears. That was the same trap on Fabric (see the 0.2.0 risks).
- **Adding biomes can start a `FeatureSorter` cycle** at startup. It's the first suspect for a crash in Stage C.
- **The map differs by band.** An old world upgraded to 26.2 or 26.3 gets the new biomes only in chunks generated
  after the upgrade, with seams at old chunks.
- **Carried over, still true:** land structures are down to luck · strongholds sit under the sea ·
  `/locate structure` doesn't find the guaranteed village · ~2% of seeds get a zombie village there.

**Rollback:** see the end of Stage E.

## Backlog
- Coastline review leftovers (minor): `steepestSlope()` per segment · Coastline javadoc gradient numbers ·
  `bearing()` can return 2π · river mouth angle · a tangent river isn't refused.
- Owner: live-test 0.2.0 (the borders, the village at (−68, 104)) and, in `runClient`, check that the World Type
  button shows "The Continent". Remove or replace `extended-build-limit` in the modpack (gotchas 2026-10-19).

## Done

Everything through v0.2.0 (the 1.21.11 scaffold through ship-ready, CI and releases, coastline, mountains,
ragged borders, the day-cycle and client gametests, branch cleanup) is in git history. The full plan as it stood
is `git show f73f7e2:TODO.md`, and the reasoning is in `.agent/memory/decisions.md`.
