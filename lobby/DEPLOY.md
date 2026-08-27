# Spellshot Lobby — deployment

A standalone lobby for its own Hytale instance. Salvaged from the `lobby/`
module of the original Everlands source, which was dropped during the standalone
port.

## Build

```powershell
cd path/to/Spellshot
.\gradlew.bat :lobby:shadowJar --console=plain
```

Output: `lobby\build\libs\lobby-plugin.jar`

## Upload to a fresh AMP instance

| From | To |
|---|---|
| `lobby\build\libs\lobby-plugin.jar` | `/<instance>/hytale/Server/mods/lobby-plugin.jar` |
| `lobby\server\mods\Spellshot.Lobby\` | `/<instance>/hytale/Server/mods/Spellshot.Lobby/` |

**Do not also install `moshpit-plugin.jar`.** Moshpit creates a match on
`PlayerSetupConnectEvent` and calls `error("No arenas available")` on a world
with no arena markers, so the first player to connect takes the server down.

## World setup

In the instance's `config.json`:

```json
"Defaults": { "World": "lobby", "GameMode": "Adventure" }
```

And in `universe/worlds/lobby/config.json`:

```json
"IsPvpEnabled": false,
"IsFallDamageEnabled": false,
"GameplayConfig": "Lobby"
```

`GameplayConfig: "Lobby"` picks up `Server/GameplayConfigs/Lobby.json` from the
pack, which disables block breaking/placement, health bars, combat text and
incoming damage. `Server/Entity/Stats/Stamina.json` zeroes stamina drain so
nobody gets winded walking around.

## Configure your servers

> **Addresses ship blank and must be set**, or nothing hands off. A real host is
> deliberately kept out of the published source.

**Edit the source and rebuild.** Two fields:

| File | Field |
|---|---|
| `lobby/src/main/kotlin/net/spellshot/lobby/LobbyConfig.kt` | `servers[0].host`, and `discordInvite` |
| `moshpit/src/main/kotlin/net/spellshot/moshpit/moshpit/LobbyReferral.kt` | `host` |

To keep your address out of commits while still building with it, mark both files
`skip-worktree` after editing:

```powershell
git update-index --skip-worktree lobby/src/main/kotlin/net/spellshot/lobby/LobbyConfig.kt
git update-index --skip-worktree moshpit/src/main/kotlin/net/spellshot/moshpit/moshpit/LobbyReferral.kt
```

Git then ignores your local edits entirely — `git status` stays clean and pushes
carry the blanks. Undo with `--no-skip-worktree`. Be aware that a `git pull`
which changes either file will fail until you unprotect it.

**Runtime overrides** exist and win over the compiled value, but note that **AMP
provides no way to set environment variables on a game instance**
([CubeCoders/AMP#1321](https://github.com/CubeCoders/AMP/issues/1321)) — these
are only usable if you launch the server yourself or run it under Docker:

| Instance | Variable | What it does |
|---|---|---|
| **Lobby** | `SPELLSHOT_GAME_HOST` | Where the Navigator sends players |
| **Lobby** | `SPELLSHOT_GAME_PORT` | Defaults to 5524 |
| **Lobby** | `SPELLSHOT_DISCORD_INVITE` | Printed by the Discord button |
| **Moshpit** | `SPELLSHOT_LOBBY_HOST` | Where `/lobby` sends players back to |
| **Moshpit** | `SPELLSHOT_LOBBY_PORT` | Defaults to 5523 |

Blank the host deliberately and the lobby says the server has no address, while
`/lobby` reports the game server is standalone. Both log it too.

## What players get

* Spawn at the world spawn with a **Navigator** in slot 1, full health.
* Navigator (left or right click) opens the **server selector**.
* `/servers` prints the same list to chat and opens the selector.
* No damage, no item drops, no building.
* Falling into water or below Y=0 returns them to spawn.

## The handoff

Picking a server **does** connect you to it. Hytale has native player referral:

```kotlin
PlayerRef.referToServer(host, port)
```

The server sends a referral packet naming the target and the client opens a new
connection to it. No proxy, no coordinator, nothing to type. See "Multiserver
Architecture" in the Hytale Server Manual. `/lobby` on the game server sends
players back the same way.

> This section previously claimed the protocol had no transfer packet and that the
> selector could only print an address for Direct Connect. That was wrong — the
> conclusion came from grepping `PlayerRef` for *disconnect* and *kick* and never
> for *refer*. The other [HandoffMode] values (`Message`, `Disconnect`,
> `DropConnection`) are diagnostics kept from working that out; `Refer` is the one
> to use.

Disconnect Fallback — `FallbackServer` in `config.json` — is documented as
"Coming Soon" and is not implemented in 0.5.7. A dropped player lands at the main
menu regardless of what that field says.

## Not ported

See `unported/README.md`. The leaderboard has since been rebuilt on Redis — see
below — so only the original `Stamina.json` override remains parked there.

---

# Leaderboard

The game server owns the stats and publishes a ranked snapshot to Redis; the
lobby reads it and draws it. Both sides no-op when Redis is not configured, so
you can deploy this and turn it on later.

## 1. Redis

Any Redis 6+ will do. It only ever holds one key.

```
spellshot:leaderboard:v1   →  JSON snapshot, 900s TTL
```

The TTL matters: if the game server goes down, the board expires rather than the
lobby showing yesterday's numbers forever.

## 2. Configure both instances

`RedisConfig` lives in `:core`, so the same fields exist on both jars. Host is
blank by default, which disables everything.

| Field | Notes |
|---|---|
| `host` | Blank = leaderboard off entirely |
| `port` | 6379 |
| `password` | Blank for none |
| `leaderboardKey` | `spellshot:leaderboard:v1` |
| `snapshotTtlSeconds` | 900 |

Game server also has `LeaderboardPublisherConfig`:

| Field | Default | Notes |
|---|---|---|
| `intervalSeconds` | 120 | How often a snapshot is published |
| `topN` | 50 | Rows kept per board |
| `minKillsForKd` | 25 | Floor for the K/D board, or one player with 1 kill and 0 deaths owns it |

Lobby also has, on `LobbyConfig`:

| Field | Default | Notes |
|---|---|---|
| `leaderboardItemId` | `Leaderboard` | Second hotbar item; null to omit |
| `leaderboardRefreshSeconds` | 60 | How often the lobby re-reads Redis |
| `leaderboardModes` | FFA, TDM, HARDPOINT, OITC, ELIMINATION, GUNGAME | Must match the MoshpitGame enum names |

`leaderboardModes` is a string list because the lobby jar does not depend on
moshpit and cannot read the enum. If you add a mode, add it here too.

## 3. Boards

Ten tabs: Global kills, Wins, Level, K/D, then kills per mode.

* **Level** ranks on XP but displays the level -- level alone ties constantly.
* **K/D** is published as a scaled integer so the snapshot sorts on a Long, and
  divided back out for display.
* **Wins** is new. Nothing tracked it before; each mode's win path now calls
  `user.recordWin(game)`. Existing players start from zero.

## 4. Boot log

Game server:

```
Leaderboard publishing disabled - no Redis host configured   ← host not set
Redis pool created for <host>:<port>                         ← working
```

Lobby:

```
Registered interaction codec 'LeaderboardInteraction'        ← item will work
Leaderboard disabled - no Redis host configured              ← host not set
```

If the lobby shows "Waiting for data from the game server", the lobby reached
Redis but the game server has not published yet -- give it `intervalSeconds`.
