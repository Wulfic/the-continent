# Decisions

> **Append-only.** Newest entries at the bottom. Never edit or delete a past decision —
> if it gets reversed, append a new entry that says so and links back to it.
>
> Record a decision when a real alternative existed. "Used the standard library function"
> is not a decision. "Chose Postgres over SQLite because we need concurrent writers" is.
>
> Write the **reasoning**, not the diff. Git already stores what changed; it cannot store
> why, or what you rejected.

## Entry template

```markdown
## YYYY-MM-DD — Short title
**Context:** what forced the decision
**Choice:** what we did
**Why:** the actual reason
**Rejected:** what we didn't do, and why not
**Affects:** src/path/to/file.ts
```

---

## 2026-08-12 — Agent memory is repo-local files, not an MCP server
**Context:** Instructions mandated persisting decisions to the `mem0` MCP server, but
`mem0` was never present in the active `.mcp.json`, so nothing was actually being recorded.
**Choice:** Memory lives in `.agent/memory/` as committed Markdown: `decisions.md`,
`gotchas.md`, `state.md`.
**Why:** Works identically in Copilot and Claude Code with no network call, survives a
dead server, and shows up in code review where it can be corrected. A memory write that
silently no-ops is worse than none, because it feels like it worked.
**Rejected:** `mem0` MCP — cross-project recall wasn't worth an invisible failure mode on
the one guarantee that matters most. Also rejected keeping both as a mirror: two sources
of truth means neither is trusted.
**Affects:** `AGENTS.md`, `.agent/memory/`, `.github/skills/recall-session`, `.github/skills/save-memory`

## 2026-08-12 — Dropped the `think` MCP server
**Context:** Every skill routed reasoning through `mcp_wulfnet-think_think_invoke_tool`
before acting.
**Choice:** Removed the server and all call sites. Reasoning happens natively; the
`think-plan` skill was replaced by `plan-work`, which produces a written plan in `TODO.md`.
**Why:** Opus 5 reasons natively. The round-trip added latency and a failure mode without
improving output. A plan written to a file is also a checkable artifact — an MCP call is
not, which matters under Copilot where step-skipping can't be enforced.
**Rejected:** Keeping it for the `criticize` operation — self-review against an explicit
checklist in `code-review` covers the same ground without the dependency.
**Affects:** `AGENTS.md`, `.mcp.json`, all skills that previously called `think`

## 2026-08-12 — MCP surface cut to `github` + `context7` only
**Context:** Nine MCP servers were configured. `filesystem` and `git` had zero references
in any skill — dead config. The rest duplicated capability that is now native to both
Copilot and Claude Code.
**Choice:** Dropped `filesystem`, `git`, `gitnexus`, `playwright`, and `context-mode`.
Kept `github` (with a `gh` CLI fallback) and `context7`.
**Why:** The bar is now "does this do something native tools cannot?" Only two clear it.
`context7` serves live docs that training data can't; `github` returns structured JSON for
issues/PRs/CI. Everything else was a network round-trip and a failure mode in exchange for
what grep, the terminal, and native web fetch already do.
**Rejected:** Keeping `gitnexus` for cross-repo symbol lookup — real capability, but it
doesn't pay for itself on single-repo work, which is the norm here. Keeping `playwright`
for E2E — see the separate entry below; that turned out to be a category error on our part.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`, `AGENTS.md`,
`.github/copilot-instructions.md`, `code-explore`, `research-docs`

## 2026-08-12 — E2E runs through the project's own runner, not an MCP browser
**Context:** Dropping the `playwright` MCP server looked like it would remove E2E testing
capability, since `test-iterate` routed E2E through the old `web-task` skill.
**Choice:** E2E tests are committed spec files executed from the terminal
(`npx playwright test`, `npx cypress run`). The `web-task` skill was deleted.
**Why:** The playwright *MCP server* and Playwright *the test library* are different
things, and conflating them was the original mistake. Ad-hoc agent browser-driving isn't
a test — it can't run in CI and it isn't repeatable. Committed specs are strictly better,
and they need a terminal, not an MCP.
**Rejected:** Keeping the MCP for exploratory UI checks — real but rare, and native web
fetch plus the runner's own `--headed` mode and trace viewer cover the debugging case.
**Affects:** `test-iterate`, `AGENTS.md`, deleted `web-task`

## 2026-08-12 — Merged `git-ops` and `github-workflow` into `git-github`
**Context:** Two skills split one linear workflow: branch → commit → push → PR → CI.
**Choice:** Single `git-github` skill covering local git and GitHub remote operations.
**Why:** The split forced loading two skills for one continuous task and duplicated the
pre-commit checklist across both. The seam was in the docs, not in the work.
**Rejected:** Keeping them separate as "local vs remote concerns" — a clean distinction on
paper that nothing in the actual workflow respects.
**Affects:** `.claude/skills/git-github/`, `.github/skills/git-github/`, `AGENTS.md`,
`.github/copilot-instructions.md`

## 2026-08-12 — Destructive-action doctrine: prose first, permission rules as backup
**Context:** The template had no unified stance on irreversible operations — only scattered
rules in `git-github` (force-push, `.env`). Nothing covered filesystem deletion, database
drops, container/volume pruning, or the guards that code *we write* should carry. Needed a
gate that survives Copilot (no permission layer, no hooks) and a truncated context.
**Choice:** Two layers. (1) Doctrine — a `Destructive Actions` section in `AGENTS.md` as
rule zero, the `guard-destructive` skill holding the full procedure, and per-skill guards
in `code-review`, `git-github`, `build-run`, `plan-work`, `test-iterate`. (2) Mechanical —
`permissions.deny` (unrecoverable) and `permissions.ask` (destructive-shaped) in a new
`.claude/settings.json`. The doctrine is primary; the rules are a seatbelt.
**Why:** Permission rules match on **command prefix**, so `cd sub && rm -rf ../..` walks
straight past them, `PowerShell` coverage is unverified, and Copilot has no permission
layer at all. Anything that only exists as config would therefore cover roughly half the
actual usage. Conversely, prose alone loses to a truncated context. Each layer covers the
other's blind spot, and stating the rules' limits in writing is what stops them being
trusted past their range.
**Rejected:** A `PreToolUse` command-hook script — it would need to be cross-platform and
correct, and a hook that silently no-ops is worse than none (same reasoning that dropped
`mem0`). A `type: "prompt"` hook classifying every shell command semantically *would* catch
the compound-command cases, but costs a model call on every `ls`; documented in
`guard-destructive` as an opt-in escalation instead of a default. Also rejected
`disableBypassPermissionsMode` — locking the owner out of their own tool without asking.
**Affects:** `AGENTS.md`, `.github/copilot-instructions.md`, `.claude/settings.json`,
`.claude/skills/guard-destructive/`, `.github/skills/guard-destructive/`, `code-review`,
`git-github`, `build-run`, `plan-work`, `test-iterate`, `TODO.md`

## 2026-10-01 — No AI co-author attribution on commits or PRs
**Context:** Claude Code appends `Co-Authored-By: Claude …` to commits and a "Generated with
Claude Code" line to PR bodies by default. Owner wants commits signed by the git user only.
**Choice:** `.claude/settings.json` sets `attribution: { commit: "", pr: "" }` (current key)
plus `includeCoAuthoredBy: false` (deprecated key, kept for older Claude Code versions). The
`git-github` skill (both trees) states the rule in prose for Copilot and any agent that
ignores settings.json.
**Why:** The setting is what actually stops the harness injecting the trailer; prose alone
loses to the harness's own attribution instruction. Prose covers Copilot, which never reads
settings.json.
**Rejected:** A `commit-msg` git hook stripping the trailer — works for every tool, but it's
an extra installed artifact per clone and silently rewrites messages; revisit only if a
trailer still slips through.
**Affects:** `.claude/settings.json`, `.claude/skills/git-github/SKILL.md`,
`.github/skills/git-github/SKILL.md`

## 2026-10-16 — The Continent: custom BiomeSource + layout density function, not multi_noise
**Context:** The mod needs a *fixed* hand-drawn map (named biomes in named compass regions,
ocean everywhere else). Status: proposed in `TODO.md`, pending owner review of Q1–Q5.
**Choice:** One pure-Java layout model (biome seed points + coast + river) feeds two
adapters: a custom `BiomeSource` (surface biome from layout, cave biome from sampler depth)
and a custom `DensityFunction` type that drives continentalness/erosion/ridges in a
namespaced copy of the vanilla overworld noise router. The world preset itself is datapack JSON.
**Why:** Vanilla density functions have no x/z coordinate input (only `y_clamped_gradient`),
so `multi_noise` + datapack can't place a biome at a chosen location. Biomes alone aren't
enough either: terrain comes from the noise router, so without the density function you'd
get vanilla land everywhere with "ocean" biome painted on it. Keeping one layout as the
single source of truth stops biome borders and terrain from drifting apart.
**Rejected:** Pure datapack `multi_noise` (can't express geography). Mixin into
`MultiNoiseBiomeSource` (touches every world, brittle across versions). TerraBlender-style
regions (still noise-placed). Overriding `minecraft:overworld/*` density functions in place
(would reshape normal worlds too).
**Affects:** `TODO.md` (The Continent plan), future `src/main/java/.../layout/`, `.../world/`,
`src/main/resources/data/the_continent/`

## 2026-10-16 — The Continent: 1000-block radius; only block-gating structures are required
**Context:** Owner answered the plan's Q1–Q5. The earlier proposal (radius ~3072) was sized
so that land structures and the first stronghold ring would sit on the continent.
**Choice:** Land radius ~1000. Mod id `the_continent`, package `com.wulfic.continent`, Mojang
mappings. Add Beach, Sunflower Plains, and an Ocean → Deep Ocean filler to the owner's list.
The layout is the same for every seed. The census test *requires* only structures that gate a
block (ocean monument, warm-ocean ruin, buried treasure, trial chambers, stronghold), and only
reports land structures.
**Why:** Every structure-exclusive *block* comes from an ocean structure or an underground
one, and those spread through the infinite ocean. Land structures (temples, villages,
outposts, igloo, mansion, trail ruins, ancient city) gate no block, so a small continent
that's quick to explore loses nothing the goal cares about.
**Rejected:** Radius ~3072: guarantees more structures, but triples walking distance for
no block. Moving stronghold rings onto land with a custom chunk generator or a `structure_set`
override: the first is scope creep, the second is global and would change normal worlds.
**Affects:** `TODO.md` (The Continent plan), future `layout/DefaultLayout.java`, Stage 6 census test

## 2026-10-16 — The Continent: one guaranteed village via a custom anchored placement
**Context:** Owner requires exactly one village on every seed. With 300–400-block regions,
vanilla village spacing (34 chunks) can leave the continent with none.
**Choice:** A custom `StructurePlacementType` (`the_continent:anchored`) in our own
`structure_set`, placing `minecraft:village_plains` at the layout's village anchor (−100, 130).
It returns true only when the world's biome source is `ContinentBiomeSource`, so normal worlds
are untouched. The anchor was moved from (−150, 100), which sat ~63 blocks from the Swamp
border and would have failed the plan's own 64-block margin.
**Why:** It goes through real structure generation, so the structure start and pieces are
registered (cats spawn, census can check the chunk) and the village is jigsaw-built to the
terrain. It's data-driven and needs no world-load side effects.
**Rejected:** Running `/place structure` once on first world load with a `SavedData` flag.
That's the fallback if `ChunkGeneratorStructureState` doesn't expose the biome source in
1.21.11, but it doesn't register structure pieces and it adds a one-shot world mutation that
must be guarded. Overriding vanilla `minecraft:villages` spacing: global, would change
normal worlds.
**Affects:** `TODO.md` Stage 6, future `world/AnchoredPlacement.java`,
`data/the_continent/worldgen/structure_set/guaranteed_village.json`

## 2026-10-16 — The Continent: Loom 1.18.4 release, not the template's snapshot or 1.19.1
**Context:** Stage 1 scaffold. The official `fabric-example-mod` 1.21.11 branch pins
`loom_version=1.18-SNAPSHOT`; the newest Loom release is 1.19.1. Minecraft is at 26.3, so
1.21.11 is no longer the version Loom's head is developed against.
**Choice:** `loom_version=1.18.4` with plugin id `net.fabricmc.fabric-loom-remap`.
**Why:** A `-SNAPSHOT` is not a pin, so builds could change under us. 1.18.4 is the release on
the line the template is tested with for 1.21.11. 1.19.x targets the unobfuscated 26.x
toolchain, which gains us nothing here and adds risk. Verified: build, tests, and `runServer` all pass.
**Rejected:** `1.18-SNAPSHOT` (unpinned). `1.19.1` (untested with 1.21.11 by the template;
revisit only if a 1.18.x bug forces it).
**Affects:** `gradle.properties`, `build.gradle`

## 2026-10-17 — Layout model: plain Voronoi + own value-noise warp, compass-quadrant coasts
**Context:** Stage 2 has to turn the seed table into a `sample(x, z, seed)` with no Minecraft
imports, called millions of times on worker threads.
**Choice:**
- **Record + static geometry helpers.** `ContinentLayout` is a record with `List.copyOf`
  fields. The geometry (nearest two seeds, distance to the polyline) lives in static methods,
  so the compact constructor can run the village-anchor validation before the record exists.
- **Unweighted nearest-seed Voronoi.** Border distance is the exact distance to the edge of
  the nearest seed's cell: the *minimum over all other seeds* of the bisector distance
  `(dj² − d1²) / (2·|s1 − sj|)`. It's 1-Lipschitz, so a warp of amplitude A moves it by at
  most A. (Measuring only to the second-nearest seed's bisector is wrong. At the village
  anchor, Forest is second-nearest at ~124 blocks, but the Swamp edge is ~101 away. The
  warp-40 validation test caught it.)
- **Own 2-octave value noise for the domain warp** (cells 256 + 128, slope ≤ ~0.016 per block, ≈ 0.5 at A = 32).
  A 24-block beach keeps at least ~12 blocks.
- **Coast style by compass quadrant** of the warped point: N = no beach + Frozen Ocean band,
  E/S = beach + Warm Ocean band, W = beach + no band. Past the band comes shallow Ocean, then
  Deep Ocean.
- **Village validation is fail-closed against the warp.** The anchor needs border and river-edge
  distance ≥ 64 + warpAmplitude on the *unwarped* layout, so no seed can push it under 64.
**Why:** Pure Java keeps the model unit-testable without a game. Unwarped Voronoi makes the
plan's bisector math exactly what the validation checks.
**Rejected:** Minecraft's `NormalNoise` for the warp: it breaks the no-MC-imports rule and the
plain unit tests. A weighted (power) diagram to make Sunflower Plains a "small patch": it's
unneeded until Stage 7 maps show it matters, and the test only gives it a smaller area floor.
Angle-sector coasts with configurable boundaries: four fixed quadrants are all the map needs.
**Affects:** `src/main/java/com/wulfic/continent/layout/`

## 2026-10-17 — River moved off the origin
**Context:** The plan's river polyline (−100,−450)→(−30,−100)→(80,150)→… puts its centre line
13 blocks from (0, 0). With a 20-wide river and a 32-block warp, the origin (spawn) sampled as
River on seed 12345.
**Choice:** The middle points move east to (30,−100) and (110,150). The centre line now clears
the origin by ~59 blocks, so ≥ 17 blocks of dry Plains stay at spawn on any seed. The river
mouth (1000,350) and source (−100,−450) are unchanged. Village-to-river clearance goes 157 → ~194.
**Why:** The origin has to be Plains: that's the spawn search's starting point (Stage 4 risk).
**Rejected:** Narrowing the river or the warp: it treats the symptom and leaves no margin.
Routing the river west of the origin: it would run toward the village anchor at (−100,130).
**Affects:** `layout/DefaultLayout.java`, `TODO.md` target-map table

## 2026-10-17 — All work goes on `main`, never a feature branch
**Context:** A cloud session started on its harness-assigned branch `claude/keen-sagan-t9clx9`.
The owner restated that every change goes on `main`.
**Choice:** Always check out `main` (fast-forward it to `origin/main`), commit there and push
`main`. This rule outranks any session-default or harness-assigned `claude/*` branch name.
**Why:** It's the owner's standing instruction. It's a solo repo, and stages are one commit
each on a linear history.
**Rejected:** Working on the session's assigned branch and opening PRs. The owner doesn't
want that.
**Affects:** every session, `git-github` workflow

## 2026-10-17 — Layout seed is derived from the seeded temperature field
**Context:** The layout warp needs a per-world seed (Q5: the seed varies border wobble). In
1.21.11 no world seed ever reaches a `BiomeSource`. `RandomState` only hands seeded
`NormalNoise` to density functions through `NoiseHolder` wiring, and `getNoiseBiome` only gets a
`Climate.Sampler`.
**Choice:** `world/LayoutSeed.derive(DensityFunction)` hashes the function's value at 3 fixed,
far-off, off-lattice probes (SplitMix64). The biome source passes `sampler.temperature()` and
caches the result per sampler *identity* (one volatile record, so it can't tear). **Stage 5's
layout density function must call the same `derive` on a wired copy of the vanilla temperature
definition** (`shifted_noise`, `minecraft:temperature`, xz_scale 0.25), or biomes and terrain
warp differently.
**Why:** Mixin-free and deterministic, and it gives a different warp per seed (tested on 1/2/−1).
It's a pure function of the sampler, so even if a mod passed another sampler, the cost is only a
rebind, never a wrong biome for that sampler.
**Rejected:** Mixin into `RandomState` to pass the raw seed: the scaffold has no mixins, and it's
brittle across versions. Capturing the seed in `SERVER_STARTING` into a mutable field: global
mutable state that is wrong for a second world in the same JVM, and empty before the server
exists. A seed-independent warp: it breaks Q5.
**Affects:** `world/LayoutSeed.java`, `world/ContinentBiomeSource.java`, future Stage 5 DF

## 2026-10-17 — Biome source codec carries no layout; caves live in the layout
**Context:** The plan said the `MapCodec` holds "layout + biome HolderSet", and left open whether
cave regions belong in the layout.
**Choice:** The codec is just `RegistryOps.retrieveGetter(BIOME)`, `flatXmap`-ed into a source
over `DefaultLayout`. It resolves every `layout.biomeIds()` entry, and an unknown or malformed id is
a logged `DataResult` error that names it. Caves are a `Caves` record in the layout:
Lush (−300, 50) / Dripstone (400, 400) Voronoi at the warped position, plus Deep Dark north of
z −350 at **block y ≤ 0**. The adapter uses vanilla climate depth ≥ 0.2 for shallow caves.
Islands keep their own biome all the way down.
**Why:** Q5 fixes one map for all seeds, so serialising it would only open a second source of
truth. Caves in the pure layout are unit-testable without a game. Deep Dark uses absolute y, not
depth, because the ancient city starts at absolute y −27 (checked in the 1.21.11 jar). A depth
threshold would miss that start under low terrain and over-cover it under the peaks.
**Rejected:** A datapack-configurable layout codec (scope creep). Deep Dark by depth ≥ ~1.1 like
vanilla (terrain-dependent; see above). Caves under the mushroom island (it would spawn hostile mobs
under a "safe" biome).
**Affects:** `layout/Caves.java`, `layout/ContinentLayout.java`, `world/ContinentBiomeSource.java`

## 2026-10-17 — Terrain: per-biome C/E/W profiles fed into vanilla's own splines
**Context:** Stage 5 has to make terrain follow the fixed map. Terrain comes from the noise router.
Its `offset`/`factor`/`jaggedness` splines read continentalness (C), erosion (E) and ridges (W).
**Choice:** A pure `layout/ContinentTerrain` turns the warped layout into target (C, E, W) values: a
per-biome `TerrainShape` profile, a coast ramp, an ocean shelf and deep ramp, and a river valley. A thin
`the_continent:layout` density function (one per channel) adds scaled vanilla noise for local detail.
The vanilla spline graph is copied unchanged under `the_continent:` and re-pointed at those three channels.
- **Continuous blend:** every seed weighs `(1 − (|p−sᵢ| − d_min)/D)²`. Each term is continuous in p,
  so the blend is continuous everywhere. `LayoutSample.blend/secondary` (blend toward the neighbour
  across the *nearest* edge) jumps at triple junctions, where the nearest edge flips between two
  neighbours, so terrain doesn't use it. That leaves those two fields with no consumer. They stay, because
  removing them means deleting `blendIsHalfAtBordersAndFullDeepInside`, and deleting a test is an
  absolute stop (AGENTS.md). That's the owner's call.
- **River valley pulls E to 0 as well as W to 0:** at E −0.9, W 0 still stands at y 82–105 (spline table).
- **Profiles avoid E 0.45–0.55:** vanilla's factor there is 0.6, which gives floating, shattered terrain.
  Validation rejects it.
**Why:** The vanilla splines already produce coherent shapes (factor and jaggedness match the offset), so
picking a point in their input space per biome gets "mountains look like mountains" for free. The terrain
stays deterministic per layout. The seed only varies the warp and the detail noise.
**Rejected:** Writing `offset`/`factor`/`jaggedness` directly per region: three hand-made curves that must
agree, and no vanilla look. Blending via `secondary`/`blend`: discontinuous (above).
Overriding `minecraft:overworld/*`: it would reshape normal worlds.
- **The JSON is copied, never hand-written:** extract `data/minecraft/worldgen/{density_function/overworld,
  noise_settings/overworld.json}` from the merged 1.21.11 jar. Copy `ridges_folded, offset, factor,
  jaggedness, depth, sloped_cheese` and the noise settings. Text-replace only the quoted references
  `"minecraft:overworld/{continents,erosion,ridges,ridges_folded,offset,factor,jaggedness,depth,sloped_cheese}"`
  with `the_continent:`. `TerrainResourcesTest` proves the copies equal the jar's once mapped back.
- **Detail amplitudes live in Java (`ContinentTerrain.*_DETAIL`), not in the JSON:** validation can then
  prove that profile E plus detail stays below the shattered band.
**Affects:** `layout/TerrainShape.java`, `layout/TerrainSample.java`, `layout/ContinentTerrain.java`,
`world/LayoutDensityFunction.java`, `data/the_continent/worldgen/**`

## 2026-10-17 — Village guard reads the noise router, because the biome source is private
**Context:** Stage 6's plan guarded `the_continent:anchored` with "the biome source is a
`ContinentBiomeSource`". In 1.21.11, `ChunkGeneratorStructureState.biomeSource` is private, with no
getter (javap). The plan's fallback was a first-load `/place structure` with a `SavedData` flag.
**Choice:** Guard on `LayoutDensityFunction.drivesTerrainOf(state.randomState())`. It unwraps the router's
continentalness (registry holder → flat_cache) and checks for our layout function. Position is tested
first, so this only runs for the anchor chunk.
**Why:** It's public API, it's exactly as specific (only the Continent noise settings route terrain
through the layout), and it keeps real structure generation: pieces are registered, cats spawn, and the
census can check the start. If a datapack paired our settings with another biome source, vanilla's own
biome check on `village_plains` would still reject a non-plains anchor.
**Rejected:** The `/place` fallback: a one-shot world mutation with no structure pieces. An access
widener on the private field: it works, but it's an extra build artifact for no gain over a public signal.
**Affects:** `world/AnchoredPlacement.java`, `world/LayoutDensityFunction.java`

## 2026-10-17 — Census runs on placement math + Structure.generate, not on a generated world
**Context:** Stage 7.2 needs "is there a monument / warm ruin / treasure / trial chamber / stronghold
within 3000 blocks" on 3 seeds. `/locate` and `findNearestMapStructure` need a live `ServerLevel` of a
Continent world, and a gametest server's level is a normal one.
**Choice:** `gametest/StructureCensus` repeats world generation's own decision without a world. It
builds `ChunkGeneratorStructureState.createForNormal` for the seed, takes each placement's candidate chunks
(random-spread regions, ring positions, our anchor) nearest-first, and accepts the first where
`isStructureChunk` holds and `Structure.generate(...).isValid()`. Spacing-1 sets (buried treasure) are
pre-filtered by the sea-level biome, because each `generate` costs a ~4 ms heightmap probe.
**Why:** It's the same code path world gen uses (placement, then biome and terrain checks in `generate`),
deterministic, and ~8 s per seed. The required and reported lists come straight from the plan's block checklist.
**Rejected:** Creating a temporary Continent `ServerLevel` in the gametest (heavy, and it fights the
gametest framework). A headless server + `/locate` per structure (slow, and `/locate` can't see our anchored
placement anyway).
**Affects:** `src/gametest/.../StructureCensus.java`, `ContinentCensusGameTest.java`

## 2026-10-17 — Badlands straddles y 74 so red sand generates
**Context:** The Stage 8 block walk found red sand in only 4 chunks, a coastal strip. Vanilla's badlands
surface rule lays terracotta bands at y ≥ 74 and red sand only below that, above water. Badlands was
profiled at y 100–130, so it was all terracotta. Red sand comes only from badlands; coloured terracotta
can be crafted from dye.
**Choice:** Badlands profile (0.05, 0.0, 0.39): low ground at y ~65 with hills to ~85. The gametest
`badlandsHasRedSandFlatsAndTerracottaHills` requires ≥ 25% of columns below y 72 and ≥ 10% above y 76 on
seeds 0 and 12345. Measured: 50%/22% and 31%/55%.
**Why:** Ridges between 0.35 and 0.45 is the spline's mid→high step at E 0, so the exact value sets the
split: 0.36 gave 69%/4%, 0.42 gave 24%/68%, 0.39 balances both seeds.
**Rejected:** Keeping the high badlands and relying on the coastal strip (too thin to be a real source).
Eroded/wooded badlands variants (more biomes, against the minimum-set goal).
**Affects:** `layout/DefaultLayout.java`, `ContinentGameTest`

## 2026-10-17 — CI: one workflow, GitHub Release on a `v*` tag, behind a tested guard
**Context:** The owner asked for CI that publishes the jar. Options offered: a GitHub Release per tag,
a rolling "latest" pre-release from `main`, run artifacts only, or Modrinth/CurseForge too. The owner
picked release-on-tag and asked for `v0.1.0` to be tagged once CI is green.
**Choice:** `.github/workflows/build.yml`. The `build` job (read-only token) runs `./gradlew build`,
gametests included, on every push to `main`, every PR and every tag, and uploads `build/libs/*.jar`.
The `release` job runs only for `refs/tags/v*`. It is the only job with `contents: write`. It runs
`.github/scripts/release-guard.sh` and then `gh release create --verify-tag`, with no `--clobber`. The
guard checks that tag == `v` + `gradle.properties` version, that the tag is the checkout and is on
`origin/main`, that the jar's `fabric.mod.json` id and version match, and that there is a `CHANGELOG.md`
section. That section becomes the notes. `release-guard-test.sh` (32 cases, mutation-checked) runs in
every build.
**Why:** No secrets are needed, and a release can't come from a branch, a mismatched version, or a
re-run over a published one. Splitting the jobs keeps the write token away from the Gradle build, which
runs repository code. The guard is a script rather than inline YAML so it can be tested.
**Rejected:** `gradle/actions/setup-gradle`: v6 moved caching into a closed-source component, and
upgrading means accepting its Terms of Use. `setup-java`'s `cache: gradle` is open-source and enough here.
`softprops/action-gh-release`: an extra third-party action, when `gh` is already on the runner. CI
deriving the version from the tag (`-Pversion=`): the committed `gradle.properties` would then disagree
with the release. One job that builds and releases: the write token would be visible to the build.
**Affects:** `.github/workflows/build.yml`, `.github/scripts/release-guard*.sh`, `CHANGELOG.md`, `README.md`

## 2026-10-17 — Manual release path: `workflow_dispatch` with a version tags main from CI
**Context:** The owner asked for `v0.1.0` to be tagged once CI was green, but a cloud session can't push
tags (gotchas.md, same date). The only GitHub write left was triggering a workflow.
**Choice:** `build.yml` takes a `release` input on `workflow_dispatch`. On `main` only, the release job
creates the annotated tag `v<input>` locally at HEAD. Then the unchanged `release-guard.sh` runs, and only
after it passes is the tag pushed with the job token and `gh release create --verify-tag` run. Manual runs
have their own concurrency group, so a push to main can't cancel a release part-way.
**Why:** It is the same guard and the same publish step as the tag path, with no new secret. It also gives
the owner a release button in the GitHub UI. A tag pushed with `GITHUB_TOKEN` starts no second workflow
run, so there's no duplicate release attempt. Simulated with good, wrong-version, injection, `..`,
non-main and existing-tag inputs: only the good one passes, and nothing executes from the input.
**Rejected:** Letting `gh release create` make a lightweight tag (releases use annotated tags, per the git
skill). Asking the owner to push the tag by hand (this works too and stays documented, but it would have
left the release blocked on them).
**Affects:** `.github/workflows/build.yml`, `README.md`

## 2026-10-17 — Coastline: hand-laid radius per bearing, distance normalised by its gradient
**Context:** The owner asked for a less circular coast (7.3 polish). Q5 fixes the layout for every seed,
so the shore shape can't come from seeded noise.
**Choice:** `layout/Coastline`: 24 radii, one every 15° clockwise from north, joined by a periodic
Catmull-Rom spline. `distance = (r(θ) − ρ) / √(1 + (r'(θ)/max(ρ, r/2))²)`. That's `r − ρ` divided by its
gradient length, so it measures across the shore. Measured on the default shore: within 15% of the true
distance up to 150 blocks either side, and a gradient of at most 1.18 at sea and 1.25 within 150 blocks
inland. The design has bays at 30/120/180/240/315° and headlands at 0/60/150/210/300°, radius 880–1120.
Validation: |dr/dθ|/r ≤ 1, every seed inside, island channel measured against the real shore, and the
river leaves the land exactly once.
**Why:** Hand-laid matches the rest of the map. A star-shaped r(θ) makes "inside" and validation trivial.
Without the normalisation, bands along slanted shores shrank by up to ~25% (the test fails on that mutant).
**Rejected:** An explicit polygon with exact distance (O(vertices) per sample and visibly straight segments
unless dense). A fixed-seed low-frequency domain warp on the coast (no control over where bays land; it
could close the island channel or swallow the river mouth). Capping the distance inland to hide the
origin's bearing-dependent value: nothing reads it past 128 inland, so I documented it instead.
**Known limit:** Deep inland the value is just "large". At the origin it differs by bearing but stays
> 200, past every inland threshold.
**Affects:** `layout/Coastline.java`, `layout/ContinentLayout.java` (`landRadius` → `coastline`),
`layout/ContinentTerrain.java`, `layout/DefaultLayout.java`, `docs/continent-map.png`

## 2026-10-17 — Tall mountains: raised top slide + per-region uplift with a peaks noise
**Context:** The owner asked mid-task for "tall and epic" mountains. Jagged Peaks topped out at y ~253, because
vanilla's router fades density to air from y 240 to 256 (`final_density` and `preliminary_surface_level`), whatever
the spline inputs.
**Choice:** Raise both top slides to 288–304. Add `TerrainShape.uplift` (+0.1 ≈ +13 blocks), blended like the rest
and faded to 0 across the coast ramp and in the river valley. Feed it into `offset` (`offset = offset_spline + layout
uplift`), so depth and the preliminary surface both see it. The uplift's own detail comes from a dedicated
seeded noise, `the_continent:peaks` (octaves at 256/128/64 blocks), which makes separate summits and saddles.
The ridge noise couldn't do that, because PV falls on both sides of W = 2/3. Interiors get per-profile E/W detail
amplitudes. Every step stays a convex mix, so profile E + detail < 0.45 still keeps every point out of the
shattered band.
**Why:** It's the minimal deviation from vanilla's graph, and `TerrainResourcesTest` still proves nothing else changed
(it undoes exactly two slide patches and compares). Uplift only touches the layout regions that ask for it.
**Rejected:** Raising the world height (changes the dimension type for every save). Pushing C/E/W further (the splines
are already at their ceiling there). Only ridges detail on the peaks (gave a table mountain pressed against the slide).
**Affects:** `layout/TerrainShape|TerrainSample|ContinentTerrain|DefaultLayout`, `world/LayoutDensityFunction`,
`density_function/overworld/{offset,offset_spline}.json`, `noise/peaks.json`, `noise_settings/continent.json`

## 2026-10-18 — Client gametests on Xvfb for what only a client sees
**Context:** The owner reported the day stuck at noon and asked to be sure the Nether and End still work. Server
gametests can't answer either: the gametest server doesn't run on the Continent preset, and the sun is
client-side.
**Choice:** `enableClientGameTests = true` and a `fabric-client-gametest` entrypoint, `ContinentClientGameTest`.
It creates a real singleplayer Continent world, checks the day cycle (client sun angle, server sky light), lights
a Nether portal and rides it, then opens the nearest stronghold's portal with real eyes of ender and rides it to
the End. CI runs it in its own step under `xvfb-run` with Mesa's software renderer (~1 min here).
**Why:** It's the only test that plays the game the way the owner does. Loom doesn't hang `runClientGameTest` off
`check`, so `./gradlew build` stays display-free.
**Rejected:** A server gametest querying the preset's dimension type (proves the data, not the experience).
Fabric's `createServer` dedicated-server path (no sun to look at).
**Affects:** `build.gradle`, `src/gametest/`, `.github/workflows/build.yml`

## 2026-10-18 — Ragged borders: a fine, clamped warp on the region lookup only
**Context:** The owner said borders between biomes were "far too neat of a line". The broad warp (cells
256/128, ±32) only bends borders gently, so Voronoi edges read as ruled lines. Owner's picks: "natural
ragged" (±~24, stray patches) and borders only, not the coast.
**Choice:** `ContinentLayout.borderRoughness` (24 in `DefaultLayout`). A four-octave value noise
(`WarpNoise.fine`, cells 96/48/24/12) pushes the point the Voronoi lookup reads, on top of the broad warp.
The noise is boosted ×2.5, then held to the unit disk (`roughScale`), so the push never exceeds 24 and averages
~18. `ContinentTerrain` blends regions at the same point, so hills follow the ragged line. The coast, river,
islands, caves and coast quadrants still read the broadly warped point.
**Why:** Value noise rarely strays from 0: unboosted, the mean push was 8.5 of 24 and the borders only
wiggled (seen in rendered crops). The gain-and-clamp keeps a hard bound, which the village check needs.
**Rejected:** Roughening the coast too (owner: no; it squeezes the 24-block beaches). Cells 64/32/16 (wiggly
but no meander). Cells 128-16 (smoother, fewer jags). A per-axis bound for the fine warp (√2 × 24 = 34
would not fit the village anywhere in Plains).
**Also:** The village check had understated the broad warp. It moves x and z by up to 32 *each*, so up
to 32·√2 = 45. The check now needs 64 + 32·√2 + 24 = 133.3 from a border, and the anchor moved from
(−100, 130), which has 101, to (−68, 104), the best spot in Plains (135.2). `AnchoredPlacement` follows it.
**Affects:** `layout/WarpNoise|ContinentLayout|ContinentTerrain|DefaultLayout`, `docs/continent-map.png`,
README, CHANGELOG

## 2026-10-19 — The client gametest's integrated server sprints
**Context:** The client gametest stalled on CI: a client drawing < 20 frames/s keeps the lock-stepped server
behind real time, and a server that is behind doesn't finish chunks (gotchas.md 2026-10-19).
**Choice:** Sprint the integrated server for the whole test (`ServerTickRateManager.requestGameToSprint` on
`SERVER_STARTED`), and hold client ticks to ≥ 60 ms so every machine reproduces the runner.
**Why:** It removes the dependence on how fast the runner renders. Game time still advances one step per tick,
so the day-cycle checks mean the same thing.
**Rejected:** Cutting render cost (menu blur, render distance): still hostage to runner speed. `/tick rate`:
the client's tick clock follows it, and every tick waits out wall time. A mixin forcing `haveTime()`: then
`waitUntilNextTick` never returns. A larger runner: costs money, same fragility.
**Affects:** `src/gametest/.../ContinentClientGameTest.java`

## 2026-10-20 — 26.x support: three bands on one branch, four loaders from one source tree
**Context:** The owner asked for Quilt, Forge and NeoForge on the 26.x versions, with as few bands and branches
as possible, using mcMMO-Singleplayer as a reference. mcMMO runs a branch per band (9 branches). To keep them in
sync it needs `drift-audit.py`, `Backport-of:` trailers and byte-identity audits, and back-ports still went
missing. 26.2 and 26.3 also add biomes that are the only natural source of some blocks (Sulfur Caves, Dappled
Forest), so each band's map differs.
**Choice:** (planned, not implemented; TODO.md Stage A.1 can overturn the tooling)
- Three bands: 26.1–26.1.2 (built on 26.1.2), 26.2 and 26.3.
- All of them on `main`: Stonecutter for the version axis, and `common`/`fabric`/`neoforge`/`forge` subprojects
  for the loader axis. Quilt gets the Fabric jar, tested on Quilt Loader, with no jar of its own.
- The layout builds from whatever biome ids the running game's registry has, so one source tree serves every
  band without a version table.
- 1.21.11 is frozen at 0.2.0 on its own branch, `mc/1.21.11` (owner, 2026-10-20), which takes critical fixes only.
**Why:** Every minor release changes the data pack format, the Fabric API line, NeoForge's line and the biome
set, so three bands is the floor. mcMMO measured the same split. Forge publishing one major per release (62/63/64
for 26.1/26.1.1/26.1.2) and NeoForge having no stable 26.1 or 26.1.1 line rule out anything wider for those two
loaders, unless a boot test on each version proves otherwise. Only `ContinentMod` touches Fabric, so each loader's
own code is one class.
**Rejected:** A branch per 26.x band (mcMMO's drift overhead; one frozen branch has nothing to drift from). Architectury API (a runtime dependency for players, to
save four registrations). A repo per loader. One jar across 26.1–26.3 (the data pack format and API lines differ).
**Affects:** `TODO.md` (the 26.x plan), `README.md` (Supported versions), and later `build.gradle`,
`settings.gradle`, `src/` → `common/`, `fabric/`, `neoforge/`, `forge/`

## 2026-10-20 — MIT licence; the agent files go public
**Context:** The repo had no licence, so it was "all rights reserved", and it is going public (TODO.md Stage E).
**Choice:** MIT (owner). `LICENSE` at the root, and `"license": "MIT"` in `fabric.mod.json`. `.agent/`,
`AGENTS.md`, `CLAUDE.md`, the skills and `TODO.md` stay in the public repo, provided the Stage E.4 sweep
finds nothing sensitive in them.
**Rejected:** Leaving it unlicensed (nobody could legally fork or contribute). Stripping the agent tooling
(owner: fine to publish).
**Affects:** `LICENSE`, `src/main/resources/fabric.mod.json`

## 2026-10-20 — common is a Loom project whose sources each loader compiles into its own jar
**Context:** A.2 split the mod into `common` and `fabric` (and later `neoforge`, `forge`). Common needs Minecraft
classes to compile and Fabric Loader to run its unit tests (`fabric-loader-junit` boots the registries).
**Choice:** `common` applies Loom (Minecraft + Mojang mappings, loader for the tests) and exposes its source and
resource directories through consumable `commonJava`/`commonResources` configurations. `fabric` compiles them into
its own `compileJava`/`processResources`/`sourcesJar` (the MultiLoader-Template pattern). There is still one jar,
`the_continent-<version>.jar`, and common's own jar is never shipped. `compileOnly …namedElements` is only for IDEs.
A first `Continent` class holds `MOD_ID` and `LOGGER`, so `ContinentMod` is Fabric's registrations alone.
**Why:** Shipping one jar per loader with no runtime dependency on a common jar. Loom remaps the jar once with
common inside it. Keeping Loom in common means the unit tests run exactly as before (199 = 198 moved + 1 new).
**Rejected:** ModDevGradle's vanilla (NeoForm) mode for common, as MultiLoader-Template does: it would move the
unit tests off `fabric-loader-junit`, a rewrite with no gain while Fabric is the reference loader. Depending on
common's jar (`implementation project(':common')`): a second jar to ship, or a jar-in-jar for nothing.
**Affects:** `settings.gradle`, `build.gradle`, `common/build.gradle`, `fabric/build.gradle`

## 2026-10-20 — Bands as Stonecutter nodes: per-band properties, data overlays, band-labelled jars
**Context:** A.4/A.5 added 26.2 beside 26.1.2. Measured against the jars: 26.2 differs in one class
(`LayoutDensityFunction`: codec constant, `mapChildren`), one client-gametest call, and two copied vanilla JSON
files. 26.3 replaced the worldgen engine (TODO A.4b).
**Choice:**
- `common` and `fabric` are Stonecutter branches; each band is a node named after the version it compiles
  against. The sources are committed with 26.1.2 active.
- Per-band pins live in the root `gradle.properties` keyed by node (`fabric_api_version.26.2`,
  `minecraft_range.26.2`, `band_label.26.2`), so nothing is duplicated per branch.
- Code differences: `//? if >=26.2` blocks. Data differences: every copied vanilla terrain file lives in
  `common/versions/<band>/src/main/resources`, regenerated by `tools/terrain_copies.py` and checked against the
  band's own jar by `TerrainResourcesTest`.
- Jars are `the_continent-<version>+fabric-mc<label>.jar`. `fabric.mod.json`'s version stays plain.
  `BandLabelTest` holds the label to the declared range (mutation-checked: a widened range goes red).
**Why:** Small API differences stay one line apart in one file, and the JSON can't hold comments. Keeping all
copied files per band, even the ones identical in 26.1.2 and 26.2, means 26.3's rewrite only adds a folder.
**Rejected:** per-node `gradle.properties` files (the same pin duplicated in every branch); shared JSON with a
26.2 override for the two changed files (shadowing by classpath order is exactly the trap in gotchas 2026-10-20).
**Affects:** `settings.gradle`, `stonecutter.gradle`, `gradle.properties`, both branch `build.gradle`s,
`tools/terrain_copies.py`, `common/versions/*/src/main/resources`

## 2026-10-20 — 26.3: compile-time layout seed, a Worldgen facade, and a copy set derived from the jar
**Context:** 26.3 replaced the density-function engine (functions compile to `DensitySampler`s through
`DfRewriteRule`s), made the noise router private, moved `final_density` and the surface levels out of the noise
settings, renamed `flat_cache`/`shifted_noise`, and rewrote the noise format. All 118 spline point lists are
unchanged, so the terrain tuning stands.
**Choice:**
- `LayoutDensityFunction` on 26.3 derives the layout seed once, in `compileSampler`, from the compiled
  `seed_source`, so there's no lazy volatile. `ContinentBiomeSource.createResolver` derives it from
  `sampler.temperature()`. Both sample the same seeded noise, so the floats agree (`LayoutDensityFunctionTest`).
- `drivesTerrainOf` unwraps the compiled climate sampler (cache, axis slices, context-bound) to find ours, via
  `LayoutDensityFunction.find`.
- `world/Worldgen` holds the reads whose API moved (`climate`, `biomes`, `randomState`, `layoutSeed`), and
  `TestWorlds` is the tests' seam (`router`, `sample`, `layoutSeed`). The command, tests and gametests call these
  and keep `//? if` blocks to a few lines.
- `AnchoredPlacement` extends 26.3's `AbstractSpreadingStructurePlacement`. `TYPE` is whatever the registry holds:
  the `MapCodec` from 26.3, a `StructurePlacementType` before.
- Every terrain file, ours included, is per band. The copy set is computed (vanilla functions reachable from the
  noise settings that lead to the spline inputs) by both `tools/terrain_copies.py` and `TerrainResourcesTest`, so
  26.3's three new copies came from the rule, not a list. 26.3's `peaks.json` is vanilla's parity form
  (`NormalNoise.createParity`), asserted by a test.
**Rejected:** per-band source overlays for whole classes (Gradle can't exclude the shared copy for one node
only); hand-maintained copy lists per band (the next format change would silently miss a function).
**Affects:** `common/src/main/java/com/wulfic/continent/world/*`, `common/src/test/...`, `fabric/src/gametest/...`,
`tools/terrain_copies.py`, `common/versions/26.3/src/main/resources`

## 2026-10-20 — Every shipped jar boots on its loader's real server, fingerprinted; no per-loader gametests
**Context:** TODO B.3: the Fabric gametests are deep (11 server tests, a client test, a census). Porting them to
NeoForge's and Forge's gametest frameworks would mean three copies of a suite to keep in step.
**Choice:** `tools/server_smoke.py` installs the loader's real dedicated server (Fabric meta launcher, Quilt
installer, NeoForge and Forge installers), drops in the built jar, makes a Continent world, and checks
`/locate biome minecraft:mushroom_fields` lands near the layout's (-1250, -50), the saved generator names
`the_continent:continent`, there are no ERROR or FATAL lines, and `/stop` exits cleanly. `tools/smoke_band.py`
reads every pin from `gradle.properties`. CI runs all four loaders in every band leg.
**Why:** It tests what players download, on the loader they run, which is exactly where the loader-specific risks
live (registration timing, metadata, Forge's pack.mcmeta). The worldgen logic is identical common code, already
covered on Fabric. The fingerprint matters: Forge's missing pack.mcmeta produced a clean, error-free, vanilla
world.
**Rejected:** porting the gametests to each loader (a duplicated suite for code that doesn't differ); a smoke mod
inside the dev run (it tests the dev environment, not the shipped jar).
**Affects:** `tools/server_smoke.py`, `tools/smoke_band.py`, `.github/workflows/build.yml`, `gradle.properties`

## 2026-10-20 — The band's biome set is a compile-time list, not read from the registry
**Context:** TODO C.1 planned for `DefaultLayout` to build from the biome ids the running game's registry has.
But only the biome source sees the registry, through its codec. The terrain (`LayoutDensityFunction.TERRAIN`),
`AnchoredPlacement`, `/continent` and the map export all hold a static `DefaultLayout`. A registry-filtered biome
source would shape terrain for regions it never places.
**Choice (owner, 2026-10-20):** `VanillaBiomes.inThisBand()` lists the newer biomes this band's game has, in
Stonecutter `//? if` blocks. `DefaultLayout.create(Set)` / `terrain(Set)` are pure functions of that set, and the
no-argument forms pass the band's set, so every reader shares one layout. `BandBiomesTest` checks the list against
vanilla's `Biomes` class in both directions, and `BandLayoutsTest` validates all three bands' layouts in every band.
**Why:** It is one source of truth with no new plumbing. A missing biome still fails loudly and names the id,
instead of quietly shrinking the map. The layout package stays free of Minecraft types.
**Rejected:** registry-driven (the per-world layout would touch three codecs and the command, and a datapack
that removed a vanilla biome would silently change the map); referencing `Biomes.SULFUR_CAVES` in the list itself
(that would pull Minecraft into `layout/`, and the test catches a wrong id and a forgotten one too).
**Affects:** `common/src/main/java/com/wulfic/continent/layout/{VanillaBiomes,DefaultLayout}.java`

## 2026-10-20 — BandBiomesTest pins 26.1's whole biome list
**Context:** The first `BandBiomesTest` compared `inThisBand()` with the registry only for ids already in
`VanillaBiomes.NEWER`. A third new biome in some band, one nobody had added to `NEWER`, would pass unnoticed, and
the band would ship without a block source.
**Choice:** The test holds the 65 biomes of 26.1 (read from the 26.1.2 jar's `data/minecraft/worldgen/biome`).
In every band, registry minus that list must equal `inThisBand()`, and every listed biome must still be registered.
On 26.1.2 that pins the registry exactly. Checked against the jars: 26.2 adds only `sulfur_caves`, 26.3 only
`dappled_forest`.
**Why:** It's the "both directions" the plan asked for. A missing entry is caught by the band's own unit tests,
not by someone reading a changelog.
**Rejected:** reading vanilla's `Biomes` class by reflection (the same check, but brittle across renames).
**Affects:** `common/src/test/java/com/wulfic/continent/world/BandBiomesTest.java`

## 2026-10-21 — The release guard reads the band table from gradle.properties and refuses any extra file
**Context:** D.1: one tag must publish every band × loader jar. The guard knew one jar name.
**Choice:** The bands are the `band_label.<band>` keys, and each band's labels and ranges come from the same keys
the builds read. dist must hold exactly one jar and one sources jar per band and loader, and nothing else
(a stale jar, a dotfile, a directory are refused). Each jar's own metadata must carry the id, version and its
band's range: `fabric.mod.json` via jq, the two `mods.toml`s via awk (they're generated, one key per line; a
second `[[mods]]` or a missing minecraft dependency is refused). The publish step can then upload
`the_continent-*.jar` safely.
**Why:** One source of truth for the band table, and the jar's own declared range is what a loader enforces, so
a mislabelled jar (proved with a 26.2 jar renamed as 26.3) can't ship.
**Rejected:** a hard-coded list of 9 names in the workflow (drifts from `gradle.properties`); a TOML parser
dependency on the runner (awk over generated files is enough, and fails closed).
**Affects:** `.github/scripts/release-guard.sh`, `.github/scripts/release-guard-test.sh`, `.github/workflows/build.yml`

## 2026-10-21 — A fresh public repo from one clean commit; credential files untracked
**Context:** Stage E. `.env`, `.mcp.json` and `.claude/mcp.vscode-reference.json` were tracked with live
credentials (a GitHub PAT, a context7 key, system passwords) in every commit since `1b9209a`, by the owner's
earlier call (gotchas 2026-08-12). The repo was going public.
**Choice (owner, 2026-10-21):** the private repo is renamed `the-continent-archive` and archived, with every commit,
tag, release and Actions log. A new `Wulfic/the-continent` starts from **one commit** of today's tree, authored by
the noreply identity, and v1.0.0 is rebuilt there by CI. The three files are untracked and ignored. The
`.example` templates read tokens from environment variables. It goes public only after scanning the pushed
repo and hardening it while it's still private.
**Why:** a public repo whose history never contained the secrets can't leak them, whatever a scanner misses. A
new repo also leaves the old Actions logs and force-push-reachable SHAs behind. Rotation stops blocking the release,
though the owner still rotates, since the archive holds the values.
**Rejected:** `git filter-repo` over the full history (one missed copy in an old commit leaks, and GitHub keeps
old SHAs reachable); making the old repo public (its logs and history go with it); carrying 0.1.0 / 0.2.0 and
`mc/1.21.11` across (they live on in the archive).
**Note:** SHAs cited in this file and in gotchas.md before this entry exist only in the archive and in the
local bundle, not in the public repo.
**Affects:** `.gitignore`, `.env.example`, `.mcp.example.json`, `.claude/mcp.vscode-reference.example.json`, `AGENTS.md`
