# Changelog

## Unreleased

### ⚠ Deploy this before touching arenas again

The arena data-loss bug below is live on AMP right now. Until the new jar is
installed, any restart can wipe every marker in the world.

1. **Stop** the AMP moshpit instance.
2. Upload `moshpit-plugin.jar`.
3. Upload `universe/worlds/Moshpit/resources/MiniGameArenaResource.json`.
4. Start, and confirm the log says `Arena marker store registered`.

If you ever see `ARENAS ARE DISABLED`, stop the server **before** it saves.

---

### Fixed — arena data loss ‼

**A restart could silently destroy every arena marker in the world.** This is what
ate the new Nuketown spawns.

`MiniGameArenaManager.setup()` runs twice on every boot: once from its `@OnSetup`
annotation, and again from an explicit call in `MiniGameEngine.setup()`. The second
call throws `id 'MiniGameArenaResource' already exists!`, which the old code caught
and logged as a single `WARN`.

When that failure leaves `MiniGameArenaResource.resourceType` unset — it is
`lateinit` — the consequences cascade silently:

1. Every `World.arenaResource` access throws.
2. `MiniGameArena.locations` catches that and returns an empty map.
3. Nothing teleports: no spawn on death, no spawn at round start, no power-ups,
   no airstrike, no hardpoint.
4. **The next world save writes those empty entries back over the file**, and the
   markers are gone from disk.

Registration is now idempotent, so the second call is a no-op. Success logs
`Arena marker store registered`; failure logs at `ERROR`, states that arenas are
disabled, and warns that a save will overwrite the marker file.

**Marker writes were impossible.** `ObjectMapCodec` wraps everything it decodes in
`Collections.unmodifiableMap`, regardless of the mutable supplier it is handed. So
`arena.entries` and each entry's `locations` were read-only once loaded from disk,
and `/arena loc set` threw a bare `UnsupportedOperationException` with no message.
Writes now go through `setLocation` / `removeLocation`, which copy before mutating.
`loc set` also echoes the coordinates it recorded.

### Fixed — commands

**`/arena loc set` never worked in this port.** Three faults in sequence:

- The command DSL registered only one level of subcommands, so a `collection`
  nested inside another had its children silently dropped. `arena loc` resolved to
  a zero-argument leaf and reported a confusing argument-count error.
- Once registration recursed, it attached each node to its parent *before*
  populating it. `AbstractCommand` finalises a subcommand when it is added, so
  filling `loc` afterwards threw `Cannot add new subcommands when a command has
  already completed registration` and took the plugin down at boot. Registration is
  now depth-first, attaching each node to its parent last.
- Container nodes were built as `AbstractPlayerCommand`. Vanilla uses
  `AbstractCommandCollection` for every non-leaf node; the difference is
  `isNoArgVariant()`, which tells the dispatcher that trailing tokens belong to a
  subcommand. Without it the root rejected input before consulting its own
  subcommand table.

**Command handlers failed completely silently.** Every handler ran inside a `launch`
with no exception handler on a bare `world.execute` dispatcher, wrapped in
`try { } finally { }` with no `catch`. A throw reached nothing — no log, no message,
no trace. That is what hid the fault above through four deploy cycles. Handlers now
log with the command name and report the failure to the sender, and log on entry so
"never dispatched", "never ran" and "threw" are distinguishable.

### Fixed — gameplay

**`/moshpit stopserver` evacuated nobody.** It called `EverlandsLeave.leaveAll()`
with no arguments, resolving to the `vararg` overload with an empty array. The
countdown ran, the broadcast went out, and every player stayed where they were. It
now refers everyone to the lobby and reports how many. The `vararg` overload has
been **removed** rather than fixed — it had one caller, the broken one.

**`MiniGame.json` did not set `Player.MovementConfig`.** The same omission broke
sprinting in the lobby. Added explicitly rather than by inheriting
`Parent: Default`, which would also have pulled in `HitboxCollisionConfig:
SoftCollision` and altered player-to-player collision on a live PvP server.

### Fixed — publishing

**The entire Kotlin codebase was excluded from git.** `.gitignore` contained a bare
`everlands/`, meant for the runtime progression store. Unanchored gitignore patterns
match at any depth, and every source file lives under `net/everlands/` — so that one
line dropped all 161 source files. The published repository had assets, worlds and
documentation, but nothing that compiles. Now anchored (`/everlands/`,
`*/server/everlands/`). Tracked files: 529 → 690.

**Player-data rules did not match at depth.** `universe/players/` and
`universe/memories*` were anchored to the repository root, so they never covered
`lobby/server/universe/players/` or the moshpit equivalent. Both are now
`**/universe/...`, verified with `git check-ignore`.

**The moshpit arena world is now published**, extracted rather than archived, with
the 8,451 third-party player save files filtered out.

### Changed — arenas

**Nuketown is now team-capable.** The rebuilt arena carries 51 markers — 20 plain
spawns, 8 ODA, 8 SENTINEL, 3 hardpoints, 8 power-ups, and the airstrike set — where
the original had plain spawns only. It has been promoted into `TEAM_READY_ARENAS`,
so every mode now draws from the full six-arena rotation and the FFA-only special
case is gone.

Two marker sets are inert and always were, ignored because they do not match the
prefixes the code looks for:

- `nuketown`: `airstrike:pos3` — only `pos1` and `pos2` are read, as opposite
  corners of the box missiles land in.
- `rust`: `spawn11` … `spawn25`, 13 markers missing the colon, so that map runs on
  13 spawns rather than 26.

### Changed — error handling

A measured sweep, not a blanket rewrite. Of 73 `runCatching` blocks, 49 already
handled failure and 17 were deliberate fallbacks; 8 genuinely discarded errors. Of
50 broad `catch` blocks, 47 already logged. Fixed where a silent failure produced a
baffling symptom:

| Site | Symptom when it failed |
|---|---|
| `MiniGameArenaManager` registration | Nothing teleports; marker file destroyed on save |
| `BeanScanner` bean registration | Feature silently does not exist |
| `UserStore` shutdown-hook save | Players lose levels after a restart, no trace |
| `UserStore` corrupt-file rename | The only copy of the bad file is overwritten |
| `Leaderboard.decode` | "Waiting for game server" — same as empty Redis |
| `LobbyCommand` page open | `/servers` does nothing; it is the only way out |
| `SpectatorManager` camera reset ×2 | Player stuck spectating until reconnect |
| `PlayerDisconnectListener` | Was `// Ignore` |

Four `println` and four `printStackTrace` replaced with the logger; both are now
zero repository-wide. Three sites remain deliberately silent, each with a comment
saying why.

### Changed — duplication

- **Three round clocks became one.** `GunGameCountdown` and `OITCCountdown` were
  byte-identical apart from doc comments; `FFACountdown` differed only in which enum
  and XP table it named. Now one `KillRaceCountdown` plus three ~20-line subclasses.
- **Three team strategies became one.** `TDMTeamStrategy`, `HardpointTeamStrategy`
  and `EliminationTeamStrategy` were the same class with a different participant
  type. Now `BalancedTeamStrategy` with a `participantProvider`.
- **K/D is defined once**, in `killDeathRatio`. It was computed in `LeaderboardRow`,
  `LeaderboardPublisher` and `StatsContainer`.

### Added — local test server

`StartServer.bat` used to run `:moshpit:runDevServer`, a Gradle task that does not
exist in this build — a leftover from Everlands' hyboot setup. It now builds the
plugin, assembles a scratch server in `.local-test/` against a **copy** of the world,
and starts Hytale. Output is teed to `.local-test/server.log`.

Two things it has to get right, both of which cost time to find:

- **`--assets` must be passed.** Its default is `../HytaleAssets`, a directory that
  does not exist. Without it the server logs `Skipping pack at HytaleAssets` and
  then no vanilla asset exists at all — no `DamageCause`, no `Weather`, no
  `ItemQuality`, no animations — and the boot dies on
  `Missing default DamageCause assets`.
- **The server jar and `Assets.zip` must be the same version.** Both are taken from
  the game install so they cannot drift.

### Changed — configuration

Server addresses can be overridden by environment variable (`EVERLANDS_GAME_HOST`,
`EVERLANDS_LOBBY_HOST`, `EVERLANDS_DISCORD_INVITE`). Nothing is required — the
compiled defaults still point at the Spellshot deployment and work unchanged.

> Note for forks: those defaults are a **live public server**. Change
> `LobbyConfig.servers[0].host` and `LobbyReferralConfig.host` before running your
> own instance.

### Changed — documentation

- **`moshpit/ARENAS.md`** — new. Full walkthrough for placing an arena's markers.
- **`README.md`** — the `/arena` syntax was wrong (`/arena <id> set spawn:1`; the
  real form is `/arena loc set <id> spawn:1`). Corrected, worlds section rewritten.
- **`lobby/DEPLOY.md`** — documents the environment variables.
- **`lobby/unported/`** — deleted three superseded leaderboard files; the directory
  contradicted its own README and `DEPLOY.md`.

### Verified clean

Checked and found nothing wrong, recorded so it is not re-checked:

- Asset references in both packs, cross-referenced against vanilla `Assets.zip` —
  **0 broken** out of ~600 paths.
- `.lang` keys — 14 defined, 14 used, none missing.
- No single file exceeds GitHub's 50 MB warning threshold.
