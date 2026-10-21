# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-21 (Stages A-D done; v1.0.0 released)
**Branch:** `main`, always. This outranks any harness-assigned branch (decisions.md 2026-10-17).
Only `main` exists on the remote. `mc/1.21.11` (frozen 1.21.11 line, critical fixes only) exists locally from 2026-10-20. The owner works on Windows: `./gradlew build` must stay green there too.

## Where we are
- **26.x plan in progress** (TODO.md stages A–E; owner answers 2026-10-20: push once per stage, first 26.x
  release is **1.0.0**, public identity = noreply, credentials **not rotated yet**, so Stage E is blocked on E.1).
- **Stage A done, pushed, CI green** (run 39488841812 on a5b0961): Fabric on bands 26.1.2 / 26.2 / 26.3 as
  Stonecutter nodes, 26.3 worldgen port included. Per-band jars `the_continent-<v>+<loader>-mc<label>.jar`.
- **Stage B done, committed and pushed at session end** (2026-10-20): `neoforge/` and `forge/` branches,
  `tools/server_smoke.py` + `tools/smoke_band.py`, a CI smoke step for all four loaders per band, Forge
  `pack.mcmeta`. Locally: all 12 nodes build and test green, and all 12 loader × band smokes pass; the NeoForge
  mutation check goes red as it should.

## Resume here
1. **v1.0.0 is released (2026-10-21)**, tag on d1c7545: 9 band x loader jars, their sources jars and
   `SHA256SUMS`, checked after download. Stages A-D of the 26.x plan are done.
2. **Stage E is next and blocked on E.1:** the owner rotates every credential in `.env`, `.mcp.json` and
   `.claude/mcp.vscode-reference.json` (asked 2026-10-20: not yet). E.2 (untrack those files) reverses an owner
   decision, so ask before starting it even then.
3. Running the release scripts' tests locally: Docker, see gotchas 2026-10-21. Before any future tag, download a
   real run's artifacts and run the guard on them (gotchas 2026-10-21, upload-artifact).

## How to work in this repo now
- Sources are committed with Stonecutter's active band **26.1.2**. To edit another band's code as plain Java:
  `./gradlew "Set active project to 26.3"`, then `./gradlew "Reset active project"` **before committing**.
- Decompiled Minecraft for a band: `./gradlew :common:<band>:genSources` (~8 min) → Loom's
  `minecraftMaven/.../minecraft-merged-deobf-<band>-sources.jar`. Read it rather than guess APIs.
- Long runs (builds, gametests, smokes): run in the background **and** watch them with a monitor that reports
  failures and hangs. A client gametest whose log stops at "Stopping singleplayer server" is hung (gotchas).

## Do not forget
- `.env` and both MCP configs are tracked with live credentials (owner's call). Rotate the `ghp_` PAT.
- `.claude/skills/` and `.github/skills/` are hand-synced copies — edit both.
