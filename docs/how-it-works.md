# How It Works

Civillis suppresses hostile mob spawning near player-built structures by computing a real-time **civilization score** for every location in the world.

## The Basics

Every time a hostile mob attempts to spawn naturally, Civillis evaluates the area around that spawn point:

1. **Scan** — A 240×240×48 block area (configurable) is checked for blocks that signal human presence
2. **Score** — Each recognized block contributes a weighted value to its local 16³ voxel chunk; all chunks in range are then aggregated into a final score between 0 and 1
3. **Decide** — The score determines the spawn outcome

*Naively that would touch a huge volume every time; see [Architecture](technical/architecture.md) for how caching keeps spawn checks fast.*

## Spawn Decisions

The civilization score maps to one of three outcomes:

- **LOW** (score below suppression threshold) — Spawn proceeds normally. This is wilderness.
- **MID** (score between low and high thresholds) — Spawn is probabilistically blocked. The higher the score, the more likely the block. This is the frontier.
- **HIGH** (score at or above suppression threshold) — Spawn is fully blocked. This is civilization.

The in-game **Civilization Strength** slider (1–10) adjusts both thresholds together — a higher value makes it easier to reach full protection with fewer blocks.

## Max. Civilization Radius

The detection area is centered on the spawn point, not the player. Default size is **240×240×48 blocks** (15×15×3 voxel chunks). This can be adjusted from 112 to 496 blocks per side via the **Max. Civilization Radius** setting.

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
    Each 16×16×16 voxel chunk has a maximum civilization score. Past the cap, more blocks in that same chunk do not add score — spread builds across chunks for more coverage.

## Practical Scale

- **A small cabin** with a crafting table, furnace, bed, and chest provides modest protection — mobs are less likely to spawn nearby, but not fully suppressed.
- **A well-developed village** with workstations, campfires, and lanterns pushes monsters back ~40 blocks from its borders.
- **A sprawling industrial megacity** — many chunks at cap — can reach on the order of ~90 blocks of spawn-free perimeter.

## Beyond Scoring

Civilization scoring is the foundation, but the mod doesn't stop there:

- **[Civilization Decay](civilization-decay.md)** — Protection fades from the edges inward when you leave. Return and stay, and it recovers.
- **[Civil Maps](play/civil-maps.md)** — Filled maps upgraded with a Civil Detector show civilized and monster-pocket regions as map tints.
- **[Podium of Spawning](play/podium-of-spawning.md)** and **[Podium of Spawning](play/podium-of-spawning.md)** — A controlled mob-friendly pocket inside civilization, shaped by skulls placed inside it.
- **[Mob Flee AI](mob-flee-ai.md)** — Existing hostiles can retreat from civilization pressure, especially in dense city cores.
- **[Civilization Sonar](sonar/index.md)** — Portable detector and static bell scans; visualize boundaries in real time (overview + tabbed detail).
- **[Zone transition HUD](play/zone-transition-hud.md)**, **[Structure spawn rules](play/structure-spawn-rules.md)**, **[Dimension rules](play/dimension-rules.md)**, **[Podium of Undying](play/podium-of-undying.md)**, **[Commands](play/commands.md)** — feedback, structure/dimension exceptions, late-game rescue, `/civil rebuild`, and `/civil ring`.

## In-game configuration

The two knobs that most change the feel of the spawn loop are on the **main** row of **Civillis Settings** → **Civilization**:

| Setting | Range | Default | What it does |
|---------|-------|---------|--------------|
| **Civilization Strength** | 1–10 | 5 | Moves both spawn thresholds — higher = easier full protection. |
| **Max. Civilization Radius** | 112–496 blocks (step 32) | 240 blocks | How far around each spawn attempt the mod looks for civilization blocks. |

Sonar, decay, Podium of Spawning, Podium of Undying, flee AI, and HUD live in **collapsible subcategories** on the same screen. Full table in [Configuration](configuration.md).

!!! warning "Advanced: civil.properties"
    Raw keys override the GUI for matching parameters. If tuning goes wrong, delete `config/civil.properties` and restart.
