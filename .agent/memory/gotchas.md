# Gotchas

> **Append-only.** Traps, dead ends, and environment quirks that cost real time.
>
> The test for whether something belongs here: **would a competent developer hit this
> again in six months?** If yes, write it down. If it's obvious from reading the code,
> it does not belong here.
>
> Log a failed approach as carefully as a successful one. "We tried X, it doesn't work
> because Y" is what stops the next session from burning an hour on X.

## Entry template

```markdown
## YYYY-MM-DD — Short title
**Symptom:** what you actually saw (error text, wrong output, hang)
**Cause:** the real root cause
**Fix:** what worked
**Dead ends:** what looked right but wasn't, and why
**Affects:** src/path/to/file.ts
```

---

## 2026-08-12 — `.gitignore` does not cover `.env`
**Symptom:** `.env` is tracked in git despite a large "Secrets / config" section in
`.gitignore`.
**Cause:** That section only lists `credentials*`, `secrets*`, `*.pem`, `*.key`, `*.crt`.
There is no `.env` pattern, and a file already tracked stays tracked even after a matching
pattern is added.
**Fix:** Not applied — the repo owner opted to leave credential handling as-is for now.
**Update 2026-10-21:** fixed. All three files are untracked and ignored, with `.example`
templates (decisions.md 2026-10-21, "A fresh public repo").
Anything spawned from this template inherits both the tracked `.env` and the pattern gap.
**Dead ends:** Adding `.env` to `.gitignore` alone does nothing for an already-tracked
file; it needs `git rm --cached` as well.
**Affects:** `.gitignore`, `.env`, `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — The two MCP config files disagreed on hosts (RESOLVED)
**Symptom:** `.mcp.json` and `.claude/mcp.vscode-reference.json` pointed the same servers at different
LAN hosts: one IP per server in the first, one host with a port per server in the second.
**Cause:** The self-hosted MCP stack moved from a single docker-compose host (one port per
service) to one container per server, **one IP per server**. `.mcp.json` was updated; the reference file was not.
**Fix:** `.mcp.json`'s hosts are current. The reference file was realigned and the stale
host:port addresses removed. (Addresses redacted 2026-10-21 for the public repo.)
**Dead ends:** We initially guessed the reference file might be the newer one because
`AGENTS.md` still described a docker-compose stack — the prose was simply stale too. The
`// description` comment in `.mcp.json` is the reliable source here, not the prose.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — mcp-compressor 0.31.4 changed its CLI flags
**Symptom:** Server configs written with `--compression-level` and `--header` fail on
current mcp-compressor. Backends without an `Authorization` header fail differently —
mcp-compressor auto-attempts OAuth discovery, which these backends don't support.
**Cause:** 0.31.4 renamed `--compression-level` → `--compression`, `--header` → `-H`, and
now requires a `--` separator before the backend URL and its args.
**Fix:** Use `--compression`, `-H`, and `--`. For backends with no real auth, pass a
placeholder `-H "Authorization=Bearer unused"` to suppress OAuth discovery.
**Dead ends:** Omitting the Authorization header entirely looks correct for an unauthed
backend, but triggers the OAuth path and fails with an unrelated-looking error.
**Affects:** `.mcp.json`, `.claude/mcp.vscode-reference.json`

## 2026-08-12 — A newly created `.claude/settings.json` does not load in the session that created it
**Symptom:** Added `permissions.deny` / `permissions.ask` rules, then ran `dropdb --help`
(matches the `ask` rule `Bash(dropdb*)`). It executed straight through to
`bash: dropdb: command not found` — no prompt, no block.
**Cause:** The settings watcher only watches directories that already contained a settings
file when the session started. `.claude/settings.json` did not exist at session start, so
the new file stays invisible until the session restarts (or `/hooks` is opened, which
reloads config). Nothing wrong with the file — it parses and matches the published schema.
**Fix:** Restart the session, or open `/hooks` once. Verify afterward with a harmless
matching command such as `dropdb --help`.
**Dead ends:** Re-checking the JSON and the rule syntax — both were correct the whole time.
The failure looks exactly like a bad matcher pattern, which is the trap: **a permission
rule that silently doesn't load is indistinguishable from one that doesn't match.** Same
class of invisible-failure problem that got `mem0` dropped. Probe with a real command
rather than assuming the rules are live.
**Affects:** `.claude/settings.json`, `.claude/skills/guard-destructive/SKILL.md`

## 2026-10-16 — `.gitignore` `*.jar` silently drops the Gradle wrapper jar
**Symptom:** None at first. `gradle/wrapper/gradle-wrapper.jar` would be left out of the
commit, so `./gradlew` fails on a fresh clone and in CI.
**Cause:** The template's Java section ignores `*.jar` globally.
**Fix:** `!gradle/wrapper/gradle-wrapper.jar` placed right after `*.jar`. Check with
`git check-ignore -v`: a match on the `!` line means the jar is tracked.
**Dead ends:** none.
**Affects:** `.gitignore`

## 2026-10-16 — Piping `stop` into `./gradlew runServer` doesn't stop the server
**Symptom:** `echo stop | ./gradlew runServer` → the server logs "An unexpected error occurred
trying to execute that command" and keeps running until killed.
**Cause:** Not root-caused. The stdin forwarded through the Gradle daemon to the server's
console is not delivered as a clean command line (likely a CR or encoding issue on Windows).
**Fix:** For a "reaches Done" smoke check, wrap it in `timeout 600` and afterwards confirm that
no `KnotServer` java process is left (only the `GradleDaemon`). Stage 3+ should use Fabric
gametests (`runGametest`-style, self-exiting) instead of a manually driven server.
**Dead ends:** none tried beyond the pipe.
**Affects:** Stage 1.4 / any scripted `runServer`

## 2026-10-17 — Loom 1.18.4 needs a Java 25 JVM to *run* Gradle, though the mod targets 21
**Symptom:** `./gradlew build` on a JDK 21 → "Could not resolve net.fabricmc:fabric-loom:1.18.4
… Dependency requires at least JVM runtime version 25. This build uses a Java 21 JVM."
**Cause:** The Loom plugin jar is built for Java 25. That governs the JVM running Gradle,
not the mod's bytecode: `options.release = 21` still produces Java 21 classes.
**Fix:** Run Gradle on a JDK 25 (`JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64`). On Ubuntu:
`apt-get install openjdk-25-jdk-headless`. Keep `release = 21` and `"java": ">=21"`: the game
runs on Java 21.
**Dead ends:** none. The error text names the fix.
**Affects:** `gradle.properties` (`loom_version`), any CI / cloud session

## 2026-10-17 — `gradlew` is committed without the exec bit; Maven Central rate-limits parallel resolution
**Symptom:** On Linux, `./gradlew` → "Permission denied". A cold build (empty Gradle cache)
then failed with "Received status code 429 from server: Too Many Requests" from
`repo.maven.apache.org`.
**Cause:** The scaffold was committed from Windows, so the file mode is `100644`. The 429 comes
from `org.gradle.parallel=true`, which resolves many POMs at once from a shared egress IP.
**Fix:** `bash gradlew …` works around the mode, and `git update-index --chmod=+x gradlew` fixes
it properly. For a cold cache, `--max-workers=1` resolved cleanly on the first retry.
**Dead ends:** none.
**Affects:** `gradlew`, cold-cache builds in CI / cloud sessions

## 2026-10-17 — `fabric-loader-junit` puts every Fabric API `fabric.mod.json` on the test classpath
**Symptom:** After adding `fabric-loader-junit`, `ModMetadataTest` read `id = fabric-loot-api-v3`,
and the entrypoint lookup threw an NPE.
**Cause:** `getResourceAsStream("/fabric.mod.json")` returns the first copy on the classpath.
The remapped Fabric API module jars now come before `build/resources/main`.
**Fix:** Enumerate `getResources("fabric.mod.json")` and require exactly one with our id.
Mutation-checked: a broken id still fails the suite.
**Dead ends:** none.
**Affects:** `src/test/java/com/wulfic/continent/ModMetadataTest.java`

## 2026-10-17 — No world seed reaches a BiomeSource; spawn search ignores the biome source
**Symptom:** Found while planning Stage 3, not hit at runtime.
**Cause:** (1) `BiomeSource.getNoiseBiome(x, y, z, Climate.Sampler)` is the whole input, and
`RandomState` only wires seeded noise into density functions. (2) `Climate.Sampler.findSpawnPosition()`
searches *climate space* using the noise settings' `spawn_target`, not biomes.
**Fix:** (1) See decisions.md "Layout seed is derived from the seeded temperature field".
(2) Stage 4/5: spawn follows terrain/climate, so Stage 5's continentalness must make the origin
land, and the `spawn_target` in our copied noise settings must accept it. Check spawn in 4.4.
**Dead ends:** none.
**Affects:** `world/ContinentBiomeSource.java`, Stage 4 preset, Stage 5 noise settings

## 2026-10-17 — On Linux, console commands reach `runServer` through a FIFO held open on fd 3
**Symptom:** None. This is the working recipe that the Windows pipe gotcha (2026-10-16) lacked.
**Cause:** A plain `echo … | ./gradlew runServer` closes stdin straight after the first line.
A FIFO opened read-write (`exec 3<>fifo`) stays open, so the server reads each line as one
command, and `stop` shuts it down cleanly (exit 0).
**Fix:** `mkfifo f; exec 3<>f; ./gradlew runServer <&3 >log 2>&1 &`, wait for `Done (` in
the log, then `echo "locate biome minecraft:plains" >&3`, …, `echo stop >&3`. Wrap the gradle
call in `timeout`. The world type comes from `run/server.properties`
(`level-type=the_continent\:continent`). `run/` is gitignored and disposable. Console commands
run from the world spawn, so `/locate biome` distances are measured from spawn.
**Dead ends:** none on Linux. Untested on Windows.
**Affects:** headless verification (TODO 4.6), Stage 6/7 smoke checks

## 2026-10-17 — A custom DensityFunction needs value equality, or noise chunks get ~3× slower
**Symptom:** `getBaseHeight` on the continent settings took ~10 ms per column, against ~3.5 ms for
vanilla's overworld on the same machine. The terrain was correct.
**Cause:** `RandomState` (`NoiseWiringHelper`) and `NoiseChunk.wrap` dedupe the graph with
`Map.computeIfAbsent(function, …)`, which relies on `equals`. Vanilla functions are records. Our
`LayoutDensityFunction` was a class with identity equality, so each of the ~100 spline references to
the ridges/erosion channels got its own `flat_cache` instance, and each one filled 25 columns per chunk.
**Fix:** `equals`/`hashCode` over (channel, detail, seed_source). The lazily derived layout seed is a
pure function of seed_source, so leaving it out is exact. After the fix: 3.8 ms ocean (parity), ~4.5 ms
land. A regression test asserts the equality.
**Dead ends:** none. Timing vanilla against ours on identical columns, in one gametest, found it in a
single run.
**Affects:** `world/LayoutDensityFunction.java`

## 2026-10-17 — Never evaluate a density function's inputs in its constructor
**Symptom:** The server failed at registry load: "Trying to access unbound value
'minecraft:shift_x' … Unbound values in registry … the_continent:overworld/continents".
**Cause:** The codec constructs the function while the density-function registry is still loading.
`LayoutSeed.derive(seed_source)` in the constructor evaluated `shift_x` before its holder was bound.
JUnit didn't catch it, because `VanillaRegistries.createLookup()` is fully bound before the tests decode.
**Fix:** Derive lazily on first `compute` (a volatile, immutable `Long`). Only a gametest or a real
server load exercises datapack registry loading.
**Dead ends:** none.
**Affects:** `world/LayoutDensityFunction.java`

## 2026-10-17 — Touching a worldgen class before Bootstrap poisons every later test in the JVM
**Symptom:** Adding `AnchoredPlacementTest` made *other* classes fail too (`ContinentBiomeSourceTest`,
`LayoutDensityFunctionTest`) with `ExceptionInInitializerError: Not bootstrapped (called from registry
minecraft:game_event)`.
**Cause:** A static field initialised `AnchoredPlacement.CODEC` before anything had called
`Bootstrap.bootStrap()`. Loading `StructurePlacement` initialises `BuiltInRegistries`, which failed. A
class whose static init failed stays broken for the life of the JVM, and Gradle runs all test classes in one.
**Fix:** Bootstrap first. The test's first static field is `TestWorlds.OPS`, whose class bootstraps in
its static block. Static fields initialise in textual order.
**Dead ends:** Registering the placement type into `BuiltInRegistries` from the test: they're frozen after
the bootstrap. Decode with `AnchoredPlacement.CODEC` directly instead; the gametest covers registry dispatch.
**Affects:** `src/test/java/com/wulfic/continent/world/*`

## 2026-10-17 — Under the continent, every biome-gated *underground* feature is gone
**Symptom:** The block walk found no infested stone anywhere, though it's in the mountain biomes' feature lists.
**Cause:** Features check the biome at their own position (`minecraft:biome` placement filter). Our biome
source makes everything at climate depth ≥ 0.2 a cave biome (Lush/Dripstone), and Deep Dark at y ≤ 0 in the
north. `ore_infested` only places below y 63, which is always cave biome under our land. The same goes for
badlands' extra gold (`ore_gold_extra`). Emerald ore survives because its range reaches the surface layer.
**Fix:** None needed. Infested blocks are unobtainable in survival (Silk Touch gives plain stone), and gold
is everywhere. **Check this before adding any biome whose block comes from an underground feature.**
**Dead ends:** none.
**Affects:** `world/ContinentBiomeSource.java` (`CAVE_DEPTH`), future biome additions

## 2026-10-17 — Headless block census: forceload, then parse region palettes; one sample can miss
**Symptom:** None. This is the recipe for checking which blocks actually generated, with no client.
**Fix:** Dev server (FIFO recipe), `forceload add x1 z1 x2 z2` per area (≤ 256 chunks each, in batches of ~6
areas, ~80 s per batch on 4 cores), then `forceload remove all`, `save-all flush`, `stop`. Then read
`run/<world>/region/r.X.Z.mca` offline: a ~70-line Python NBT reader, listing each chunk's
`sections[].block_states.palette[].Name`. Vanilla packs the palette on save, so it lists only blocks present.
**Dead ends:** One 6×6-chunk sample per biome gave false misses: coral reefs are noise-gated in ~400-block
blobs, bee nests are a few % of trees, and blue orchid/bee nests in the same area differed between two
runs of the same seed (decoration near chunk borders depends on multithreaded order). Search the whole
saved world for a block before calling it missing.
**Affects:** Stage 8.2 verification; any future block-availability check

## 2026-10-17 — Cloud sessions can push branches but not tags (HTTP 403)
**Symptom:** `git push origin main` worked, but `git push origin refs/tags/v0.1.0` failed with "RPC failed;
HTTP 403 … send-pack: unexpected disconnect" and then "Everything up-to-date". The proxy status showed no
relay failures, so it wasn't network trouble.
**Cause:** The session's GitHub git access lets it push branch refs only. The GitHub MCP tools have no
tag-creation call either.
**Fix:** Release through the workflow instead: `actions_run_trigger` on `build.yml` with
`inputs.release = X.Y.Z`. The release job tags main's head, runs the guard, and only then pushes the tag
with its own token. The owner can still push tags from their own machine.
**Dead ends:** Retrying the push (a 403 is not transient). `gh` in the container: the session rules route
GitHub through the MCP tools, not the CLI.
**Affects:** releases from any cloud session, `.github/workflows/build.yml`

## 2026-10-18 — Cloud sessions can't delete other branches either (HTTP 403)
**Symptom:** `git push origin --delete claude/epic-planck-3evevs` → "RPC failed; HTTP 403 … the remote end hung
up", then "Everything up-to-date". `--dry-run` happily printed "[deleted]" first. Deleting the session's own
branch worked.
**Cause:** The session's git proxy only lets it write its own branch and `main`. Same wall as the tags entry above.
**Fix:** The owner deletes other branches in GitHub's branch list. Check the result with `git ls-remote --heads
origin`, never with the push output: a dry run passes and the real push prints "Everything up-to-date" on failure.
**Dead ends:** The GitHub MCP tools have no branch-delete call.
**Affects:** branch cleanup from any cloud session

## 2026-10-18 — Client gametests: what bit while writing `ContinentClientGameTest`
**Symptom:** (1) Twelve eyes of ender didn't open the end portal. (2) Then the player stood in the open portal
and never reached the End within 600 ticks.
**Cause:** (1) `EnderEyeItem.useOn` acts on `UseOnContext.getLevel()`, the *player's* level, and the player was
still in the Nether, so every use hit a Nether block and passed. (2) A dimension change (here the teleport back
from the Nether) leaves a portal cooldown, and the container's lag stretched it further.
**Fix:** Teleport the player to the portal room in the overworld first, then use the eyes. Clear the cooldown
with `player.setPortalCooldown(0)` before stepping in. Also: `TestWorldBuilder` defaults to "consistent settings",
which use a flat world and switch `advance_time` off. Any time or worldgen test needs
`setUseConsistentSettings(false)`.
**Dead ends:** none.
**Affects:** `src/gametest/.../ContinentClientGameTest.java`

## 2026-10-18 — A value-noise warp almost never reaches its amplitude
**Symptom:** Border roughness of "24 blocks" looked like 5-block wiggles. The raggedness test failed: borders
bent 1.4-2.0 blocks per 8, against 0.4-0.7 with no roughness at all.
**Cause:** Value noise averages lattice values. Several octaves average further, so typical |noise| is ~0.3,
and the mean push was 8.5 blocks of 24.
**Fix:** Boost the noise (×2.5) and clamp the push vector to the disk of the amplitude. The bound stays hard
and the mean push becomes ~18. Look at a rendered crop before trusting an amplitude; the scratch renderer
compiled against `build/classes/java/main` + slf4j works without a game.
**Dead ends:** Raising the amplitude instead (breaks the village bound).
**Affects:** `layout/ContinentLayout.roughScale`, any future warp

## 2026-10-19 — Client gametest: a slow client starves the integrated server's chunk loading
**Symptom:** On the CI runner (2 CPUs, llvmpipe) `runClientGameTest` died with "Timeout loading world": spawn
prep logged 16% once, then "Can't keep up! … ~100 ticks behind" every 20 s. Locally it loaded in seconds.
**Cause:** Fabric's client gametest runs exactly one server tick per client frame (a `Phaser`). llvmpipe drew
~14 frames/s, so the server could never keep its 50 ms tick and stayed behind real time.
`MinecraftServer.pollTaskInternal` drains the chunk system's main-thread queue (`ServerChunkCache.pollTask`)
only when `isSprinting() || shouldRunAllTasks() || haveTime()`. While behind, `haveTime()` is false, so chunks
never reach FULL. The thread dumps showed it: server thread 0.05 s CPU in 31 s, parked in the phaser, and the
worldgen pool idle. The 1200-tick timeout (`SharedConstants.TICKS_PER_MINUTE`) counts ticks, not seconds.
**Fix:** `requestGameToSprint(Integer.MAX_VALUE)` on `SERVER_STARTED`. To reproduce on a fast machine, hold
each client tick to ≥ 60 ms (`END_CLIENT_TICK` sleep). The test now always does that, so reverting the sprint
fails locally too. A frame-rate cap can't stand in: `FramerateLimitTracker` pins menus, the loading screen
included, at 60 fps whatever `framerateLimit` says.
**Dead ends:** (previous session) a 1.75 GB heap cap and pinning to one CPU. Neither slows the *renderer*, which
is what paces the server.
**Affects:** `src/gametest/.../ContinentClientGameTest.java`

## 2026-10-19 — Windows refuses `Files.createSymbolicLink` without Developer Mode
**Symptom:** `MapFilesTest.refusesAMapFolderThatLinksOutsideTheWorld` failed on the owner's Windows machine
("A required privilege is not held by the client"). Earlier sessions ran on Linux and never saw it.
**Fix:** Fall back to a directory junction (`cmd /c mklink /J`), which needs no privilege. `toRealPath`
follows it, and with the guard in `MapFiles` disabled the test goes red through the junction too.
**Dead ends:** none. (Skipping the test on Windows was rejected: it would leave the guard untested there.)
**Affects:** `src/test/.../map/MapFilesTest.java`

## 2026-10-19 — "Sky stuck at noon" was a stale dimension_type override in the owner's modpack
**Symptom:** In the owner's Prism instance (`instances/1.21.11`), the sun sat straight overhead with no moon,
no clouds and no fog. That's the earlier "time stuck at noon" report. Server time was fine: level.dat had
`advance_time` on and DayTime 2154. The client gametest passes.
**Cause:** `extended-build-limit-1.18-1.21.jar` (mod id `mr_extended_buildlimit`, pack_format ≤ 61) ships
`data/minecraft/dimension_type/{overworld,the_nether,the_end}.json` in the pre-1.21.11 format. It replaces
vanilla's file outright, and in 1.21.11 that drops `attributes` (cloud_height/color, fog_color, sky_color) and
`timelines: #minecraft:in_overworld` (the day and moon timelines drive sun/moon/star angles). The client falls
back to attribute defaults: sun angle 0 (overhead), no moon, no clouds. Old fields (`effects`, `natural`,
`bed_works`…) are silently ignored. Every world in that instance is affected, vanilla presets too.
**How found:** scan every mod jar for `data/minecraft/(dimension_type|timeline)/`.
**Not ours:** the Continent preset references `minecraft:overworld` unchanged and overrides nothing in `data/minecraft` except the preset tag.
**Affects:** nothing in this repo.

## 2026-10-20 — `fabric-loader-junit` alone fails with "ASM not detected on the classpath"
**Context:** A.2 split the code into `common` (Minecraft only) and `fabric`. Common's tests kept only
`testImplementation fabric-loader-junit`, and every test died before running: `FabricLoaderLauncherSessionListener
could not be instantiated … ASM not detected on the classpath`.
**Cause:** Loom adds Fabric Loader's own libraries (ASM, Mixin, …) from the loader's installer JSON, and only for a
loader in a `mod*` configuration. The loader's POM doesn't list them. Loom 1.18 has no `modTestImplementation`
("Could not find method"), so a test-only loader isn't possible.
**Fix:** `modImplementation fabric-loader` in `common/build.gradle`. The compiler can no longer keep loader APIs out
of common, so `LoaderFreeTest` scans common's sources for `net.fabricmc`/`net.neoforged`/`net.minecraftforge`/
`org.quiltmc` instead (mutation-checked).
**Affects:** `common/build.gradle`, `common/src/test/java/com/wulfic/continent/LoaderFreeTest.java`

## 2026-10-20 — A resource test that reads a path vanilla also ships depends on classpath order
**Context:** After the split, `WorldPresetResourcesTest.normalTagAppendsThePreset` failed in `common`: it read
`data/minecraft/tags/worldgen/world_preset/normal.json` and got **vanilla's** copy, which has no `"replace"`.
**Cause:** `getResourceAsStream` returns the first match. In the old single project our resources came first; in
`common` the Minecraft jar does. The test only ever passed because of ordering.
**Fix:** read every match with `getResources` and keep the one with a `file:` URL (this project's resource
directory). Mutation check: `"replace": true` in our tag turns it red. Any future test that reads a path under
`data/minecraft/` must do the same.
**Affects:** `common/src/test/java/com/wulfic/continent/WorldPresetResourcesTest.java`

## 2026-10-20 — 26.1 moved world generation out of `level.dat`
**Context:** A.3 boot test on a 26.1 dedicated server. Grepping `level.dat` for `the_continent:continent`, to
prove the world really used our generator, found nothing.
**Cause:** 26.1 saves the generator in `<world>/data/minecraft/world_gen_settings.dat` (gzip NBT), and dimensions
live under `<world>/dimensions/`. `level.dat` no longer has `WorldGenSettings`.
**How to check a world's generator now:** search `data/minecraft/world_gen_settings.dat` (gunzip it first) for
`the_continent:continent`. `LevelResource.ROOT` is unchanged, so the map export still writes to
`<world>/continent-maps` (a server gametest asserts it).
**Affects:** boot smokes (TODO B.3/B.4), any tool that reads a world's generator

## 2026-10-20 — 26.1: closing a client gametest world deadlocks while the server sprints
**Symptom:** `runClientGameTest` on 26.1.2 passed every check, logged "Stopping singleplayer server as player
logged out", then hung for 50 minutes until killed. 1.21.11 never did this.
**Cause (thread dump):** `TestSingleplayerContext.close()` defers a disconnect to the render thread, which calls
`IntegratedServer.halt` → `executeBlocking` and waits for the server. The server thread was parked in Fabric's
gametest `Phaser` (`MinecraftServer.postRunTasks` hook), waiting for the test thread, and the test thread was in
`close()` → `waitFor`, waiting for a render tick that never came. The server was still sprinting
(`requestGameToSprint(Integer.MAX_VALUE)`, gotchas 2026-10-19).
**Fix:** stop the sprint as the last step inside the `try` (`runOnServer(s -> s.tickRateManager().stopSprinting())`).
The next run shut down cleanly in seconds.
**How to see it next time:** a client gametest whose log ends at "Stopping singleplayer server" is this hang.
Take a `jstack` of the `-Dfabric.client.gametest` java process before killing it.
**Affects:** `fabric/src/gametest/java/com/wulfic/continent/gametest/ContinentClientGameTest.java`

## 2026-10-20 — Stonecutter nodes with the same name in two branches resolve to each other
**Symptom:** After adding `common` and `fabric` branches with nodes `26.1.2`/`26.2`: "Could not resolve project
':common:26.1.2' … A dependency was declared on configuration 'commonJava' of 'project :fabric:26.1.2' but no
variant with that configuration name exists".
**Cause:** A project's module identity is `group:name:version`. Both nodes are named after the band, so
`:common:26.1.2` and `:fabric:26.1.2` were both `com.wulfic.continent:26.1.2:0.2.0`, and Gradle substituted the
consumer for the dependency. The error names the wrong project, which hides it.
**Fix:** a per-branch group (`com.wulfic.continent.common` / `.fabric`) in each branch's `build.gradle`. Any new
branch (`neoforge`, `forge`) needs its own.
**Also learned:** Stonecutter makes the root's build script its generated controller, `stonecutter.gradle`, so a
root `build.gradle` silently stops running (plugins declared there vanish). Each node also gets its own
`versions/<band>/src/main/{java,resources}` overlay, which is where per-band data goes. Non-active nodes compile
`versions/<band>/build/generated/stonecutter/…`, built by `stonecutterGenerate`.
**Affects:** `settings.gradle`, `stonecutter.gradle`, `common/build.gradle`, `fabric/build.gradle`

## 2026-10-20 — Porting a band: edit with that band active, and read its decompiled sources
**Context:** The 26.3 port touched ~15 files in `//? if` blocks.
**What worked:** `./gradlew "Set active project to 26.3"` makes 26.3's code plain Java you can compile and fix,
with the older code commented. `./gradlew "Reset active project"` (back to 26.1.2) must run before every commit,
because the sources are committed with 26.1.2 active. `./gradlew :common:<band>:genSources` decompiles that
band's Minecraft (Vineflower, about 8 minutes) to
`~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/<band>/*-sources.jar`. Reading
it beat `javap` for every API question.
**Traps:** Inside an inactive block, Stonecutter writes nested block comments as `/^ … ^/`. 26.3 dropped the old
noise format (`firstOctave`/`amplitudes`), and its codec only reads `base_octave`/`octave_count`/
`base_amplitude`/`amplitude_modifiers`. Vanilla's `createParity` gives the equivalent.
**Affects:** any future band

## 2026-10-20 — Forge skips a mod's data without pack.mcmeta, and the world silently comes up vanilla
**Symptom:** The Forge 26.3 server smoke reached "Done" with no ERROR line and our mod "initialized", but the
world was vanilla: Mushroom Fields 5250 blocks away and no `the_continent:continent` in the saved generator.
**Cause:** `WARN … Missing metadata in pack mod:the_continent`, then `Missing data pack mod:the_continent`, then
`Failed to parse level-type the_continent:continent, defaulting to minecraft:normal`. Forge needs a
`pack.mcmeta` in the jar to load its data. Fabric and NeoForge don't. Every step is a WARN, so a log scan for
errors passes.
**Fix:** `forge/src/main/resources/pack.mcmeta`, with `min_format = max_format =` the band's data-pack major
(`forge_pack_format.<band>`, read from `version.json` in the band's Minecraft jar: 101, 107, 121), as Forge's MDK
writes it.
**Lesson:** a boot test has to fingerprint the world, not just count errors. `tools/server_smoke.py` locates
Mushroom Fields and checks they're near (-1250, -50).
**Also:** Forge's Maven answers Python's default user agent with HTTP 403. A crashed NeoForge server can stay
alive without exiting, so the harness also gives up after 180 s of silence.
**Affects:** `forge/`, `tools/server_smoke.py`

## 2026-10-20 — No `jq` on the owner's Windows machine: a jq-based monitor is silently blind
**Symptom:** A Monitor polling `gh run view --json ... | jq` reported nothing for 20+ minutes while CI jobs finished.
**Cause:** Git Bash here has no `jq`. The pipeline's `jq` failed on stderr, which a Monitor doesn't surface, and
stdout stayed empty, so it looked exactly like "still running".
**Fix:** Use `gh`'s built-in `--jq` (`gh run view ID --json jobs --jq '...'`), and make every poll loop print
something when its own command fails.

## 2026-10-21 — A test that reads a repo file is skipped when only that file changes
**Symptom:** none yet, caught in review: `ModMetadataTest` reads `LICENSE` and the new `ReadmeBandsTest` reads
`README.md` and `gradle.properties` through the `the_continent.repoRoot` system property.
**Cause:** A system property holding a path isn't a task input, so Gradle saw `:fabric:<band>:test` as up to date
after a README-only edit and wouldn't rerun the test that guards the README.
**Fix:** `inputs.files(...)` for those three files on the Fabric `test` task. Proved by the README mutations, which
went red without any code change.
**Affects:** `fabric/build.gradle`

## 2026-10-21 — Run the release scripts' tests in Docker, not Git Bash
**Symptom:** `release-guard-test.sh` can't run on the owner's machine: no `jq` and no `shellcheck` in Git Bash,
and MSYS `ln -s` copies instead of linking, so the symlink cases would lie.
**Fix:** Docker Desktop is installed (start it first). `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd -W):/src:ro"
ubuntu:24.04 bash -c 'apt-get update -qq && apt-get install -y -qq git zip unzip jq shellcheck && cp -r /src/.github
/w && cd /w && shellcheck .github/scripts/*.sh && bash .github/scripts/release-guard-test.sh'`. actionlint:
`docker run --rm -v "$(pwd -W):/repo:ro" -w /repo rhysd/actionlint:1.7.7 -no-color`.
**Affects:** `.github/scripts/`

## 2026-10-21 — ForgeGradle's Mavenizer can time out downloading on CI: that's a flake, re-run the job
**Symptom:** `build (26.1.2)` failed in 2.5 min while *configuring* `:forge:26.1.2`: `MavenizerValueSource` →
`Failed to execute task decompile` → `java.net.http.HttpTimeoutException: request timed out` (run 39549877472).
**Cause:** ForgeGradle 7 builds its Minecraft artifacts at configuration time with a Java tool that downloads the
decompiler. A slow Maven host times it out, and the whole leg dies before compiling anything.
**Fix:** Nothing in our code. Read the failing job log before suspecting a change (`gh api
repos/Wulfic/the-continent/actions/jobs/<id>/logs` works while the run is still going), then `gh run rerun <run>
--failed` once the run has completed.
**Affects:** `forge/`, `.github/workflows/build.yml`

## 2026-10-21 — A gametest's maxTicks is not a time limit: the test server runs far ahead of 20 ticks/s
**Symptom:** On the v1.0.0 tag run, 26.3's `mapCommandWritesOneNewPngForAdminsOnly` failed "expected new map
files to be 1: was 0 on tick 402". The worker wrote the PNG in the same second. The same commit had passed on main.
**Cause:** The command ran at tick 0 (06:11:53) and tick 402 came at 06:11:54: the server ran ~400 ticks in a
second. The render is wall-clock work on `Worker-Main`, queued behind the other tests' chunk generation, so
`maxTicks = 400` gave it about a second on a busy runner.
**Fix:** A wall-clock deadline (60 s) checked inside `succeedWhen`, failing with a plain exception, and a large
`maxTicks` as a backstop. In 26.x `GameTestSequence` retries only `GameTestAssertException` (which `helper.fail`
also throws); `GameTestInfo` fails the test on any other `Exception` (read with `javap` on the 26.3 jar). Mutation:
a command that writes nothing with a 5 s budget fails in 5 s with the message.
**Affects:** `fabric/src/gametest/.../ContinentGameTest.java`; any gametest waiting on off-thread work

## 2026-10-21 — upload-artifact with several paths keeps their directories, so the release saw no jars
**Symptom:** The v1.0.0 tag run built every band, then the release guard refused: "missing build output
'dist/the_continent-1.0.0+fabric-mc26.1.jar'". Nothing was published. The artifacts held
`fabric/versions/26.1.2/build/libs/...`, `neoforge/...`, `forge/...`.
**Cause:** `actions/upload-artifact` stores files relative to the least common ancestor of all its paths. With
one path (Fabric only, before Stage B) that was `libs/`, so the files sat at the top. With three loaders it's the
repo root. My local dry run of the guard copied the jars into a flat `dist/` itself, so it couldn't see this.
**Fix:** The build job stages each band's jars into one flat `$RUNNER_TEMP/jars-<band>` (refusing a name collision)
and uploads that. The check that would have caught it: download a real run's artifacts (`gh run download <run>
-p 'the_continent-jars-*' -D <dir>` merges them like the release job) and run the guard on that folder.
**Also:** re-running a tag's run uses the workflow *at the tag*, so a workflow fix needs a new tag commit.
**Affects:** `.github/workflows/build.yml`

## 2026-10-21 — The global git identity is the personal email
**Symptom:** The last commits before Stage E (`4b818a2` and earlier, in the archive) carry the owner's personal email,
although the plan said "public identity = noreply".
**Cause:** `user.email` comes from the global `~/.gitconfig`, and nothing in the repo overrode it. A scratch repo or
a fresh clone inherits it too, and so does an annotated tag's tagger.
**Fix:** this repo's local config sets `user.name Wulfic` / `user.email 34788214+Wulfic@users.noreply.github.com`.
Anything made outside this working copy (`git init` in the scratchpad, `git commit-tree`, `git tag -a`) needs
`GIT_AUTHOR_*` / `GIT_COMMITTER_*` set explicitly. Check with `git log --format='%an <%ae> | %cn <%ce>'` before
pushing.
**Affects:** `.git/config` (local), any new clone

## 2026-10-21 — gitleaks has no rule for a context7 key or a plain password
**Symptom:** gitleaks 8.28 over the old history found only the GitHub fine-grained PAT (twice), not the `ctx7sk-`
key in the same files or the passwords in `.env`.
**Cause:** its rules match known token formats; a context7 key and `PASSWORD=...` lines match none.
**Fix:** a clean gitleaks run is necessary, not sufficient. Also search the tree (or every object, via
`git cat-file --batch-all-objects`) for the actual secret values read from the local files, printing only the
file or object names.
**Dead ends:** copying the secret files into the scratchpad for a positive control was blocked by the sandbox, and
rightly so. Scanning the old history in place (read-only Docker mount) is the control that copies nothing.
**Affects:** Stage E sweep
