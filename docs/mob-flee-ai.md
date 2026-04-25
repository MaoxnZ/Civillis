# Mob Flee AI

Mob Flee AI lets **already-spawned** hostile mobs **retreat** from civilized areas. Spawn suppression only affects **new** spawns; flee handles mobs that are already in the world.

## What it does

When enabled, hostiles periodically sample civilization pressure around themselves:

- **Idle flee** — drift outward under moderate pressure
- **Combat panic** — short bursts under very high pressure (can include mid-combat disengagement in dense city cores)

Both scale with **Civilization Strength** the same way spawn thresholds do.

## How direction is chosen

The mod first looks for a nearby active [Podium of Spawning](play/podium-of-spawning.md) attraction target when the mob is outside the local podium pocket. If none is useful, it samples candidate directions, compares civilization pressure along each ray, and prefers moving toward **lower** pressure. Vanilla pathfinding still applies underneath.

## Relationship to spawn suppression

1. **Civilization Strength** — shared with spawn thresholds; sets how “oppressive” high-score zones feel for flee.
2. **Mob Flee AI** toggle — master switch for whether retreat logic runs at all.

## In-game configuration

Open **Civillis Settings** → **Civilization** → expand **Miscellaneous** (requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config) on Fabric):

| Setting | Type | Default | What it controls |
|---------|------|---------|------------------|
| **Mob Flee AI** | Toggle | on | Enables or disables all flee behavior globally. |

!!! warning "Advanced: civil.properties"
    Fine-grained tuning uses `mobFlee.*` keys (`checkIntervalTicks`, `panicDurationTicks`, `speed`, `sampleDistance`, …). See the grouped list in [Configuration](configuration.md). Delete `civil.properties` to reset if behavior becomes unstable.

## See also

- [Configuration](configuration.md) — full screen layout and properties overview
- [How It Works](how-it-works.md) — spawn LOW / MID / HIGH pipeline
- [Podium of Spawning](play/podium-of-spawning.md) — controlled hostile pockets
