# Release checklist

Everything needed to get this version live. Roughly 20 minutes.

Order matters in one place only: **set the environment variables before restarting the
AMP instances**, or the first restart comes up with handoff disabled.

---

## 1. Build — 5 min

```powershell
cd C:\Users\dkibb\Downloads\everlands-update6-verified\everlands-main
.\gradlew :moshpit:shadowJar :lobby:shadowJar
```

Both jars are needed. This release changes `:core`, which is shaded into each.

- [ ] `moshpit\build\libs\moshpit-plugin.jar` — timestamp is from this build
- [ ] `lobby\build\libs\lobby-plugin.jar` — same

If the build fails, stop here and send the error. Nothing in this release has been
compiled — it was written without a working compiler available.

---

## 2. Publish to GitHub — 5 min

```powershell
cd C:\Users\dkibb\Downloads\everlands-update6-verified\everlands-main
git add -A
git commit -m "Fix .gitignore excluding all source; command DSL, arena writes, cleanup"
git push --force origin main
```

**Force is required.** The previous commit was missing all 161 Kotlin source files, so
history has to be replaced rather than added to.

Then confirm on github.com/Oatman5000/Spellshot:

- [ ] `core/src/main/kotlin/...` exists and contains `.kt` files
- [ ] File count is ~688, not ~529
- [ ] Search the repo for `oathosting` → **no results**
- [ ] `Settings → Emails` lists `oatman5k@gmail.com`, or the commit shows as unlinked

---

## 3. Set environment variables — 5 min ⚠

**This is the step that breaks things if skipped.** Addresses no longer ship with
working defaults.

AMP → each instance → *Configuration* → *Environment Variables*.

### Lobby instance

| Variable | Value |
|---|---|
| `EVERLANDS_GAME_HOST` | `play.oathosting.com` |
| `EVERLANDS_DISCORD_INVITE` | `https://discord.gg/6CpNPdDRE` |

### Moshpit instance

| Variable | Value |
|---|---|
| `EVERLANDS_LOBBY_HOST` | `play.oathosting.com` |

Ports already default correctly — moshpit 5524, lobby 5523 — so `EVERLANDS_GAME_PORT`
and `EVERLANDS_LOBBY_PORT` are only needed if those change.

- [ ] Lobby: both variables set
- [ ] Moshpit: variable set

---

## 4. Upload — 5 min

### Moshpit instance

- [ ] `moshpit-plugin.jar` → `mods/`
- [ ] `MiniGame.json` → `mods/Everlands.Game/Server/GameplayConfigs/`
      *(the only asset change this release — adds `Player.MovementConfig`)*

### Lobby instance

- [ ] `lobby-plugin.jar` → `mods/`

No world files changed. No other assets changed.

---

## 5. Restart and verify — 5 min

Restart both instances. **Stop cleanly rather than killing** — the moshpit world holds
arena markers that flush on save.

### Console, on the moshpit instance

- [ ] Boots without `Failed to setup plugin Everlands:Moshpit`
- [ ] `/lobby will refer players to play.oathosting.com:5523`
      *(if it says "referral disabled", step 3 was missed)*

### Console, on the lobby instance

- [ ] `Registered interaction codec 'LeaderboardInteraction'`
- [ ] No `has no host` warning

### In game

- [ ] `/arena entries` lists the five arenas — nuketown reappears once you place a marker
- [ ] `/arena loc set nuketown spawn:1` replies with the coordinates it recorded
      *(this is the fix that took four deploys; it should now confirm in chat)*
- [ ] Navigator sends you to the moshpit server
- [ ] `/lobby` sends you back
- [ ] **Sprint works** — this tells us whether `MovementConfig` was silently broken
      before or was always fine
- [ ] Leaderboard opens and shows data

---

## 6. Then — the new Nuketown

`moshpit/ARENAS.md` is the walkthrough. The short version:

1. Paste the prefab in its final position.
2. Clear the old markers — the script is in section 2 of that document. **Do not skip
   this**; leftover markers point into the old build and players will spawn there
   mid-match.
3. `/moshpit leave` so the session stops respawning you.
4. Place ~46 markers. `/arena info nuketown` to audit by prefix before trusting it.
5. Stop the server cleanly so the markers flush.
6. If you added team spawns, add `"nuketown"` to `TEAM_READY_ARENAS` in
   `MoshpitConfig.kt` and rebuild.

---

## If something breaks

Roll back by re-uploading the previous jars from git:

```powershell
git stash          # if you have uncommitted work
git checkout HEAD~1 -- .
.\gradlew :moshpit:shadowJar :lobby:shadowJar
```

The environment variables are harmless to leave set — the old jars ignore them.
