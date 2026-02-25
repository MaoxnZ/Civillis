# Mob Flee AI

Mob Flee AI makes hostile mobs behave more naturally inside civilized areas: instead of only being blocked at spawn time, existing mobs can also decide to retreat.

---

## What It Does

When enabled, hostile mobs periodically evaluate the local civilization level and may choose to flee toward less civilized ground.

- In moderately civilized areas, mobs tend to disengage and drift outward
- In highly civilized city centers, mobs can panic and may even give up fighting to escape
- This behavior is linked to your Spawn Suppression tuning, so stronger suppression generally means stronger flee pressure

This gives settlements a more believable defensive atmosphere: danger does not just fail to appear, it also struggles to stay.

---

## Two Flee Modes

Mob Flee AI uses two behavior modes:

- **Idle flee** — normal retreat movement when civilization pressure is present
- **Combat panic** — short panic bursts in very high-pressure zones, including potential mid-combat disengagement

Both modes are controlled by the same underlying civilization score, but with different thresholds and durations.

---

## How Direction Is Chosen

The flee goal samples multiple candidate directions around the mob and compares civilization pressure in each direction. It then prefers routes that move the mob toward lower-pressure space.

In practice, this means:

- mobs near borders tend to leak outward first
- mobs deep in dense city interiors react more aggressively
- movement still respects normal pathfinding constraints

---

## Relationship to Spawn Suppression

Mob Flee AI is a behavior layer, not a replacement for spawn suppression:

- **Spawn suppression** controls whether new hostile mobs are allowed to spawn
- **Mob flee AI** controls how already-present hostile mobs behave after spawning

Together, they produce a smoother "civilized territory" feeling:

1. New hostile spawns are reduced or blocked
2. Existing hostiles are encouraged to retreat

---

## Configuration

### In-Game GUI

`Civillis Settings` → `Civilization` → `Mob Flee Behavior`

- **Mob Flee AI** (toggle): enables/disables flee behavior globally

### Advanced `civil.properties`

You can tune deeper behavior with raw parameters:

- `mobFlee.enabled`
- `mobFlee.combatFleeRatio`
- `mobFlee.checkIntervalTicks`
- `mobFlee.jitterTicks`
- `mobFlee.panicDurationTicks`
- `mobFlee.maxDurationTicks`
- `mobFlee.speed`
- `mobFlee.sampleDistance`

See [Configuration](configuration.md) for parameter details.
