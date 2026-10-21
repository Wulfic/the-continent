# Current State

> Rewritten at every phase boundary — not appended to. Keep it under ~30 lines so it is
> cheap to read at the start of every session. History belongs in `decisions.md` and
> `gotchas.md`; this file only answers "where am I right now?".

**Last updated:** 2026-10-21 (Stage E done: the repo is public)
**Branch:** `main`, always. This outranks any harness-assigned branch (decisions.md 2026-10-17).
The owner works on Windows: `./gradlew build` must stay green there too.

## Where we are
- **Two repos now** (decisions.md 2026-10-21 "A fresh public repo"):
  - `origin` = `Wulfic/the-continent`, the new repo. Its history starts at the clean root **c2cd2a8** ("The Continent
    1.0.0"). Public since 2026-10-21, with release v1.0.0 (9 jars, rebuilt by its own CI).
  - `archive` = `Wulfic/the-continent-archive`: private, archived, read-only. All old history, `mc/1.21.11`, tags and
    releases v0.1.0 / v0.2.0 / v1.0.0, Actions logs. Also in `../the-continent-archive-2026-10-21.bundle` (verified).
- Every SHA in `.agent/memory` and TODO.md before 2026-10-21's Stage E entry exists only in the archive.
- `mc/1.21.11` is local and tracks `archive`. It must never be pushed to `origin`, and the local `pre-push` hook
  refuses anything that doesn't descend from c2cd2a8. `remote.origin.push` pushes `main` only.

## Resume here
1. **The 26.x plan (stages A-E) is done.** Next work comes from the TODO backlog or the owner. Only secret
   scanning + push protection are on (owner's pick); there's no ruleset on `main` and no Dependabot.
2. **Owner, not blocking:** rotate the GitHub PAT, the context7 key and the system passwords that were in `.env`.
   They're still readable in the private archive.
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
- `.env`, `.mcp.json` and `.claude/mcp.vscode-reference.json` are untracked and ignored; copy the `.example` files.
- This repo's local git config sets the noreply identity. The global one is the personal email: never commit or
  tag in a fresh clone or a scratch repo without setting the identity first.
- `.claude/skills/` and `.github/skills/` are hand-synced copies — edit both.
