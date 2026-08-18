# Not ported

What is left here from the original Everlands `lobby` module, and why.

* `Stamina.json` — the original lobby's stamina override. It redefines the whole
  vanilla stamina asset (regen tiers, min/max, min-value effects) and was written
  against an older build. Replacing a core stat asset to get unlimited sprint is
  a lot of surface area for the benefit; the lobby uses `InfiniteStaminaSystem`
  instead, which is what moshpit does and which ships no stat override at all.

That is the whole list.

The leaderboard files (`Leaderboard.json`, `Leaderboard.ui`, `LeaderboardEntry.ui`)
used to sit here too. They were parked because the original was fed by `HUsers`,
Spookline's cross-server user store, which is not in the source drop. They have
since been rebuilt on a Redis snapshot published by the game server, with the UI
redone on the hextech theme and the hardcoded tab list generated from config
instead — so the parked copies have been deleted rather than left to rot beside
the live ones they no longer match. The working versions are in
`lobby/server/mods/Everlands.Lobby/`; see the Leaderboard section of `DEPLOY.md`.
