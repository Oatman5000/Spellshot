# Release checklist

Getting this build onto AMP. Roughly 30 minutes for this release.

**Four things go up:** two jars and two asset packs. No AMP configuration --
addresses are baked into the jars you build (step 4 explains how).

## 0. Before anything — this release only

Both instances must be on **Hytale 0.6.0** before the plugins go anywhere near
them. Every manifest now declares `ServerVersion: ^0.6.0`, and the code is
compiled against the 0.6.0 API; on 0.5.x the plugin either refuses to load or
dies on a missing method.

Back up first, because this release renames things that hold player data:

- [ ] `universe/` copied down from both instances
- [ ] the moshpit instance's `everlands/` directory copied down — this is every
      player's level, prestige and stats. The server moves it to `spellshot/` on
      first boot and that migration is one-way.
- [ ] Both instances updated to 0.6.0

---

## 1. Build

```powershell
cd path\to\Spellshot
.\gradlew :moshpit:shadowJar :lobby:shadowJar
```

- [ ] `moshpit\build\libs\moshpit-plugin.jar` — timestamp is from this build
- [ ] `lobby\build\libs\lobby-plugin.jar` — same

Both are needed: this release changes `:core`, which is shaded into each.

---

## 2. Stop the moshpit instance ⚠

**Before uploading anything.** A running server saves its world on shutdown and
will overwrite the marker file you are about to upload. That is exactly how the
previous Nuketown markers were lost.

Use AMP's **Stop**, not a kill — the world flushes on a clean shutdown.

- [ ] Moshpit instance stopped

---

## 3. Upload

> ⚠ **This release is not like the last one.** Previous releases shipped the jars
> alone. This one changes the asset packs *and renames them*, so both packs go up
> and both old ones must be deleted. Skipping either half leaves the server
> loading two copies of every asset.

### Moshpit instance

| From | To |
|---|---|
| `moshpit\build\libs\moshpit-plugin.jar` | `mods/moshpit-plugin.jar` |
| `moshpit\server\mods\Spellshot.Game\` | `mods/Spellshot.Game/` |

Then **delete `mods/Everlands.Game`**.

The pack must go up this time. It carries the nine ADS interactions, the rewired
firearm items, the grenade cook step, the M40's reticle fix and the whole loadout
menu — the jar references all of them and none of it exists in the old pack.

**Markers only if they are missing up there.** Do not copy
`MiniGameArenaResource.json` into `universe/` on a running server — see below.

### Lobby instance

| From | To |
|---|---|
| `lobby\build\libs\lobby-plugin.jar` | `mods/lobby-plugin.jar` |
| `lobby\server\mods\Spellshot.Lobby\` | `mods/Spellshot.Lobby/` |

Then **delete `mods/Everlands.Lobby`**.

The lobby jar on AMP predates the 0.6.0 port entirely — it was built against
0.5.7 and will not run. Rebuild it even if nothing in `lobby/` looks changed:
`:core` is shaded into it and did change.

### Do NOT upload

- **World chunk files.** AMP's copy is authoritative; the local `.region.bin`
  files have been modified by local testing. The Nuketown *build* is already on
  AMP — only its markers were missing.
- **`spellshot/` or `everlands/`.** Player progression lives there and AMP's copy
  is the real one. The server renames the directory itself on first boot; creating
  `spellshot/` by hand makes it skip the migration and every player starts at
  level 1 with their real data stranded in the old folder.

---

## 3a. Arena markers — use `/arena import`, not a file copy

Skip this unless `/arena entries` comes back empty.

**Do not copy `MiniGameArenaResource.json` into `universe/` on a live server.** The
server reads that file once, at world load, and holds it in memory until it stops.
Dropping a new copy over it changes nothing — and the next world save writes the
in-memory version straight back over your file. Doing it in the wrong order has
destroyed this marker set twice, silently both times.

Instead:

1. Copy `moshpit\server\universe\worlds\Moshpit\resources\MiniGameArenaResource.json`
   to the repo root as `arena-markers.json`. The build also drops a copy next to the
   jar in `moshpit\build\libs\`.
2. Upload it to `mods/arena-markers.json` — the same directory as the plugin jar, and
   deliberately so: a bad jar upload fails loudly at boot, so it is the one path you
   can confirm works. A misplaced marker file fails silently.
3. In game: `/arena import`

```
Imported 295 markers from mods/arena-markers.json:
 - mirage: 46
 - dust2: 43
 - raid: 54
 - nuketown: 51
 - rust: 54
 - hijacked: 47
```

Spawns work immediately; there is nothing to overwrite and no ordering to get wrong.
It merges rather than replaces, so markers placed by hand since the export survive.

4. **Stop the instance cleanly.** That save writes the markers into the world, and the
   import becomes one-time rather than every boot.

- [ ] `/arena entries` lists six arenas
- [ ] Died once and got teleported
- [ ] Clean stop performed, so it persists

---

## 4. Addresses — nothing to do on AMP

**AMP has no per-instance environment variable setting.** It is an open feature
request ([CubeCoders/AMP#1321](https://github.com/CubeCoders/AMP/issues/1321));
the environment variables AMP documents are for installing AMP itself and for
Docker deployments, not for game instances.

So the addresses live in the working tree instead, and are held out of every
commit with `skip-worktree`:

| File | Local value | Committed value |
|---|---|---|
| `lobby/.../LobbyConfig.kt` | your host + invite | blank |
| `moshpit/.../LobbyReferral.kt` | your host | blank |

```powershell
git ls-files -v | Select-String "^S"     # confirm both are still protected
```

`git status` will not show these as modified, and `git add -A` will not stage
them. Build from this working tree and the jars carry your addresses; the repo
stays clean.

**If you ever need to edit those files for real:**

```powershell
git update-index --no-skip-worktree <path>   # unprotect
# ...make the change, commit it...
git update-index --skip-worktree <path>      # protect again
```

Two things to know about this arrangement: a `git pull` that changes either file
will fail with a confusing message until you unprotect it, and a fresh clone of
your own repo will have blanks and need the values re-entered.

`SPELLSHOT_GAME_HOST`, `SPELLSHOT_LOBBY_HOST` and `SPELLSHOT_DISCORD_INVITE`
still work if you ever run somewhere that can set them — they win over the
compiled value.

---

## 5. Start and verify

### Moshpit console

- [ ] `Loaded pack: Spellshot:Game from Spellshot.Game` — and no second pack line
      for `Everlands.Game`. If both appear, the old folder is still there.
- [ ] `Migrated progression directory ...everlands -> ...spellshot` on the first
      boot only. If instead you see `No saved progression at ...` then the
      migration did not happen and **stop before anyone logs in** — a save will
      write a fresh file over the empty one.
- [ ] `Spellshot: NN beans initialised` — proves the bean scan found the renamed
      package. Zero beans is silent otherwise.
- [ ] `Registered interaction codec 'CookExplosiveInteraction'` and
      `'ThrowExplosiveInteraction'` — without these the grenades do nothing.
- [ ] No `Failed to setup plugin Spellshot:Moshpit`
- [ ] **`Arena marker store registered`** — the one that matters. If you see
      `ARENAS ARE DISABLED` instead, **stop the server immediately**, before it
      saves and overwrites the marker file with an empty one.
- [ ] `/lobby will refer players to <your lobby host>:5523`

### Lobby console

- [ ] `Registered interaction codec 'LeaderboardInteraction'`
- [ ] No `has no host` warning

### In game

- [ ] `/arena entries` lists all six arenas
- [ ] `/arena info nuketown` reports 51 markers
- [ ] Die once — you get teleported to a spawn
- [ ] Disconnect and rejoin — no `Assert not in thread!` in the console
- [ ] Navigator sends you to moshpit; `/lobby` sends you back
- [ ] Tab opens the loadout page, with the player list showing name, team, K/D
      and a **sane ping** — three digits at most. A four-digit ping means the
      microsecond conversion regressed.
- [ ] Clicking a loadout replaces the player list with the edit panel, and the
      Primary/Secondary/Melee/Utility buttons respond. Dead buttons mean the
      event bindings did not ride along with the appended panel.
- [ ] Leaderboard opens and shows data

### New this release

- [ ] Right-click aims on every gun, and firing does not drop the zoom
- [ ] M40 appears in the loadout menu at level 26 and scopes to 10x
- [ ] Hold a grenade past three seconds: it kills you, and it is gone from your
      hotbar afterwards
- [ ] Killing yourself with your own grenade awards **no** kill, no XP and no
      Gun Game tier — but still costs a death
- [ ] The One in the Chamber row in the loadout menu offers Revolver and M40, and
      OitC issues whichever you picked

---

## 6. Optional — `MiniGame.json`

Not needed for anything above. It adds `Player.MovementConfig: "Default"` to the
moshpit gameplay config — the setting whose absence broke sprinting in the lobby.
Moshpit has the same omission but sprint appears to work, so this is either a
no-op or a fix for something subtly wrong that nobody noticed.

| From | To |
|---|---|
| `moshpit\server\mods\Spellshot.Game\Server\GameplayConfigs\MiniGame.json` | same path under `mods/` |

- [ ] Uploaded, restarted, sprint still behaves

---

## Publishing to GitHub *(independent of the deployment)*

```powershell
git push --force origin main
```

Force is required — history was replaced. Your host and Discord invite are not in
the published source; `skip-worktree` keeps them local (step 4).

---

## If something breaks

```powershell
git stash                      # if you have uncommitted work
git checkout HEAD~1 -- .
.\gradlew :moshpit:shadowJar :lobby:shadowJar
```

`git checkout HEAD~1 -- .` will not touch the two address files — they are
`skip-worktree`, so your values survive a rollback.

**If the arena markers are ever wiped again**, the recovery copies are on disk at
`moshpit\server\universe\worlds\Moshpit\resources\` as
`MiniGameArenaResource.json.with-old-nuketown` (the pre-rebuild set) — and the
current 51-marker set is in git.
