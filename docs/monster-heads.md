# Monster Heads

Monster skulls are the counterpart to civilization. Where civilization blocks push monsters away, skulls invite them back — on your terms.

## Three Mechanisms

Monster heads operate through three distinct systems, each activating at a different scale:

### 1. Spawn Gate Bypass

Place a monster skull and it creates a **48×48×16 block zone** (3×3×1 voxel chunks) around itself where civilization protection is completely bypassed. Mobs can spawn freely inside this zone regardless of the civilization score.

This is a local, unconditional override — the skull punches straight through the spawn gate.

### 2. Distant Spawn Redirection

Beyond the local bypass zone, skulls exert a **dimension-wide attraction effect**. When a hostile mob attempts to spawn far from any skull, the mod may suppress that spawn based on:

- **Distance** — The closer the spawn is to a skull, the less likely it is to be suppressed. Spawns very close to a skull are never suppressed.
- **Head count** — More skulls in the world increase the suppression strength at distance. The formula uses a logarithmic scaling on head count.
- **Max radius** — Attraction only operates within a configurable horizontal radius (default 128 blocks). Beyond that, spawns are unaffected.

The net effect: distant spawns are probabilistically cancelled, concentrating mob spawns near your skulls. This is what makes skulls useful for mob farms — you don't need to light up caves or build in the sky. Just place skulls where you want the mobs.

### 3. Mob Conversion

When **3 or more skulls** are clustered in the same voxel chunk area, they begin converting spawned mobs into matching types:

| Skulls in area | Conversion chance |
|---------------|-------------------|
| 1–2 | No conversion (local bypass only) |
| 3 | ~12.5% |
| 5 | ~37.5% |
| 7 | ~62.5% |
| 10+ | 100% |

Conversion picks a random type from the skulls present. Place 3 skeleton skulls and some spawns will convert to skeletons. Mix skeleton and zombie skulls for a blend. The more skulls, the stronger the conversion.

!!! note "Dimension Restrictions"
    Some skull types are restricted to specific dimensions. By default, **Wither Skeleton Skulls** and **Piglin Heads** only function in the Nether — all three mechanisms (bypass, redirection, and conversion) are inactive outside their designated dimensions. In non-matching dimensions, restricted skulls are purely decorative.

    Modpack authors can customize which dimensions each skull type operates in via the `"dimensions"` field in datapack JSON — see [Data-Driven Registries](modpack/data-driven.md#head-types).

## Skull Types

Head types are data-driven. The default registry ships with all vanilla skull types:

| Skull | Attracts | Converts | Active Dimensions |
|-------|----------|----------|-------------------|
| Skeleton Skull | Skeleton | Yes | All |
| Zombie Head | Zombie | Yes | All |
| Creeper Head | Creeper | Yes | All |
| Piglin Head | Piglin | Yes | Nether only |
| Wither Skeleton Skull | Wither Skeleton | Yes | Nether only |

Dimension-restricted skulls are purely decorative outside their designated dimensions — bypass, redirection, and conversion are all inactive.

Custom skull types added by other mods via `SkullBlock` subclasses are automatically compatible. Head types can be configured, disabled, or extended via datapacks — see [Data-Driven Registries](modpack/data-driven.md#head-types).

## In-Game Configuration

Head attraction parameters are adjustable via the in-game GUI under the "Head Attraction" subcategory (requires [Mod Menu](https://modrinth.com/mod/modmenu) + [Cloth Config](https://modrinth.com/mod/cloth-config)):

| Setting | Range | Default | What it controls |
|---------|-------|---------|-----------------|
| Attraction Strength | 1–10 | 5 | How strongly skulls suppress distant spawns |
| Attraction Range | 48–160 blocks | 128 blocks | Maximum horizontal radius for redirection |

!!! warning "Advanced: civil.properties"
    Raw parameters can also be edited in `config/civil.properties`, but this is intended for advanced users only. If things break, delete `civil.properties` and restart — the mod will regenerate it with defaults.
