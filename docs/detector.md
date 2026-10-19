# Civilization Detector

The Civilization Detector is a handheld tool that lets you scan and visualize your civilization level.

## Crafting

Surround a **compass** with **8 emeralds** in a crafting table:

```
E E E
E C E
E E E
```

Where E = Emerald, C = Compass.

## Basic Use

Right-click to scan. The detector reads the civilization score at your position and gives immediate feedback:

| Color | Meaning | What it tells you |
|-------|---------|-------------------|
| 🟢 Green | HIGH | Fully protected — hostile spawns are blocked here |
| 🟡 Yellow | MID | Partially protected — some spawns may get through |
| 🔴 Red | LOW | Unprotected wilderness — spawns proceed normally |
| 🟣 Purple | MONSTER | Inside a monster head zone — skulls are active here |

Each reading plays a distinct sound cue, so you can tell the result without looking at the item.

The detector has a brief enchantment glint while the reading is active (~2 seconds).

## Sonar Pulse

When the aura effect is enabled (on by default), right-clicking also fires a **sonar pulse**:

### Charge-Up
A vertical column of particles appears at your position for ~0.4 seconds, accompanied by a charge-up tone. The particle color reflects your current zone:

- **White sparks** (End Rod) — you're in a protected area
- **Blue flames** (Soul Fire) — you're in wilderness
- **Orange flames** (Fire) — you're in a head zone

### Expanding Shockwave
After the charge-up, a ring of particles expands outward from your position over ~1.5 seconds, reaching up to 120 blocks. Each particle is colored by the zone it passes through, painting the landscape with civilization data.

A boom sound (breeze shoot) plays as the wave launches.

### Boundary Walls

~1.2 seconds after the scan, glowing translucent walls rise at the boundaries of your civilization:

- **Gold/amber walls** — civilization boundaries (where protection ends and wilderness begins)
- **Amethyst/purple walls** — monster head zone boundaries

Walls appear with a fade-in, hold steady for ~2.5 seconds, then gently fade out over ~2 seconds. Firing the detector again while walls are visible extends their duration — and if you've moved toward a boundary, newly discovered faces appear while existing ones hold steady.

Wall height spans ±48 blocks from the scan center.

## Configuration

The sonar effect can be toggled on or off in the settings GUI. When disabled, the detector still provides color and sound feedback — just without the visual pulse and walls.
