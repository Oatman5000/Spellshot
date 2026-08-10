# Not ported

These came from the original Everlands `lobby` module but depend on Spookline's
private `hyboot` platform, which is not in the source drop and not on Maven.

* ~~`Leaderboard.json`, `Leaderboard.ui`, `LeaderboardEntry.ui`~~ — RESTORED.
  These were parked because the original was fed by `HUsers`, a cross-server user
  store we do not have. They now run on a Redis snapshot published by the game
  server instead; the UI was rebuilt on the hextech theme and the hardcoded tab
  list replaced with one generated from config. See the Leaderboard section of
  DEPLOY.md.

The item json is parked here rather than left in the pack because it binds
`"Type": "LeaderboardInteraction"`, and an unregistered interaction type makes
the whole item fail to decode.

* `Stamina.json` — the original lobby's stamina override. It redefines the whole
  vanilla stamina asset (regen tiers, min/max, min-value effects) and was written
  against an older build. Replacing a core stat asset to get unlimited sprint is
  a lot of surface area for the benefit; the lobby now uses InfiniteStaminaSystem
  instead, which is what moshpit does and which ships no stat override at all.
