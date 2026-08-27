# Spellshot

A multi-mode PvP server for **Hytale**, running on Update 6 / Server `0.6.0`.

Six game modes, a persistent progression and loadout system, a hextech-styled
HUD, and a separate lobby instance with a cross-server leaderboard.

---

## Credit

**This is a port and extension of [Everlands](https://everlands.net) Moshpit,
created by Spookline.** The original mini-game engine, arena system, progression,
loadouts, weapon assets, HUD framework and lobby module are all their work, and
the bulk of this codebase is still theirs. Original author credited throughout
the asset manifests as **XiaBit**.

Published with the permission of the Everlands developers. Thanks in particular
to **Helight** for the go-ahead and for answering licensing questions.

Everlands was built on Spookline's own `hyboot` platform — a coordinator,
matchmaking service and cross-server user store that is not public. This port
replaces those pieces with standalone equivalents (see *Differences from
upstream* below), but the design they shaped is unchanged.

Licensed **AGPL-3.0**, as the original is. See `LICENSE`.

> **If you run this as a public server, AGPL §13 applies to you.** Anyone who
> connects is entitled to the source you are running. Publishing your fork and
> linking it from your Discord or MOTD is the simplest way to comply.

---

## What's here

| Module | Purpose |
|---|---|
| `core` | Shared framework — Spring wiring, ECS helpers, config, user store, leaderboard model and Redis client |
| `moshpit` | The game server: six modes, arenas, progression, loadouts, HUD |
| `lobby` | A separate instance: server selector, leaderboard, lobby world behaviour |

### Game modes

| Mode | Shape |
|---|---|
| **FFA** | Timed free-for-all, most kills wins |
| **TDM** | Two teams, first to a point target |
| **Hardpoint** | Rotating capture point |
| **One in the Chamber** | One bullet, one-shot kills, a kill refunds a bullet, miss and you're on the knife |
| **Elimination** | TDM with one life per round, best of five, follow-cam spectating |
| **Gun Game** | Nine-weapon ladder; every kill promotes you, every death resets you to the knife |

FFA, TDM and Hardpoint are Everlands originals. OitC, Elimination and Gun Game
were added in this fork.

---

## Building

Requires **JDK 25** and the Hytale server jar from
[maven.hytale.com](https://maven.hytale.com/release).

```bash
./gradlew :moshpit:shadowJar   # -> moshpit/build/libs/moshpit-plugin.jar
./gradlew :lobby:shadowJar     # -> lobby/build/libs/lobby-plugin.jar
```

The two jars go on **separate Hytale instances**. Do not install both on one
server: the moshpit plugin creates a match on connect and fails on a world with
no arena markers.

---

## Worlds

Both worlds are included and ready to run:

| Path | Contents |
|---|---|
| `moshpit/server/universe/` | The Moshpit arena world, with arena markers already placed |
| `lobby/server/universe/` | The lobby world — Everlands' own build, shipped with their permission |

Drop the matching `universe/` next to each instance's server jar.

> **On the arenas.** Mirage and Dust2 are recreations of Counter-Strike level
> designs; Rust, Hijacked, Nuketown and Raid are recreations of Call of Duty
> maps. The geometry is original Hytale blockwork, but the layouts are not ours
> and are not covered by this project's licence. They are here because the
> server they came from ran them; if that is a problem for how you intend to use
> this, delete `moshpit/server/universe/` and build your own — everything else
> works without it.

**No player data is included.** The archive this world came from also held 8,451
per-player save files from the original Everlands server. Those are other
people's data and are not published here; `universe/players/` and
`universe/memories*` are excluded in `.gitignore` so they stay out.

### Building your own arenas

An arena is a set of named markers in a world, placed in-game by standing where
the marker should go:

```
/arena loc set <id> spawn:1              # free-for-all spawns
/arena loc set <id> team:ODA:spawn:1     # team spawns (TDM, Elimination)
/arena loc set <id> team:SENTINEL:spawn:1
/arena loc set <id> hardpoint:1          # capture point
/arena loc del <id> <marker>             # remove one
/arena entries                           # arenas in this world
/arena info <id>                         # every marker with coordinates
/arena debug <id>                        # visualise for 30 seconds
```

Then register the id in `MiniGameArenasConfig`, and list which modes may use it
in `MoshpitConfig`. Team modes need `team:` spawns; Hardpoint needs a
`hardpoint:` marker. Vote-screen previews go in
`Spellshot.Game/Common/UI/Custom/Maps/` as `<id>.png`.

**[`moshpit/ARENAS.md`](moshpit/ARENAS.md) is the full walkthrough** — marker
counts per mode, the airstrike box, how to clear an arena before rebuilding it,
and the failure modes that produce no error message.

---

## Lobby and multi-server

The lobby runs as its own instance and hands players over using Hytale's native
player referral:

```kotlin
PlayerRef.referToServer(host, port)
```

No proxy, no BungeeCord equivalent, no coordinator. `/lobby` on the game server
sends players back the same way.

A Redis-backed leaderboard links the two: the game server publishes a ranked
snapshot, the lobby reads and renders it. Ten boards — global kills, wins, level,
K/D, and kills per mode. Both sides no-op when Redis is not configured.

Full setup, config tables and boot-log checks are in **[`lobby/DEPLOY.md`](lobby/DEPLOY.md)**.

---

## Differences from upstream

Everlands ran on Spookline's `hyboot` platform. This port stands alone:

| Upstream | Here |
|---|---|
| `HUsers` — cross-server user store | `UserStore` — per-instance `spellshot/users.json` |
| `HMatchmakingService` — queue-based server transfer | `PlayerRef.referToServer` — Hytale's native referral |
| `HRemoteConfigs` — remote config service | `Config<T>` — in-code defaults, some env-overridable |
| `kotale` — Kotlin plugin framework | `EverlandsPlugin` — Spring context in the Hytale classloader |
| Coordinator-backed leaderboard | Redis snapshot published by the game server |

`lobby/unported/` holds the few pieces that could not come across, with notes.

---

## Status

Running in production. Built and maintained by **Oatman5000**, on the shoulders
of the Everlands team's work.
