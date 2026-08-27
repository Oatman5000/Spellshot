# Setting up an arena

How to place the marker set a map needs, using the new Nuketown as the worked
example. Everything here applies to any arena; substitute your own id.

An "arena" is not a world and not a region. It is just a **named bag of
positions** stored per-world in `universe/worlds/<World>/resources/MiniGameArenaResource.json`.
The game modes look those positions up by key prefix. Nothing validates them, so
a typo does not error — it silently does nothing.

---

## 0. Before you start

**Back up the world.** Marker edits are live and there is no undo.

```
moshpit/server/universe/worlds/Moshpit/
```

Copy that folder somewhere safe. The `resources/*.json.bak` files Hytale writes
are one save behind, not a real backup.

**Decide whether you are replacing or adding.** Replacing `nuketown` in place is
covered below and needs the old markers cleared first. If you would rather keep
the old one runnable while you build, use a new id (`nuketown2`) and delete the
old entry when you are happy — the id is arbitrary, only `MiniGameArenasConfig`
gives it meaning.

---

## 1. Paste the prefab

Paste it wherever you like in the Moshpit world. The markers you are about to
place are **absolute world coordinates**, so the only thing that matters is that
you place them after the prefab is in its final position. Move the build later
and every marker is left behind pointing at empty air.

Give it clearance from the other arenas. Airstrike bounds and power-up spawns are
per-arena, but players are not — spawn logic picks the marker furthest from *any*
online player, so overlapping builds make for strange spawn choices.

---

## 2. Clear the old Nuketown markers

**Do not skip this.** `loc set` overwrites by key, so if the old Nuketown has 19
`spawn:N` markers and you place 15, the leftover four still point into the old
build and players will spawn there mid-match. It looks exactly like a random
teleport bug and is miserable to trace.

There is no `/arena del <id>` command, only per-location delete. Fastest route is
offline:

1. Stop the server **cleanly** from AMP (not a kill — the world has to flush).
2. Open `moshpit/server/universe/worlds/Moshpit/resources/MiniGameArenaResource.json`.
3. Delete the whole `"nuketown": { ... }` block under `"Entries"`, mind the
   trailing comma.
4. Start the server.

Or, from the repo root with the server stopped:

```bash
python3 - <<'EOF'
import json
p = "moshpit/server/universe/worlds/Moshpit/resources/MiniGameArenaResource.json"
d = json.load(open(p))
removed = d["Entries"].pop("nuketown", None)
print("removed" if removed else "not found",
      len(removed["Locations"]) if removed else 0, "markers")
json.dump(d, open(p, "w"), indent=1)
EOF
```

Confirm with `/arena entries` in-game — `nuketown` should be gone from the list.

---

## 3. Get yourself out of a match

The plugin puts you into a session on connect, which means respawns and round
resets will keep yanking you around while you work.

```
/moshpit leave        # drop out of the current session
/moshpit stopserver   # end the session entirely
```

Then switch to Creative for flight. You need the `game.arena` permission for the
`/arena` commands.

---

## 4. Place the markers

One command per marker. It records **your exact position and the direction you
are facing**, so stand where a player should appear and look where they should be
looking.

```
/arena loc set nuketown <marker>
```

Work through the list in order. The numbers are cosmetic — only the prefix is
matched — so gaps are harmless, but keeping them sequential makes auditing easier.

### Free-for-all spawns — 16 to 20

Used by FFA, One in the Chamber and Gun Game.

```
/arena loc set nuketown spawn:1
/arena loc set nuketown spawn:2
...
/arena loc set nuketown spawn:18
```

Spread them evenly across the whole map. The spawn picker chooses the marker
furthest from the nearest player and keeps a 5-deep history to avoid immediate
reuse, so clustering them defeats it and you get spawn-camping.

### Team spawns — 8 per side

Needed for TDM and Elimination. **The old Nuketown had none**, which is why it was
FFA-only. Add these and it can host every mode.

```
/arena loc set nuketown team:ODA:spawn:1
...
/arena loc set nuketown team:ODA:spawn:8

/arena loc set nuketown team:SENTINEL:spawn:1
...
/arena loc set nuketown team:SENTINEL:spawn:8
```

Team ids are exactly `ODA` and `SENTINEL` — case sensitive, and the parser reads
the second colon-segment as the team name. Put each side's eight in its own
half of the map; Elimination is one life per round, so a bad spawn is a lost
round rather than a lost respawn.

### Hardpoint capture points — 2 or 3

```
/arena loc set nuketown hardpoint:1
/arena loc set nuketown hardpoint:2
/arena loc set nuketown hardpoint:3
```

These rotate on a timer. Stand in the centre of each intended capture zone —
the marker is a point, and the capture radius comes from `HardpointConfig.pointRange`.
Hardpoint refuses to start on an arena with none of these.

### Power-ups — 2 to 4

```
/arena loc set nuketown powerup:HEALTH:1
/arena loc set nuketown powerup:HEALTH:2
/arena loc set nuketown powerup:SPEED:1
/arena loc set nuketown powerup:SPEED:2
```

`HEALTH` and `SPEED` are the only two types and must be spelled exactly — the
type is parsed out of the second segment and an unknown value throws. Both
respawn 30 seconds after pickup.

### Airstrike — 3, and the fiddly ones

```
/arena loc set nuketown plane
/arena loc set nuketown airstrike:pos1
/arena loc set nuketown airstrike:pos2
```

* `plane` — where the flyover spawns, well outside the map and above it. **Your
  facing direction is the flight path**, so line yourself up across the map
  before running it.
* `airstrike:pos1` / `airstrike:pos2` — two **opposite corners of a box**.
  Missiles land at a random X and Z between them. Only X and Z are read; height
  is ignored. Put them at opposite corners of the playable area, and set pos1 to
  the corner with the smaller X and Z — the code does `random * (pos2 - pos1) + pos1`,
  so reversing them still works but is easier to reason about the right way round.

Airstrikes silently no-op with a log warning if any of the three is missing.

---

## 5. Verify before you trust it

```
/arena info nuketown     # every key with its coordinates
/arena debug nuketown    # draws boxes in-world for 30 seconds
```

Read the `/arena info` output properly and count each group. What you are looking
for is typos in prefixes, because those are invisible at runtime.

> **Real example:** the Rust arena has 13 markers named `spawn11`, `spawn12` …
> `spawn25` — no colon. They do not match the `spawn:` prefix filter, so they are
> ignored entirely and that map has been running on 13 spawns instead of 26 the
> whole time. Nothing logged.

**A quirk of `/arena debug`:** it colours by the first match, checking `spawn`
before `team`, so `team:ODA:spawn:1` draws grey like an FFA spawn rather than
navy. Team markers are still there — trust `/arena info` over the colours.

Offline audit of the whole set:

```bash
python3 - <<'EOF'
import json, collections
p = "moshpit/server/universe/worlds/Moshpit/resources/MiniGameArenaResource.json"
locs = json.load(open(p))["Entries"]["nuketown"]["Locations"]
c = collections.Counter()
for k in locs:
    parts = k.split(":")
    c[":".join(parts[:-1]) if parts[-1].isdigit() else k] += 1
for kind, n in sorted(c.items()):
    print(f"{n:3}  {kind}")
EOF
```

Target shape for a full-mode arena:

| Prefix | Count | Needed by |
|---|---|---|
| `spawn:` | 16–20 | FFA, OitC, Gun Game |
| `team:ODA:spawn:` | 8 | TDM, Elimination |
| `team:SENTINEL:spawn:` | 8 | TDM, Elimination |
| `hardpoint:` | 2–3 | Hardpoint |
| `powerup:HEALTH:` / `powerup:SPEED:` | 2–4 | all |
| `plane`, `airstrike:pos1`, `airstrike:pos2` | 3 | airstrike killstreak |

---

## 6. Save it

Markers live in a chunk-store resource, which persists on world save. **Stop the
server cleanly from AMP** — killing the process loses everything you just placed.
Then confirm the JSON on disk actually contains your markers before you celebrate.

---

## 7. Config, if you added team spawns

Two edits, then rebuild.

`MiniGameArenasConfig.kt` — the entry already exists, but drop the stale comment:

```kotlin
MiniGameArena("nuketown", "Nuketown", "everlands")
```

`MoshpitConfig.kt` — Nuketown is currently special-cased into FFA-only. Add it to
the team-ready list and delete the exception:

```kotlin
private fun defaultArenasFor(game: MoshpitGame): List<String> = TEAM_READY_ARENAS
```

with `"nuketown"` added to `TEAM_READY_ARENAS`. Also update the doc comment above
it, which currently explains why Nuketown is excluded.

```bash
./gradlew :moshpit:shadowJar
```

Optional: a `<id>.png` in `Spellshot.Game/Common/UI/Custom/Maps/` for the vote
screen, and `dieOnWater` / `dieOnWaterHeight` on the `MiniGameArena` entry if the
map has water or a kill floor.

---

## 8. Things that bite

| Symptom | Cause |
|---|---|
| Players spawn in the old build | Leftover markers from the previous arena — you placed fewer than existed |
| Marker does nothing, no error | Prefix typo. `spawn11` is not `spawn:11` |
| Hardpoint refuses to start | No `hardpoint:` markers |
| `Team ODA not found in arena` | Missing or misspelled `team:ODA:spawn:N` — case sensitive |
| Airstrike does nothing, warning in log | Missing `plane`, `airstrike:pos1` or `airstrike:pos2` |
| Missiles land off-map | pos1/pos2 are corners of the *playable* area, not the world |
| Everything vanished after restart | Server was killed rather than stopped cleanly |
| Power-up crash on spawn | Type is not exactly `HEALTH` or `SPEED` |

---

## Moving markers between servers

Markers live inside the world, at
`universe/worlds/<world>/resources/MiniGameArenaResource.json`. The server reads that
file once at world load and holds it in memory until shutdown, when it writes the
in-memory copy back out.

Two consequences, both of which have cost real data here:

* Copying a marker file onto a **running** server does nothing. The running server
  never re-reads it.
* The next world save **overwrites** whatever you copied in, with whatever the server
  has in memory. If that memory is empty, the file is now empty too.

So the file route only works as: stop → upload → start, in that order, with no save in
between. Any slip reverts silently.

### `/arena import` — the route that cannot go wrong

```
/arena import
```

Reads a marker file from disk and writes it into the **live** world through the same
path `/arena loc set` uses. Nothing to overwrite, no restart, no ordering.

Searched in order, relative to the server's working directory:

1. `mods/arena-markers.json`
2. `mods/MiniGameArenaResource.json`
3. `arena-markers.json`

`mods/` comes first on purpose. It is the directory operators upload to routinely, and
a bad upload there fails loudly at boot — whereas a marker file in the wrong place
fails with no message at all.

Merges rather than replaces: markers you placed by hand since the export are kept, and
arenas missing from the import are never touched. Import can only add.

Afterwards, **stop the server cleanly** so the save persists it. Until then the markers
exist only in memory.

### Exporting

```powershell
copy moshpit\server\universe\worlds\Moshpit\resources\MiniGameArenaResource.json arena-markers.json
```

### When an arena reports no markers

`MiniGameArena.locations` names the cause distinctly, at ERROR:

```
Arena 'dust2' (Dust2) has no usable markers: not present in the marker store on
world 'Moshpit', which holds 0 arena(s): []
```

That line carries the world the server actually resolved and the arena ids the store
holds — enough to separate an empty file from a wrong world from a wrong arena id. A
healthy load logs once per arena instead:

```
Arena 'nuketown' loaded 51 markers from world 'Moshpit'
```

Neither existed before; the first sign of trouble used to be
`NoSuchElementException: Collection is empty` thrown from a spawn strategy several
frames away, naming neither the arena nor the world.
