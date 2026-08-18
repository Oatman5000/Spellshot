# Everlands Lobby — deployment

A standalone lobby for its own Hytale instance. Salvaged from the `lobby/`
module of the original Everlands source, which was dropped during the standalone
port.

## Build

```powershell
cd C:\Users\dkibb\Downloads\everlands-update6-verified\everlands-main
.\gradlew.bat :lobby:shadowJar --console=plain
```

Output: `lobby\build\libs\lobby-plugin.jar`

## Upload to a fresh AMP instance

| From | To |
|---|---|
| `lobby\build\libs\lobby-plugin.jar` | `/<instance>/hytale/Server/mods/lobby-plugin.jar` |
| `lobby\server\mods\Everlands.Lobby\` | `/<instance>/hytale/Server/mods/Everlands.Lobby/` |

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

**Addresses ship blank and must be set, or nothing hands off.** They used to
default to a specific public server, which meant anyone cloning this repo ran a
lobby that referred their players onto somebody else's instance.

Set them as environment variables on the AMP instance — no rebuild, and the two
instances can be repointed by restarting:

| Instance | Variable | What it does |
|---|---|---|
| **Lobby** | `EVERLANDS_GAME_HOST` | Where the Navigator sends players |
| **Lobby** | `EVERLANDS_GAME_PORT` | Defaults to 5524 |
| **Lobby** | `EVERLANDS_DISCORD_INVITE` | Printed by the Discord button; blank hides it |
| **Moshpit** | `EVERLANDS_LOBBY_HOST` | Where `/lobby` sends players back to |
| **Moshpit** | `EVERLANDS_LOBBY_PORT` | Defaults to 5523 |

In AMP: *Configuration → Environment Variables* on each instance, then restart.

Without these you get, and the console says the same:

```
Navigator  ->  "PVP has no address configured. Set EVERLANDS_GAME_HOST."
/lobby     ->  "This server is standalone - there is nowhere to return to."
```

The literal values are still editable in `LobbyConfig` and `LobbyReferralConfig`
if you would rather bake them in, but the environment overrides win and keep the
addresses out of the published source.

## What players get

* Spawn at the world spawn with a **Navigator** in slot 1, full health.
* Navigator (left or right click) opens the **server selector**.
* `/servers` prints the same list to chat and opens the selector.
* No damage, no item drops, no building.
* Falling into water or below Y=0 returns them to spawn.

## The handoff — read this

Picking a server **does not connect you to it.** The Hytale protocol has no
transfer packet: `packets/connection` contains only Connect, Ping/Pong and the
two Disconnect packets, and `ServerDisconnect` carries a message and a
`DisconnectType` — no address. The original Everlands did this by calling
`matchmakingService.joinQueue(...)` against Spookline's coordinator, which owned
both servers and moved players from outside the game.

So the selector hands over the address and tells the player to use Direct
Connect.

### The one thing worth testing

`HytaleServerConfig` has a `fallbackServer` field (`HostAddress { host, port }`,
config key `FallbackServer`) that is shipped to the client at setup inside the
`ServerInfo` packet. It is clearly meant for "if this server drops you, go
here". Whether the client follows it on a *clean* disconnect is unverified.

To test:

1. Add to the lobby instance's `config.json`:
   `"FallbackServer": { "Host": "your-moshpit-host", "Port": 25565 }`
2. Connect to the lobby, then stop the lobby instance from AMP.
3. See whether the client lands in moshpit or at the main menu.

If it reconnects, set `disconnectOnSelect = true` in `LobbyConfig` and picking a
server becomes a real handoff. If it drops to the menu, leave it `false` — it
would only strand people.

Worth doing regardless: set `FallbackServer` on the **moshpit** instance
pointing at the **lobby**, so a game-server restart returns players to the lobby
instead of the main menu. That is the direction the field is unambiguously for.

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
everlands:leaderboard:v1   →  JSON snapshot, 900s TTL
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
| `leaderboardKey` | `everlands:leaderboard:v1` |
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
