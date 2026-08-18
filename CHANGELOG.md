# Changelog

## Unreleased

### ⚠ Action required

Two changes need something from you before this version behaves correctly. See
`RELEASE-CHECKLIST.md`.

1. **Server addresses now ship blank** and must be set as environment variables on
   each AMP instance, or the Navigator and `/lobby` will not hand off.
2. **The published repository was missing all source code** until this release. If you
   pushed the previous commit, it must be force-pushed again.

---

### Fixed — publishing

**The entire Kotlin codebase was excluded from git.** `.gitignore` contained a bare
`everlands/`, intended for the runtime progression store written next to the server
jar. Unanchored gitignore patterns match at any depth, and every source file lives
under `net/everlands/` — so that one line silently dropped all 161 source files. The
published repository contained assets, worlds and documentation, but nothing that
compiles. The rule is now anchored (`/everlands/`, `*/server/everlands/`).

Tracked files went from 529 to 688. The exclusions that matter — player saves,
credentials, build output, archives — were re-verified and still hold.

**Player-data rules did not match at depth.** `universe/players/` and
`universe/memories*` were anchored to the repository root, so they never covered
`lobby/server/universe/players/` or `moshpit/server/universe/players/`. Those stayed
out only because one directory had its own `.gitignore` and the other happened to be
empty. Both are now `**/universe/...` and verified with `git check-ignore`.

**The moshpit arena world is now published**, extracted rather than as an archive, with
the 8,451 third-party player save files filtered out during extraction. See the note in
`README.md` about the arena layouts.

### Fixed — commands

**`/arena loc set` never worked in this port.** Three separate faults, in sequence:

- The command DSL registered only one level of subcommands. A `collection` nested
  inside another had its children silently dropped, so `arena loc` resolved to a
  zero-argument leaf and reported "wrong number of required arguments" — an error that
  points at argument counts and gives no hint the subcommand was never wired up.
- Once registration recursed, it attached each node to its parent *before* populating
  it. `AbstractCommand` finalises a subcommand when it is added, so filling `loc`
  afterwards threw `Cannot add new subcommands when a command has already completed
  registration` and took the whole plugin down at boot. Registration is now depth-first,
  attaching each node to its parent last.
- Container nodes were built as `AbstractPlayerCommand`. Vanilla uses
  `AbstractCommandCollection` for every non-leaf node — the difference is
  `isNoArgVariant()`, which tells the dispatcher that trailing tokens belong to a
  subcommand. Without it the root rejected the input before consulting its own
  subcommand table.

**Marker writes threw `UnsupportedOperationException`.** `ObjectMapCodec` wraps
everything it decodes in `Collections.unmodifiableMap`, regardless of the mutable
supplier it is given. So `arena.entries` and each entry's `locations` were read-only
once loaded from disk, and the original in-place mutation failed with a bare exception
carrying no message. Writes now go through `setLocation` / `removeLocation`, which copy
before mutating. `/arena loc set` also echoes the recorded coordinates.

**Command handlers failed completely silently.** Every handler ran inside a `launch`
with no exception handler on a bare `world.execute` dispatcher, wrapped in
`try { ... } finally { ... }` with no `catch`. A throw reached nothing: no log, no
message, no stack trace. That is what hid the fault above through four deploy cycles.
Handlers now log with the command name and report the failure to the sender, and log a
line on entry so "never dispatched", "never ran" and "threw" are distinguishable.

### Fixed — gameplay

**`/moshpit stopserver` evacuated nobody.** It called `EverlandsLeave.leaveAll()` with
no arguments, which resolved to the `vararg` overload with an empty array. The
countdown ran, the "server is restarting" broadcast went out, and every player stayed
exactly where they were. It now refers everyone to the lobby, reports how many, and
warns when no lobby is configured. The `vararg` overload has been **removed** rather
than fixed — it had one caller, the broken one, and an empty call compiled happily.

**`MiniGame.json` did not set `Player.MovementConfig`.** The same omission broke
sprinting in the lobby. Added explicitly rather than by inheriting `Parent: Default`,
which would also have pulled in `HitboxCollisionConfig: SoftCollision` and altered
player-to-player collision on a live PvP server.

### Changed — configuration ⚠

**Server addresses no longer ship with working defaults.** `play.oathosting.com` was a
default in `LobbyConfig` and `LobbyReferralConfig`, so anyone cloning this repository
ran a lobby that referred *their* players onto *that* server. Both now default to blank
and are set by environment variable, following the pattern `RedisConfig` already used:

| Instance | Variable |
|---|---|
| Lobby | `EVERLANDS_GAME_HOST`, `EVERLANDS_GAME_PORT` |
| Lobby | `EVERLANDS_DISCORD_INVITE` |
| Moshpit | `EVERLANDS_LOBBY_HOST`, `EVERLANDS_LOBBY_PORT` |

Unset, the Navigator says the server has no address configured and names the variable
to set; `/lobby` reports the server is standalone. Both also log it.

### Changed — error handling

A measured sweep, not a blanket rewrite. Of 73 `runCatching` blocks, 49 already handled
failure and 17 were deliberate fallbacks; 8 genuinely discarded errors. Of 50 broad
`catch` blocks, 47 already logged. Fixed where a silent failure produced a baffling
symptom:

| Site | Symptom when it failed |
|---|---|
| `BeanScanner` bean registration | Feature silently does not exist |
| `UserStore` shutdown-hook save | Players lose levels after a restart, no trace |
| `UserStore` corrupt-file rename | The only copy of the bad file is overwritten |
| `Leaderboard.decode` | "Waiting for game server" — same as empty Redis |
| `LobbyCommand` page open | `/servers` does nothing; it is the only way out |
| `SpectatorManager` camera reset ×2 | Player stuck in spectator view until reconnect |
| `PlayerDisconnectListener` | Was `// Ignore` |

Four `println` and four `printStackTrace` replaced with the logger; both are now zero
repository-wide. Three sites are still deliberately silent — Redis pool close during
shutdown, the chat-send inside the DSL's own error handler, and two `Raycasts`
fallbacks — each now carrying a comment saying why.

### Changed — duplication

- **Three round clocks became one.** `GunGameCountdown` and `OITCCountdown` were
  byte-identical apart from their doc comments; `FFACountdown` differed only in which
  enum and XP table it named. Now one `KillRaceCountdown` plus three ~20-line
  subclasses. It ranks from `MiniGameSession.teams` rather than each session's own
  near-identical `findWinner()`/`topList`, so no interface or cast was needed.
- **Three team strategies became one.** `TDMTeamStrategy`, `HardpointTeamStrategy` and
  `EliminationTeamStrategy` were the same class with a different participant type. Now
  `BalancedTeamStrategy`, taking a `participantProvider` — the pattern
  `SingleTeamPerPlayerTeamStrategy` already used.
- **K/D is defined once.** It was computed in `LeaderboardRow`, `LeaderboardPublisher`
  and `StatsContainer`. All three agreed, but nothing made them agree.

### Changed — documentation

- **`moshpit/ARENAS.md`** — new. Full walkthrough for placing an arena's markers:
  counts per mode, the airstrike bounding box, clearing an arena before rebuilding it,
  and the failure modes that produce no error message.
- **`README.md`** — the `/arena` syntax was wrong (`/arena <id> set spawn:1`; the real
  form is `/arena loc set <id> spawn:1`). Corrected, and the worlds section rewritten
  now that both worlds ship.
- **`lobby/DEPLOY.md`** — documents the required environment variables.
- **`lobby/unported/`** — deleted three superseded leaderboard files. The directory
  contradicted its own README and `DEPLOY.md`, both of which said only `Stamina.json`
  remained; the parked `Leaderboard.ui` had also diverged from the live one.

### Verified clean

Checked and found nothing wrong, recorded so it is not re-checked:

- Asset references in both packs, cross-referenced against vanilla `Assets.zip` —
  **0 broken** out of ~600 paths.
- `.lang` keys — 14 defined, 14 used, none missing.
- No single file exceeds GitHub's 50 MB warning threshold.
