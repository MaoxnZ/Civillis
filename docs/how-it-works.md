# How It Works

Civillis suppresses hostile mob spawning near player-built structures by computing a real-time **civilization score** for every location in the world.

## The Basics

Every time a hostile mob attempts to spawn naturally, Civillis evaluates the area around that spawn point:

1. **Scan** — A 240×240×48 block area (configurable) is checked for blocks that signal human presence
2. **Score** — Each recognized block contributes a weighted value to its local 16³ voxel chunk; all chunks in range are then aggregated into a final score between 0 and 1
3. **Decide** — The score determines the spawn outcome

*This means millions of blocks evaluated per spawn attempt, across every spawn attempt in the world. See [Architecture](technical/architecture.md) to learn how the shard-based civilization engine keeps this at constant time.*

## Spawn Decisions

The civilization score maps to one of three outcomes:

- **LOW** (score below suppression threshold) — Spawn proceeds normally. This is wilderness.
- **MID** (score between low and high thresholds) — Spawn is probabilistically blocked. The higher the score, the more likely the block. This is the frontier.
- **HIGH** (score at or above suppression threshold) — Spawn is fully blocked. This is civilization.

The in-game "Spawn Suppression" strength slider (1–10) adjusts both thresholds together — a higher value makes it easier to reach full protection with fewer blocks.

## Detection Range

The detection area is centered on the spawn point, not the player. Default size is **240×240×48 blocks** (15×15×3 voxel chunks). This can be adjusted from 112 to 496 blocks per side in the settings.

A larger detection range means distant builds contribute to the score — your city's outskirts help protect the center. A smaller range makes protection more localized.

The Y-axis range is fixed at 48 blocks (3 voxel chunks), so builds above or below your location have limited influence.

## What Counts as Civilization

Blocks that reflect human presence contribute to the score. The more complex or significant the block, the higher its weight:

- **Stone Age** (0.2–0.3) — Crafting tables, furnaces, chests, farmland
- **Iron Age** (0.4–0.5) — Lanterns, workstations (smithing, fletching, cartography, etc.)
- **Advanced** (0.7–1.0) — Beds, anvils, brewing stands, sea lanterns
- **Mythic** (2.0–5.0) — Respawn anchors, enchanting tables, beacons, conduits

Light-emitting and magical blocks receive a bonus multiplier. See [Blocks & Scoring](blocks-and-scoring.md) for the full weight table.

!!! note "Voxel Chunk Score Cap"
    Each 16×16×16 voxel chunk has a maximum civilization score. Stacking more blocks into a single chunk beyond the cap doesn't help — spread your builds outward instead. A wider settlement actually provides bonus protection beyond what the individual blocks alone would suggest.

## Practical Scale

- **A small cabin** with a crafting table, furnace, bed, and chest provides modest protection — mobs are less likely to spawn nearby, but not fully suppressed.
- **A well-developed village** with workstations, campfires, and lanterns pushes monsters back ~40 blocks from its borders.
- **A sprawling industrial megacity** — every chunk saturated with high-value blocks — can reach up to ~90 blocks of spawn-free perimeter. This requires serious dedication.

## Beyond Scoring

Civilization scoring is the foundation, but the mod doesn't stop there:

- **[Civilization Decay](civilization-decay.md)** — Protection fades from the edges inward when you leave. Return and stay, and it recovers.
- **[Monster Heads](monster-heads.md)** — Skulls punch through civilization protection and redirect distant hostiles toward themselves.
- **[Mob Flee AI](mob-flee-ai.md)** — Existing hostiles can retreat from civilization pressure, especially in dense city cores.
- **[Civilization Detector](detector.md)** — Scan and visualize your civilization boundaries in real time.
