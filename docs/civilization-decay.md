# Civilization Decay

Civilization protection is not permanent. The world remembers your presence — and notices your absence.

## How Decay Works

Each scored region tracks a **presence time** — the last time a player was nearby. When no player has visited for a while, protection begins to fade:

1. **Grace period** — For the first 6 hours (default) after you leave, nothing changes. Full protection.
2. **Exponential decay** — After the grace period, the outer zone scores decay exponentially. Borders shrink inward.
3. **Decay floor** — Scores never drop below 25% (default) of their original value. A well-built city retains meaningful core protection indefinitely.

The decay only affects the **outer zone** of your civilization. The core — the densely built heart of your settlement — holds firm even after prolonged absence.

## Recovery

Return to a decayed settlement and spend time there, and it recovers. Recovery is gradual:

- Recovery happens in steps, each advancing the presence time toward "now"
- Each step recovers a fraction of the gap between your last recorded presence and the current time (default 20%)
- Steps are rate-limited (default once per minute) to prevent instant restoration
- Don't worry about how long you've been away — restoration won't take too long, the city responds to you

You don't need to do anything special — just being in the area is enough. Build, explore, craft — your presence is what matters.

## Offline Time

In singleplayer, offline time does **not** count as absence. The server clock only advances while the game is running. Close the game on Monday, open it on Friday — your civilization is exactly where you left it.

In multiplayer, the server keeps running, so the clock keeps ticking. If no player visits a settlement, it will gradually decay according to the rules above.

## In-Game Configuration

All decay parameters are adjustable via the in-game GUI (requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)):

| Setting | Range | Default | What it controls |
|---------|-------|---------|-----------------|
| Freshness Duration | 1–48 hours | 6 hours | Grace period before decay begins |
| Decay Speed | 1–10 | 5 | How fast outer scores fade (higher = faster) |
| Decay Floor | 0–50% | 25% | Minimum retained score after full decay |
| Recovery Speed | 1–10 | 5 | How quickly protection returns when you're nearby |
| Patrol Influence Range | 32–128 blocks | 64 blocks | How far your presence sustains and restores settlements |

!!! warning "Advanced: civil.properties"
    Raw parameters can also be edited in `config/civil.properties`, but this is intended for advanced users only. The file contains many internal parameters that interact in non-obvious ways. If things break, delete `civil.properties` and restart — the mod will regenerate it with defaults.
